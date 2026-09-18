package com.hparty.develop.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发展党员申请人实例
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dev_applicant")
public class DevApplicant extends BaseEntity {

    /** 申请人实例ID */
    @TableId(value = "applicant_id", type = IdType.AUTO)
    private Long applicantId;

    /** 人员ID */
    private Long personId;

    /** 所属党组织 */
    private Long orgId;

    /** 当前阶段编码 */
    private String currentStage;

    /** 当前步骤编码 */
    private String currentStep;

    /** 状态：1=进行中 2=已完成(转为正式党员) 3=已终止(取消资格) 4=已中止(离开单位等) */
    private Integer status;

    /** 进度百分比 0-100 */
    private Integer progress;

    /** 递交申请书日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyDate;

    /** 确定为积极分子日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate activistDate;

    /** 确定为发展对象日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate candidateDate;

    /** 成为预备党员日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate probationaryDate;

    /** 转为正式党员日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fullMemberDate;

    /**
     * 预备期满日。
     * <p>等于「成为预备党员日期 + 1 年」，每次延长预备期后按月顺延。
     * STEP_21 推进、STEP_22 提出转正申请都以它为准 —— 若只按入党宣誓日加固定
     * 365 天计算，延长预备期就不会产生任何约束效果。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate probationEndDate;

    /** 已延长预备期次数（最多1次） */
    private Integer probationExtendCount;

    /** 累计延长月数 */
    private Integer probationExtendMonths;

    /** 支部书记 */
    private Long branchSecretaryId;

    /** 培养联系人ID，逗号分隔（1-2名） */
    private String trainerIds;

    /** 入党介绍人ID，逗号分隔（2名） */
    private String introducerIds;

    /** 入党志愿书编号 */
    private String volunteerBookNo;

    /** 流程完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    /** 终止原因 */
    private String terminateReason;

    /** 备注 */
    private String remark;
}
