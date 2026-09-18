package com.hparty.develop.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 发展党员卡片（对应图5 卡片墙的一张卡）。
 */
@Data
public class DevApplicantCardVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long applicantId;
    private Long personId;
    private String personName;
    private String avatar;
    private Integer sex;

    private Long orgId;
    private String orgName;

    /** 当前阶段 */
    private String currentStage;
    private String currentStageName;

    /** 当前步骤 */
    private String currentStep;
    private String currentStepName;

    /** 流程状态 */
    private Integer status;
    private String statusLabel;

    /** 进度百分比 */
    private Integer progress;

    /** 人员状态（群众/申请人/积极分子/发展对象/预备党员/正式党员） */
    private Integer memberStatus;
    private String memberStatusLabel;

    /** 关键日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate activistDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate candidateDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate probationaryDate;

    /** 当前步骤的应办结时间，用于卡片上标红超期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime deadlineTime;

    /** 是否已超期 */
    private Boolean overdue;
}
