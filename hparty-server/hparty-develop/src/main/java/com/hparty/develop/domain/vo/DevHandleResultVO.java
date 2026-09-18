package com.hparty.develop.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 步骤办理结果。
 */
@Data
public class DevHandleResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long applicantId;

    /** 办理后的当前阶段 */
    private String currentStage;
    private String currentStageName;

    /** 办理后的当前步骤 */
    private String currentStep;
    private String currentStepName;

    /** 流程状态：1=进行中 2=已完成 3=已终止 4=已中止 */
    private Integer status;
    private String statusLabel;

    /** 进度百分比 */
    private Integer progress;

    /** 本次办理结果描述，如「已通过，流转至【STEP_03 推荐和确定入党积极分子】」 */
    private String resultText;

    /** 是否已走完全部 25 步 */
    private boolean finished;

    /** 规则提醒（不阻断办理，但需要经办人知晓，如已超期、材料未归档） */
    private List<String> warnings = new ArrayList<>();
}
