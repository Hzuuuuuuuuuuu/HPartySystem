package com.hparty.develop.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 支部大会表决记录
 */
@Data
@TableName("dev_vote")
public class DevVote implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 表决ID */
    @TableId(value = "vote_id", type = IdType.AUTO)
    private Long voteId;

    /** 申请人实例ID */
    private Long applicantId;

    /** 步骤编码：STEP_15 / STEP_23 */
    private String stepCode;

    /** 会议日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate meetingDate;

    /** 会议地点 */
    private String meetingPlace;

    /** 应到会有表决权的正式党员数 */
    private Integer shouldAttend;

    /** 实到会有表决权人数 */
    private Integer actualAttend;

    /** 开会法定人数（应到半数，向下取整+1） */
    private Integer quorumRequired;

    /** 是否达到开会法定人数：0=否 1=是 */
    private Integer isQuorumMet;

    /** 赞成票 */
    private Integer agreeCount;

    /** 反对票 */
    private Integer opposeCount;

    /** 弃权票 */
    private Integer abstainCount;

    /** 通过所需赞成票数（应到半数，向下取整+1） */
    private Integer passRequired;

    /** 是否通过：0=否 1=是 */
    private Integer isPassed;

    /** 结果类型（STEP_23用）：1=按期转正 2=延长预备期 3=取消预备党员资格 */
    private Integer resultType;

    /** 延长预备期月数（>=6 且 <=12） */
    private Integer extendMonths;

    /** 主持人 person_id */
    private Long hostId;

    /** 主持人姓名 */
    private String hostName;

    /** 记录人 */
    private String recorderName;

    /** 会议记录 */
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
