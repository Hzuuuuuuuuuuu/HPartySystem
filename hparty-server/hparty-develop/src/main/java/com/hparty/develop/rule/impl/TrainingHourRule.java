package com.hparty.develop.rule.impl;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.rule.DevContext;
import com.hparty.develop.rule.DevStepRule;
import com.hparty.develop.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 集中培训规则（STEP_11）。
 *
 * <p>流程图要求：集中培训「不少于 3 天或不少于 24 学时」，
 * 且「未经培训的，除个别特殊情况外，不能发展入党」。</p>
 *
 * <p>天数与学时是「或」的关系 —— 满足其一即可。</p>
 */
@Component
public class TrainingHourRule implements DevStepRule {

    /** 默认要求：3 天 */
    private static final BigDecimal DEFAULT_DAYS = new BigDecimal("3");
    /** 默认要求：24 学时 */
    private static final BigDecimal DEFAULT_HOURS = new BigDecimal("24");

    @Override
    public String ruleKey() {
        return "TRAINING_HOUR_RULE";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String description() {
        return "集中培训须不少于 3 天或不少于 24 学时";
    }

    @Override
    public RuleResult validate(DevContext ctx) {
        DevStep step = ctx.getStep();
        BigDecimal minDays = step.getMinTrainingDays() == null
                ? DEFAULT_DAYS : BigDecimal.valueOf(step.getMinTrainingDays());
        BigDecimal minHours = step.getMinTrainingHours() == null
                ? DEFAULT_HOURS : BigDecimal.valueOf(step.getMinTrainingHours());

        DevHandleDTO.TrainingDTO training =
                ctx.getForm() == null ? null : ctx.getForm().getTraining();

        // 驳回/不通过时无需填写培训信息
        if (ctx.getForm() != null && ctx.getForm().getResult() != null && ctx.getForm().getResult() != 1) {
            return RuleResult.pass();
        }

        if (training == null) {
            return RuleResult.reject("请填写集中培训信息。《发展党员工作流程图》要求：未经培训的，除个别特殊情况外，不能发展入党。");
        }

        if (training.getIsQualified() != null && training.getIsQualified() != 1) {
            return RuleResult.reject("该同志集中培训未结业，不能发展入党。");
        }

        BigDecimal days = training.getTrainDays() == null ? BigDecimal.ZERO : training.getTrainDays();
        BigDecimal hours = training.getTrainHours() == null ? BigDecimal.ZERO : training.getTrainHours();

        boolean daysOk = days.compareTo(minDays) >= 0;
        boolean hoursOk = hours.compareTo(minHours) >= 0;

        if (daysOk || hoursOk) {
            return RuleResult.pass();
        }

        return RuleResult.reject(String.format(
                "集中培训时长不足：当前 %s 天 / %s 学时，要求不少于 %s 天或不少于 %s 学时。",
                days.stripTrailingZeros().toPlainString(),
                hours.stripTrailingZeros().toPlainString(),
                minDays.stripTrailingZeros().toPlainString(),
                minHours.stripTrailingZeros().toPlainString()));
    }
}
