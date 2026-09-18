package com.hparty.develop.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 发展党员年度计划完成进度。
 */
@Data
public class DevPlanProgressVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 计划ID */
    private Long planId;

    /** 计划所属组织 */
    private Long orgId;

    /** 组织名称 */
    private String orgName;

    /** 计划年度 */
    private Integer planYear;

    /** 计划发展党员数 */
    private Integer planCount;

    /** 入党积极分子培养目标数 */
    private Integer activistTarget;

    /** 状态：0=草稿 1=已下达 2=执行中 3=已完成 */
    private Integer status;

    /** 本年度已达到 STEP_07（确定发展对象）及之后的人数 */
    private Long reachedCount;

    /** 完成率（%，保留 1 位小数），planCount 为 0 时返回 0 */
    private BigDecimal rate;

    /** 年度进度分组：如已下达/执行中/已完成 */
    private List<QuotaProgress> quotas;

    /**
     * 指标分解到下级组织的完成情况。
     */
    @Data
    public static class QuotaProgress implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 指标ID */
        private Long quotaId;

        /** 被分配的组织 */
        private Long orgId;

        /** 组织名称 */
        private String orgName;

        /** 分配名额 */
        private Integer quotaCount;

        /** 该组织本年度达标人数 */
        private Long reachedCount;

        /** 该组织完成率（%） */
        private BigDecimal rate;

        /** 备注 */
        private String remark;
    }
}
