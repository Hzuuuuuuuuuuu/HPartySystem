package com.hparty.system.report.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 党费台账导出条件（与党费列表页的筛选项一致）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportDuesQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属党组织 */
    private Long orgId;

    /** 年份 */
    private Integer duesYear;

    /** 月份 1-12 */
    private Integer duesMonth;

    /** 状态：0=未缴 1=已缴 2=免缴 3=补缴 */
    private Integer status;

    /** 姓名（模糊匹配） */
    private String personName;

    /** 是否欠缴：0=否 1=是 */
    private Integer isOverdue;
}
