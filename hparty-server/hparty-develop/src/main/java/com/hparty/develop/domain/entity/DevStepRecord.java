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
import java.time.LocalDateTime;

/**
 * 步骤办理记录
 */
@Data
@TableName("dev_step_record")
public class DevStepRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;

    /** 申请人实例ID */
    private Long applicantId;

    /** 人员ID（冗余，便于按人查） */
    private Long personId;

    /** 组织ID（冗余，便于数据权限） */
    private Long orgId;

    /** 步骤编码 */
    private String stepCode;

    /** 阶段编码（冗余） */
    private String stageCode;

    /** 同一步骤的第几次办理（周期性步骤用） */
    private Integer seqNo;

    /** 结论：1=通过 2=驳回 3=不通过 4=延长预备期 5=取消资格 */
    private Integer result;

    /** 办理意见 */
    private String opinion;

    /** 考察记录/谈话记录内容（周期性步骤用） */
    private String content;

    /** 办理人 user_id */
    private Long handleUserId;

    /** 办理人 person_id */
    private Long handlePersonId;

    /** 办理人姓名（冗余） */
    private String handleName;

    /** 办理组织ID */
    private Long handleOrgId;

    /** 办理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

    /** 应办结时间（办理时限预警用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadlineTime;

    /** 是否超期：0=否 1=是 */
    private Integer isOverdue;

    /** 流转到的下一步骤 */
    private String nextStepCode;

    /** 状态：1=已办结 2=待办 3=已作废 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
