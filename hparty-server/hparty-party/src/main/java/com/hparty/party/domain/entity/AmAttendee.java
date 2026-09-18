package com.hparty.party.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会议参会人员
 */
@Data
@TableName("am_attendee")
public class AmAttendee implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "attendee_id", type = IdType.AUTO)
    private Long attendeeId;

    /** 会议ID */
    private Long meetingId;

    /** 人员ID */
    private Long personId;

    /** 姓名（冗余） */
    private String personName;

    /** 出席情况：0=未签到 1=已签到 2=请假 3=缺席 */
    private Integer attendStatus;

    /** 签到时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signTime;

    /** 请假事由 */
    private String leaveReason;
}
