package com.hparty.party.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 党员教育活动查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EducationQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主办党组织（精确匹配） */
    private Long orgId;

    /** 类型：1=党课 2=专题培训 3=在线学习 4=实践锻炼 5=集中轮训 */
    private Integer activityType;

    /** 状态：0=草稿 1=报名中 2=进行中 3=已结束 */
    private Integer status;

    /** 关键词：活动名称 / 主讲人 / 主办单位模糊匹配 */
    private String keyword;
}
