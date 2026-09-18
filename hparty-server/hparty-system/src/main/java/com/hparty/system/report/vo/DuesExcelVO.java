package com.hparty.system.report.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 党费收缴台账导出的一行（一人一月一条）。
 */
@Data
@ExcelIgnoreUnannotated
@HeadRowHeight(22)
@ContentRowHeight(18)
public class DuesExcelVO {

    @ExcelProperty(value = "序号", index = 0)
    @ColumnWidth(6)
    private Integer seqNo;

    @ExcelProperty(value = "姓名", index = 1)
    @ColumnWidth(12)
    private String personName;

    @ExcelProperty(value = "所属党组织", index = 2)
    @ColumnWidth(24)
    private String orgName;

    @ExcelProperty(value = "年份", index = 3)
    @ColumnWidth(8)
    private Integer duesYear;

    @ExcelProperty(value = "月份", index = 4)
    @ColumnWidth(8)
    private Integer duesMonth;

    @ExcelProperty(value = "缴纳基数(元)", index = 5)
    @ColumnWidth(14)
    private BigDecimal duesBase;

    @ExcelProperty(value = "应缴金额(元)", index = 6)
    @ColumnWidth(14)
    private BigDecimal duesStandard;

    @ExcelProperty(value = "实缴金额(元)", index = 7)
    @ColumnWidth(14)
    private BigDecimal duesPaid;

    @ExcelProperty(value = "缴纳日期", index = 8)
    @ColumnWidth(13)
    private String payDate;

    @ExcelProperty(value = "缴纳方式", index = 9)
    @ColumnWidth(11)
    private String payTypeLabel;

    @ExcelProperty(value = "状态", index = 10)
    @ColumnWidth(10)
    private String statusLabel;

    @ExcelProperty(value = "是否欠缴", index = 11)
    @ColumnWidth(10)
    private String overdueLabel;
}
