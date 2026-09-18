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
 * 上级审批备案记录
 */
@Data
@TableName("dev_approval")
public class DevApproval implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 审批ID */
    @TableId(value = "approval_id", type = IdType.AUTO)
    private Long approvalId;

    /** 申请人实例ID */
    private Long applicantId;

    /** 步骤编码：STEP_04/08/13/17/18/24 */
    private String stepCode;

    /** 类型：1=备案 2=预审 3=审批 */
    private Integer approvalType;

    /** 申请组织（党支部） */
    private Long applyOrgId;

    /** 审批组织（上级党委/再上一级） */
    private Long approveOrgId;

    /** 提交日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate submitDate;

    /** 应办结日期（3个月内） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deadlineDate;

    /** 审批日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate approveDate;

    /** 结果：1=同意 2=不同意 */
    private Integer result;

    /** 审批意见 */
    private String opinion;

    /** 审批人 person_id */
    private Long approverId;

    /** 审批人姓名 */
    private String approverName;

    /** 是否超期 */
    private Integer isOverdue;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
