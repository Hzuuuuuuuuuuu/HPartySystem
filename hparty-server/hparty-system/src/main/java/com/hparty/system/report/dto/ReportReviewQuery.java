package com.hparty.system.report.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 民主评议结果导出条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportReviewQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评议批次ID（指定则只导这一批） */
    private Long reviewId;

    /** 评议年度 */
    private Integer reviewYear;

    /** 所属党组织 */
    private Long orgId;

    /** 等次：1=优秀 2=合格 3=基本合格 4=不合格 */
    private Integer grade;
}
