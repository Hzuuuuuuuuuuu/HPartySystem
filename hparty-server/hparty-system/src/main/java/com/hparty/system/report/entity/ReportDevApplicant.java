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
 * 导出专用的 {@code dev_applicant} 最小投影（见 {@link ReportDuesRecord} 的说明）。
 */
@Data
@TableName("dev_applicant")
public class ReportDevApplicant implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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

    /** 状态：1=进行中 2=已完成 3=已终止 4=已中止 */
    private Integer status;

    /** 进度百分比 0-100 */
    private Integer progress;

    /** 递交申请书日期 */
    private LocalDate applyDate;

    /** 确定为积极分子日期 */
    private LocalDate activistDate;

    /** 确定为发展对象日期 */
    private LocalDate candidateDate;

    /** 成为预备党员日期 */
    private LocalDate probationaryDate;

    /** 转为正式党员日期 */
    private LocalDate fullMemberDate;

    /** 预备期满日 */
    private LocalDate probationEndDate;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}
