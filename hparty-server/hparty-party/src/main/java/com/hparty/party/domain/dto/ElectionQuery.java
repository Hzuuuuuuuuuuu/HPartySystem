package com.hparty.party.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 党组织换届查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ElectionQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 换届组织（精确匹配） */
    private Long orgId;

    /** 状态：0=筹备中 1=进行中 2=已完成 3=已终止 */
    private Integer status;

    /** 关键词：换届名称 / 事由模糊匹配 */
    private String keyword;
}
