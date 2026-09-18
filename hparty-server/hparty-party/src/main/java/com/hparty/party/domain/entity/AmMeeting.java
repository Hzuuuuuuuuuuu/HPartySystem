package com.hparty.party.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 会议（三会一课/主题党日/组织生活会）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("am_meeting")
public class AmMeeting extends BaseEntity {

    /** 会议ID */
    @TableId(value = "meeting_id", type = IdType.AUTO)
    private Long meetingId;

    /** 会议类型：MEMBER_ASSEMBLY=党员大会 BRANCH_COMMITTEE=支部委员会 PARTY_GROUP=党小组会 PARTY_LECTURE=党课 THEME_PARTY_DAY=主题党日 ORG_LIFE=组织生活会 */
    private String meetingType;

    /** 会议标题 */
    private String title;

    /** 主办党组织 */
    private Long orgId;

    /** 党小组（党小组会时） */
    private Long groupId;

    /** 会议内容 */
    private String content;

    /** 会议日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate meetingDate;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 会议地点 */
    private String place;

    /** 主持人 */
    private Long hostId;

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

    /** 封面图文件ID */
    private Long coverFileId;
}
