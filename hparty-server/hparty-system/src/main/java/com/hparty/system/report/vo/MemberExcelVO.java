package com.hparty.system.report.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/**
 * 党员名册导出的一行。
 *
 * <p>身份证号与联系电话是敏感字段，导出前按当前用户权限决定是否打码
 * （见 {@code ReportExportService}）。</p>
 */
@Data
@ExcelIgnoreUnannotated
@HeadRowHeight(22)
@ContentRowHeight(18)
public class MemberExcelVO {

    @ExcelProperty(value = "序号", index = 0)
    @ColumnWidth(6)
    private Integer seqNo;

    @ExcelProperty(value = "姓名", index = 1)
    @ColumnWidth(12)
    private String name;

    @ExcelProperty(value = "性别", index = 2)
    @ColumnWidth(6)
    private String sexLabel;

    @ExcelProperty(value = "身份证号", index = 3)
    @ColumnWidth(22)
    private String idCard;

    @ExcelProperty(value = "出生日期", index = 4)
    @ColumnWidth(13)
    private String birthDate;

    @ExcelProperty(value = "年龄", index = 5)
    @ColumnWidth(6)
    private Integer age;

    @ExcelProperty(value = "民族", index = 6)
    @ColumnWidth(8)
    private String nation;

    @ExcelProperty(value = "联系电话", index = 7)
    @ColumnWidth(15)
    private String phone;

    @ExcelProperty(value = "学历", index = 8)
    @ColumnWidth(10)
    private String education;

    @ExcelProperty(value = "工作单位", index = 9)
    @ColumnWidth(24)
    private String workUnit;

    @ExcelProperty(value = "所属党组织", index = 10)
    @ColumnWidth(24)
    private String orgName;

    @ExcelProperty(value = "人员状态", index = 11)
    @ColumnWidth(12)
    private String memberStatusLabel;

    @ExcelProperty(value = "政治面貌", index = 12)
    @ColumnWidth(12)
    private String politicalStatus;

    @ExcelProperty(value = "入党时间", index = 13)
    @ColumnWidth(13)
    private String fullMemberDate;

    @ExcelProperty(value = "党龄(年)", index = 14)
    @ColumnWidth(9)
    private Integer partyAge;
}
