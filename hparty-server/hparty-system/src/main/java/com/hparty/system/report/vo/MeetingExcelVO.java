package com.hparty.system.report.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 三会一课开展情况导出的一行。
 */
@Data
@ExcelIgnoreUnannotated
@HeadRowHeight(22)
@ContentRowHeight(18)
public class MeetingExcelVO {

    @ExcelProperty(value = "序号", index = 0)
    @ColumnWidth(6)
    private Integer seqNo;

    @ExcelProperty(value = "会议类型", index = 1)
    @ColumnWidth(14)
    private String meetingTypeLabel;

    @ExcelProperty(value = "会议标题", index = 2)
    @ColumnWidth(32)
    private String title;

    @ExcelProperty(value = "主办党组织", index = 3)
    @ColumnWidth(24)
    private String orgName;

    @ExcelProperty(value = "会议日期", index = 4)
    @ColumnWidth(13)
    private String meetingDate;

    @ExcelProperty(value = "开始时间", index = 5)
    @ColumnWidth(11)
    private String startTime;

    @ExcelProperty(value = "结束时间", index = 6)
    @ColumnWidth(11)
    private String endTime;

    @ExcelProperty(value = "会议地点", index = 7)
    @ColumnWidth(20)
    private String place;

    @ExcelProperty(value = "主持人", index = 8)
    @ColumnWidth(11)
    private String hostName;

    @ExcelProperty(value = "记录人", index = 9)
    @ColumnWidth(11)
    private String recorderName;

    @ExcelProperty(value = "应到人数", index = 10)
    @ColumnWidth(10)
    private Integer shouldAttend;

    @ExcelProperty(value = "实到人数", index = 11)
    @ColumnWidth(10)
    private Integer actualAttend;

    @ExcelProperty(value = "出勤率(%)", index = 12)
    @ColumnWidth(11)
    private BigDecimal attendRate;

    @ExcelProperty(value = "状态", index = 13)
    @ColumnWidth(10)
    private String statusLabel;
}
