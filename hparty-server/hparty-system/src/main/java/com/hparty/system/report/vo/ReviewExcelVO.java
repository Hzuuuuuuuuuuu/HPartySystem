package com.hparty.system.report.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 民主评议党员结果导出的一行（对应 P0-2 的评议明细）。
 */
@Data
@ExcelIgnoreUnannotated
@HeadRowHeight(22)
@ContentRowHeight(18)
public class ReviewExcelVO {

    @ExcelProperty(value = "序号", index = 0)
    @ColumnWidth(6)
    private Integer seqNo;

    @ExcelProperty(value = "评议批次", index = 1)
    @ColumnWidth(28)
    private String reviewTitle;

    @ExcelProperty(value = "评议年度", index = 2)
    @ColumnWidth(10)
    private Integer reviewYear;

    @ExcelProperty(value = "姓名", index = 3)
    @ColumnWidth(12)
    private String personName;

    @ExcelProperty(value = "所属党组织", index = 4)
    @ColumnWidth(24)
    private String orgName;

    @ExcelProperty(value = "自评", index = 5)
    @ColumnWidth(9)
    private BigDecimal selfScore;

    @ExcelProperty(value = "互评平均分", index = 6)
    @ColumnWidth(11)
    private BigDecimal peerScore;

    @ExcelProperty(value = "互评人数", index = 7)
    @ColumnWidth(10)
    private Integer peerCount;

    @ExcelProperty(value = "群众评议", index = 8)
    @ColumnWidth(10)
    private BigDecimal massScore;

    @ExcelProperty(value = "组织评定", index = 9)
    @ColumnWidth(10)
    private BigDecimal orgScore;

    @ExcelProperty(value = "综合得分", index = 10)
    @ColumnWidth(10)
    private BigDecimal totalScore;

    @ExcelProperty(value = "等次", index = 11)
    @ColumnWidth(10)
    private String gradeLabel;

    @ExcelProperty(value = "组织评定意见", index = 12)
    @ColumnWidth(32)
    private String orgComment;

    @ExcelProperty(value = "处置意见", index = 13)
    @ColumnWidth(24)
    private String dispose;
}
