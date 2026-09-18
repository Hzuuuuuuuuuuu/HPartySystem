package com.hparty.party.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.party.domain.entity.PartyTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.Map;

/**
 * 组织关系转接 Mapper。
 *
 * <p>跨域取数（人员档案、发展党员流程）用注解 SQL 直接写 ——
 * {@code hparty-party} 不依赖 {@code hparty-system} 与 {@code hparty-develop}，
 * 不能引用它们的实体，做法与 {@link PartyLookupMapper} 一致。</p>
 */
@Mapper
public interface PartyTransferMapper extends BaseMapper<PartyTransfer> {

    /**
     * 取人员档案的关键字段（用于发起转接时补全姓名与原组织）。
     *
     * @param personId 人员ID
     * @return 含 person_id / name / org_id 的一行；人员不存在时返回 null
     */
    @Select("SELECT person_id, name, org_id FROM party_person WHERE person_id = #{personId} AND del_flag = 0")
    Map<String, Object> selectPersonBrief(@Param("personId") Long personId);

    /**
     * 取该人员「进行中」的发展党员流程（{@code status=1}）。
     *
     * <p>接收组织关系时用来判断是否需要把流程一并迁到新组织 ——
     * 流程图要求「培养教育时间可连续计算」，因此 **不重置流程**，只改归属组织。</p>
     *
     * @param personId 人员ID
     * @return 含 applicant_id / current_step / current_stage 的一行；没有进行中的流程时返回 null
     */
    @Select("""
            SELECT applicant_id, current_step, current_stage FROM dev_applicant
            WHERE person_id = #{personId} AND status = 1 AND del_flag = 0
            ORDER BY applicant_id LIMIT 1
            """)
    Map<String, Object> selectRunningApplicant(@Param("personId") Long personId);

    /**
     * 把进行中的发展党员流程迁到新组织（只改归属，不动步骤与日期）。
     *
     * <p>逻辑删除条件手写，因为 MyBatis-Plus 的 {@code @TableLogic} 只作用于内置方法。</p>
     *
     * @param applicantId 申请人实例ID
     * @param orgId       目标组织ID
     * @return 受影响行数
     */
    @Update("UPDATE dev_applicant SET org_id = #{orgId} WHERE applicant_id = #{applicantId} AND del_flag = 0")
    int updateApplicantOrg(@Param("applicantId") Long applicantId, @Param("orgId") Long orgId);

    /**
     * 变更人员的组织归属。
     *
     * <p><b>只改 {@code org_id}</b>：{@code member_status} 与
     * apply_date / activist_date / candidate_date / probationary_date / full_member_date
     * 等里程碑日期一律不动 —— 组织关系转接不是重新入党。</p>
     *
     * @param personId 人员ID
     * @param orgId    目标组织ID
     * @return 受影响行数
     */
    @Update("UPDATE party_person SET org_id = #{orgId} WHERE person_id = #{personId} AND del_flag = 0")
    int updatePersonOrg(@Param("personId") Long personId, @Param("orgId") Long orgId);

    /**
     * 统计某年度已生成的转接单数量，用于拼 {@code transfer_no}。
     *
     * @param yearPrefix 单号前缀，形如 {@code ZZ-2026-%}
     * @return 条数（含已逻辑删除的，避免复用旧号撞唯一索引）
     */
    @Select("SELECT COUNT(*) FROM party_transfer WHERE transfer_no LIKE #{yearPrefix}")
    int countByNoPrefix(@Param("yearPrefix") String yearPrefix);

    /**
     * 统计某年度已开具的介绍信数量，用于拼 {@code letter_no}。
     *
     * <p>必须查 {@code letter_no} 列 —— 介绍信号与转接单号是两套独立编号，
     * 早期误用 {@code transfer_no} 统计导致每次开信都生成同一个号。</p>
     *
     * @param yearPrefix 介绍信前缀，形如 {@code JS-2026-%}
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM party_transfer WHERE letter_no LIKE #{yearPrefix}")
    int countByLetterNoPrefix(@Param("yearPrefix") String yearPrefix);

    /**
     * 把已超期（{@code status=1} 且 {@code expire_date < 今天}）的转接单标记为 4。
     *
     * <p>口径与 {@code PartyTransferService.computeOverdue} 完全一致，两者结果相同：
     * 读取时现算保证界面永远正确，本方法负责把结果落库，便于「口袋党员」清单做带索引的批量筛查。
     * 定时任务未配置时可由接口按需触发（幂等）。</p>
     *
     * @param today 今天
     * @return 被标记为超期的条数
     */
    @Update("""
            UPDATE party_transfer SET status = 4
            WHERE del_flag = 0 AND status = 1 AND expire_date IS NOT NULL AND expire_date < #{today}
            """)
    int markExpired(@Param("today") LocalDate today);
}
