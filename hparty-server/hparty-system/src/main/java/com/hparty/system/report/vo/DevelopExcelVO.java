package com.hparty.system.report.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/**
 * 发展党员进度表导出的一行。
 */
@Data
@ExcelIgnoreUnannotated
@HeadRowHeight(22)
@ContentRowHeight(18)
public class DevelopExcelVO {

    @ExcelProperty(value = "序号", index = 0)
    @ColumnWidth(6)
    private Integer seqNo;

    @ExcelProperty(value = "姓名", index = 1)
    @ColumnWidth(12)
    private String personName;

    @ExcelProperty(value = "所属党组织", index = 2)
    @ColumnWidth(24)
    private String orgName;

    @ExcelProperty(value = "当前阶段", index = 3)
    @ColumnWidth(22)
    private String stageName;

    @ExcelProperty(value = "当前步骤", index = 4)
    @ColumnWidth(24)
    private String stepName;

    @ExcelProperty(value = "流程状态", index = 5)
    @ColumnWidth(12)
    private String statusLabel;

    @ExcelProperty(value = "进度(%)", index = 6)
    @ColumnWidth(9)
    private Integer progress;

    @ExcelProperty(value = "递交申请书日期", index = 7)
    @ColumnWidth(15)
    private String applyDate;

    @ExcelProperty(value = "确定为积极分子", index = 8)
    @ColumnWidth(15)
    private String activistDate;

    @ExcelProperty(value = "确定为发展对象", index = 9)
    @ColumnWidth(15)
    private String candidateDate;

    @ExcelProperty(value = "成为预备党员", index = 10)
    @ColumnWidth(15)
    private String probationaryDate;

    @ExcelProperty(value = "转为正式党员", index = 11)
    @ColumnWidth(15)
    private String fullMemberDate;

    @ExcelProperty(value = "预备期满日", index = 12)
    @ColumnWidth(15)
    private String probationEndDate;
}
