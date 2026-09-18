package com.hparty.develop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.develop.domain.entity.DevPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 发展党员年度计划 Mapper。
 *
 * <p>进度计算跨 {@code dev_applicant} / {@code dev_step} / {@code sys_dept} 三张表，
 * 用注解 SQL 直接查，不引入 system 模块的实体。</p>
 */
@Mapper
public interface DevPlanMapper extends BaseMapper<DevPlan> {

    /** 组织物化路径（形如 /1/3/7/），用于取整棵子树 */
    @Select("SELECT org_path FROM sys_dept WHERE org_id = #{orgId} AND del_flag = 0")
    String selectOrgPath(@Param("orgId") Long orgId);

    /** 组织 ID → 组织名称 */
    @Select("SELECT org_name FROM sys_dept WHERE org_id = #{orgId} AND del_flag = 0")
    String selectOrgName(@Param("orgId") Long orgId);

    /**
     * 取根组织 ID（最顶层的那一个党委）。
     *
     * <p>给「未分配所属组织」的账号（如超级管理员）查询计划进度时兜底用 ——
     * 党委的计划通过指标分解覆盖了下辖各支部，正是「整体进度」的自然视角。
     * 系统按单棵党组织树设计（见 docs/01-系统设计.md 第 9 节），因此根组织只有一个。</p>
     */
    @Select("SELECT org_id FROM sys_dept WHERE parent_id = 0 AND del_flag = 0 "
            + "ORDER BY org_id LIMIT 1")
    Long selectRootOrgId();

    /** 批量：组织 ID → 组织名称 */
    @Select("""
            <script>
            SELECT org_id, org_name FROM sys_dept
            WHERE del_flag = 0 AND org_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectOrgNames(@Param("ids") List<Long> ids);

    /**
     * 年度计划完成人数：本年度已达到「STEP_07 确定发展对象」及之后的人数。
     *
     * <p><b>「已达到 STEP_07」的判定</b>：把 {@code dev_applicant.current_step} 关联
     * {@code dev_step} 比较 {@code step_order >= 7}。这比硬编码步骤编码稳妥 ——
     * 步骤模板的编码一旦调整，这里不用跟着改。</p>
     *
     * <p><b>「本年度」的判定</b>：取「确定为发展对象日期」（{@code candidate_date}，
     * 即跨过 STEP_07 的里程碑日）所在年份 = 计划年度；历史数据里该日期没回写时
     * 退回「递交申请书日期」。发展对象只是发展党员的一个中间节点，
     * 若不限定年份，2024 年确定的发展对象会被算进 2026 年的计划完成数。</p>
     *
     * <p>统计范围是计划组织**及其整棵子树**（党委下达的计划要覆盖下辖支部）；
     * 终止（{@code status=3}）的流程不计入。</p>
     *
     * @param orgPath   计划组织的物化路径
     * @param planYear  计划年度
     * @return 达标人数
     */
    @Select("""
            SELECT COUNT(*)
            FROM dev_applicant a
            JOIN dev_step s ON s.step_code = a.current_step
            WHERE a.del_flag = 0
              AND a.status IN (1, 2)
              AND s.step_order >= 7
              AND YEAR(COALESCE(a.candidate_date, a.apply_date)) = #{planYear}
              AND a.org_id IN (
                  SELECT d.org_id FROM sys_dept d
                  WHERE d.del_flag = 0 AND d.org_path LIKE CONCAT(#{orgPath}, '%'))
            """)
    long countReachedStep07(@Param("orgPath") String orgPath, @Param("planYear") Integer planYear);

    /**
     * 按组织分组统计达标人数，用于「指标分解」逐组织显示完成情况。
     *
     * @return 每行含 org_id / cnt
     */
    @Select("""
            SELECT a.org_id AS org_id, COUNT(*) AS cnt
            FROM dev_applicant a
            JOIN dev_step s ON s.step_code = a.current_step
            WHERE a.del_flag = 0
              AND a.status IN (1, 2)
              AND s.step_order >= 7
              AND YEAR(COALESCE(a.candidate_date, a.apply_date)) = #{planYear}
              AND a.org_id IN (
                  SELECT d.org_id FROM sys_dept d
                  WHERE d.del_flag = 0 AND d.org_path LIKE CONCAT(#{orgPath}, '%'))
            GROUP BY a.org_id
            """)
    List<Map<String, Object>> countReachedStep07ByOrg(@Param("orgPath") String orgPath,
                                                      @Param("planYear") Integer planYear);
}
