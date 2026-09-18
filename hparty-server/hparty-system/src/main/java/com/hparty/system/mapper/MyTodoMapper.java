package com.hparty.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 「我的待办」聚合查询。
 *
 * <p><b>为什么用注解 SQL 而不是引用各域实体</b>：待办要把发展党员（{@code dev_}）、
 * 三会一课（{@code am_}）、党费（{@code party_dues_}）、组织关系转接（{@code party_transfer}）
 * 四个域的数据聚合到一起，而 {@code hparty-system} 不依赖 {@code hparty-party}
 * 与 {@code hparty-develop}（依赖是严格单向的），拿不到那些实体与 Mapper。
 * 因此这里照 {@link PartyPersonMapper#countRunningFlow} 的做法，
 * 用 {@code @Select} 直接写 SQL —— 逻辑删除条件也要手写，因为
 * MyBatis-Plus 的 {@code @TableLogic} 只作用于内置方法。</p>
 *
 * <p><b>数据权限的传参约定</b>（四个查询一致）：</p>
 * <ul>
 *   <li>{@code personId} 不为空 —— 「仅本人」数据范围，按人收敛，
 *       {@code orgIds} 被忽略；</li>
 *   <li>{@code personId} 为空且 {@code orgIds} 不为空 —— 按可见组织集合收敛；</li>
 *   <li>两者都为空 —— 不做限制（超级管理员 / 全部数据范围）。
 *       调用方在「没有任何可见组织」时不应调用本 Mapper，直接返回空列表，
 *       以免生成 {@code IN ()} 这种非法 SQL。</li>
 * </ul>
 */
@Mapper
public interface MyTodoMapper {

    /**
     * 发展党员待办：{@code dev_step_record.status = 2} 的记录即「当前待办」。
     *
     * <p>只保留仍在进行中（{@code dev_applicant.status = 1}）的流程，
     * 已终止/已完成流程遗留的待办记录不应再打扰办理人。</p>
     *
     * @param orgIds   可见组织ID集合，可为 null
     * @param personId 仅本人范围的人员ID，可为 null
     * @return 每行含 record_id / applicant_id / person_id / org_id / step_code /
     *         step_name / person_name / org_name / deadline_time
     */
    @Select("""
            <script>
            SELECT r.record_id, r.applicant_id, r.person_id, r.org_id, r.step_code,
                   r.stage_code, r.deadline_time, r.is_overdue,
                   p.name AS person_name, d.org_name, st.step_name
            FROM dev_step_record r
            JOIN dev_applicant a ON a.applicant_id = r.applicant_id AND a.del_flag = 0 AND a.status = 1
            LEFT JOIN party_person p ON p.person_id = r.person_id AND p.del_flag = 0
            LEFT JOIN sys_dept d ON d.org_id = r.org_id AND d.del_flag = 0
            LEFT JOIN dev_step st ON st.step_code = r.step_code
            WHERE r.status = 2
            <if test="personId != null">
                AND r.person_id = #{personId}
            </if>
            <if test="personId == null and orgIds != null">
                AND r.org_id IN
                <foreach collection="orgIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            ORDER BY r.deadline_time IS NULL, r.deadline_time, r.record_id
            </script>
            """)
    List<Map<String, Object>> selectDevelopTodos(@Param("orgIds") List<Long> orgIds,
                                                 @Param("personId") Long personId);

    /**
     * 三会一课任务待办：已发布（{@code status = 1}）且本组织尚未提交材料的任务。
     *
     * <p>「本组织未提交」用 {@code am_task_submit} 左连接后判空表达，
     * 一把查出避免 N+1。可见性沿用 {@code AmTaskService} 的口径 ——
     * 以<b>发布组织</b> {@code publish_org_id} 套数据权限，上级党委发布的任务下辖支部都能看到。</p>
     *
     * @param orgIds  可见组织ID集合，可为 null
     * @param myOrgId 当前用户所属组织；为 null 时本查询返回空（没有组织就无从谈「本组织未提交」）
     * @return 每行含 task_id / title / task_type / publish_org_name / org_name / deadline / start_date
     */
    @Select("""
            <script>
            SELECT t.task_id, t.title, t.task_type, t.publish_org_id, t.publish_org_name,
                   t.deadline, t.start_date, d.org_name
            FROM am_task t
            LEFT JOIN sys_dept d ON d.org_id = t.publish_org_id AND d.del_flag = 0
            LEFT JOIN am_task_submit s ON s.task_id = t.task_id AND s.org_id = #{myOrgId}
            WHERE t.del_flag = 0 AND t.status = 1 AND s.submit_id IS NULL
            <if test="orgIds != null">
                AND t.publish_org_id IN
                <foreach collection="orgIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            ORDER BY t.deadline IS NULL, t.deadline, t.task_id
            </script>
            """)
    List<Map<String, Object>> selectTaskTodos(@Param("orgIds") List<Long> orgIds,
                                              @Param("myOrgId") Long myOrgId);

    /**
     * 党费待办：账单已生成（{@code status = 0} 未缴）且缴费月份不晚于当前月。
     *
     * <p>月份比较用 {@code dues_year * 12 + dues_month} 折算成绝对月序，
     * 避免跨年时的分支判断 —— 与 {@code PartyDuesService#overdueMonths} 同一套算法。</p>
     *
     * @param orgIds     可见组织ID集合，可为 null
     * @param personId   仅本人范围的人员ID，可为 null
     * @param currentYm  当前月序，{@code 年 * 12 + 月}
     * @return 每行含 dues_id / person_id / person_name / org_id / org_name /
     *         dues_year / dues_month / dues_standard
     */
    @Select("""
            <script>
            SELECT r.dues_id, r.person_id, r.person_name, r.org_id, r.dues_year, r.dues_month,
                   r.dues_standard, d.org_name
            FROM party_dues_record r
            LEFT JOIN sys_dept d ON d.org_id = r.org_id AND d.del_flag = 0
            WHERE r.del_flag = 0 AND r.status = 0
              AND (r.dues_year * 12 + r.dues_month) &lt;= #{currentYm}
            <if test="personId != null">
                AND r.person_id = #{personId}
            </if>
            <if test="personId == null and orgIds != null">
                AND r.org_id IN
                <foreach collection="orgIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            ORDER BY r.dues_year, r.dues_month, r.person_id
            </script>
            """)
    List<Map<String, Object>> selectDuesTodos(@Param("orgIds") List<Long> orgIds,
                                              @Param("personId") Long personId,
                                              @Param("currentYm") int currentYm);

    /**
     * 组织关系转接待办：介绍信已开具（{@code status = 1}）或已超期（4），
     * 且<b>目标是本组织</b> —— 需要接收方落地。
     *
     * <p>只按 {@code to_org_id} 收敛：转出方已经开完介绍信，剩下的动作在接收方。</p>
     *
     * @param orgIds 可见组织ID集合，可为 null
     * @return 每行含 transfer_id / transfer_no / person_id / person_name /
     *         from_org_name / to_org_id / to_org_name / letter_no / expire_date / status
     */
    @Select("""
            <script>
            SELECT t.transfer_id, t.transfer_no, t.person_id, t.person_name,
                   t.from_org_name, t.to_org_id, t.to_org_name,
                   t.letter_no, t.letter_date, t.expire_date, t.status
            FROM party_transfer t
            WHERE t.del_flag = 0 AND t.status IN (1, 4)
            <if test="orgIds != null">
                AND t.to_org_id IN
                <foreach collection="orgIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            ORDER BY t.expire_date IS NULL, t.expire_date, t.transfer_id
            </script>
            """)
    List<Map<String, Object>> selectTransferTodos(@Param("orgIds") List<Long> orgIds);

    // ==================== 数据权限辅助 ====================

    /**
     * 数据权限「本级及以下」：按物化路径前缀取组织 ID 列表。
     * <p>与 {@code PartyLookupMapper.selectOrgIdsByPathPrefix} 同一份 SQL ——
     * 本模块同样不能依赖 party 模块的 Mapper，各写一份。</p>
     */
    @Select("SELECT org_id FROM sys_dept WHERE del_flag = 0 AND org_path LIKE #{prefix}")
    List<Long> selectOrgIdsByPathPrefix(@Param("prefix") String prefix);

    /** 数据权限「自定义」：按用户角色取组织 ID 列表 */
    @Select("""
            SELECT org_id FROM sys_role_dept
            WHERE role_id IN (SELECT role_id FROM sys_user_role WHERE user_id = #{userId})
            """)
    List<Long> selectOrgIdsByUser(@Param("userId") Long userId);
}
