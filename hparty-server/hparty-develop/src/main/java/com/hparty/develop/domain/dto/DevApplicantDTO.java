package com.hparty.develop.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增 / 修改发展对象。
 */
@Data
public class DevApplicantDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 修改时传入 */
    private Long applicantId;

    /** 关联的人员档案 ID */
    @NotNull(message = "请选择人员")
    private Long personId;

    /** 所属党组织 */
    private Long orgId;

    /** 起始阶段，默认 STAGE_1 */
    private String currentStage;

    /** 起始步骤，默认 STEP_01 */
    private String currentStep;

    /** 支部书记 person_id */
    private Long branchSecretaryId;

    /** 培养联系人 person_id（1-2 名） */
    private List<Long> trainerIds;

    /** 入党介绍人 person_id（2 名） */
    private List<Long> introducerIds;

    /** 入党志愿书编号 */
    private String volunteerBookNo;

    /** 递交入党申请书日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyDate;

    private String remark;
}
