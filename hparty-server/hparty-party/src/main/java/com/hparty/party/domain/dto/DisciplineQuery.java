package com.hparty.party.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 党纪学习教育查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DisciplineQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 组织ID（精确匹配） */
    private Long orgId;

    /** 类型：1=条例学习 2=警示教育 3=专题党课 4=知识测试 5=案例研讨 */
    private Integer studyType;

    /** 关键词：学习主题 / 主讲人模糊匹配 */
    private String keyword;
}
