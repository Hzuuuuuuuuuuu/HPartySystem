package com.hparty.system.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 导出专用的 {@code am_meeting} 最小投影（见 {@link ReportDuesRecord} 的说明）。
 */
@Data
@TableName("am_meeting")
public class ReportMeeting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会议ID */
    @TableId(value = "meeting_id", type = IdType.AUTO)
    private Long meetingId;

    /** 会议类型 */
    private String meetingType;

    /** 会议标题 */
    private String title;

    /** 主办党组织 */
    private Long orgId;

    /** 会议日期 */
    private LocalDate meetingDate;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 会议地点 */
    private String place;

    /** 主持人姓名 */
    private String hostName;

    /** 记录人 */
    private String recorderName;

    /** 应到人数 */
    private Integer shouldAttend;

    /** 实到人数 */
    private Integer actualAttend;

    /** 状态：0=草稿 1=待召开 2=进行中 3=已结束 4=已归档 */
    private Integer status;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}
