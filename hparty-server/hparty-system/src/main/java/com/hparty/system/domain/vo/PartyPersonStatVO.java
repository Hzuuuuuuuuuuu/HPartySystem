package com.hparty.system.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 人员/党员统计结果，供「党组织基本情况」页面展示。
 * <p>统计口径受当前登录用户的数据权限限制，只统计其可见组织范围内的人员。</p>
 */
@Data
public class PartyPersonStatVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 人员总数 */
    private long total;

    /** 党员总数（is_member = 1） */
    private long memberCount;

    /** 正式党员数（member_status = 5） */
    private long fullMemberCount;

    /** 预备党员数（member_status = 4） */
    private long probationaryCount;

    /** 流动党员数（member_status = 6） */
    private long flowingCount;

    /** 入党申请人数（member_status = 1） */
    private long applicantCount;

    /** 入党积极分子数（member_status = 2） */
    private long activistCount;

    /** 发展对象数（member_status = 3） */
    private long candidateCount;

    /** 群众数（member_status = 0） */
    private long massCount;

    /** 党组织总数（sys_dept 中当前用户可见的组织数） */
    private long orgCount;

    /** 各人员状态人数明细，含中文标签，前端图表可直接使用 */
    private List<StatusCount> statusCounts = new ArrayList<>();

    /**
     * 单个人员状态的人数。
     */
    @Data
    public static class StatusCount implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 状态编码 */
        private Integer status;

        /** 状态中文标签 */
        private String label;

        /** 人数 */
        private long count;

        public StatusCount() {
        }

        public StatusCount(Integer status, String label, long count) {
            this.status = status;
            this.label = label;
            this.count = count;
        }
    }
}
