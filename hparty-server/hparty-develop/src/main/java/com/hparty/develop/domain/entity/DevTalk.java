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
 * 谈话记录
 */
@Data
@TableName("dev_talk")
public class DevTalk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 谈话ID */
    @TableId(value = "talk_id", type = IdType.AUTO)
    private Long talkId;

    /** 申请人实例ID */
    private Long applicantId;

    /** 步骤编码：STEP_02 / STEP_16 */
    private String stepCode;

    /** 谈话类型：1=党组织派人谈话 2=上级党委派人谈话 */
    private Integer talkType;

    /** 谈话日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate talkDate;

    /** 谈话地点 */
    private String talkPlace;

    /** 谈话人 person_id */
    private Long talkerId;

    /** 谈话人姓名 */
    private String talkerName;

    /** 谈话人职务 */
    private String talkerPosition;

    /** 谈话内容 */
    private String content;

    /** 谈话结论/对能否入党的意见 */
    private String conclusion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
