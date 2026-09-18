package com.hparty.party.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 党费缴纳记录查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DuesRecordQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属党组织（精确匹配） */
    private Long orgId;

    /** 年份 */
    private Integer duesYear;

    /** 月份 1-12 */
    private Integer duesMonth;

    /** 状态：0=未缴 1=已缴 2=免缴 3=补缴 */
    private Integer status;

    /** 姓名（模糊匹配） */
    private String personName;
}
