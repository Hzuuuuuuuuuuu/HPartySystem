package com.hparty.party.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 民主评议批次查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评议年度 */
    private Integer reviewYear;

    /** 组织ID */
    private Long orgId;

    /** 状态：0=草稿 1=自评中 2=互评中 3=组织评定中 4=已公示 5=已完成 */
    private Integer status;

    /** 标题关键字 */
    private String keyword;
}
