package com.hparty.develop.rule.impl;

import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.domain.entity.DevStepRecord;
import com.hparty.develop.rule.DevContext;
import com.hparty.develop.rule.DevStepRule;
import com.hparty.develop.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 周期性考察规则。
 *
 * <p>应用于两个「要持续记录、反复办理」的步骤：</p>
 * <ul>
 *   <li>STEP_06 培养教育考察 —— 党支部<b>每半年</b>考察 1 次（180 天）</li>
 *   <li>STEP_21 继续教育考察 —— 预备期内定期考察（90 天）</li>
 * </ul>
 *
 * <p>普通步骤一次办结即流转，这两步会在同一步骤下不断追加记录。
 * 本规则限制**记录频率**，防止一次性补录多条充数。</p>
 */
@Component
public class PeriodicRule implements DevStepRule {

    @Override
    public String ruleKey() {
        return "PERIODIC_RULE";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String description() {
        return "周期性考察步骤，两次记录之间须间隔规定天数";
    }

    @Override
    public RuleResult validate(DevContext ctx) {
        DevStep step = ctx.getStep();

        // 推进到下一步时不记录考察内容，无需校验记录频率
        if (ctx.getForm() != null && Boolean.TRUE.equals(ctx.getForm().getAdvance())) {
            return RuleResult.pass();
        }

        Integer periodicDays = step.getPeriodicDays();
        if (periodicDays == null || periodicDays <= 0) {
            return RuleResult.pass();
        }

        Optional<DevStepRecord> last = ctx.lastRecord(step.getStepCode());
        if (last.isEmpty() || last.get().getHandleTime() == null) {
            // 首次记录，不受间隔限制
            return RuleResult.pass();
        }

        LocalDate lastDate = last.get().getHandleTime().toLocalDate();
        long elapsed = ChronoUnit.DAYS.between(lastDate, ctx.today());

        if (elapsed >= periodicDays) {
            return RuleResult.pass();
        }

        long remain = periodicDays - elapsed;
        return RuleResult.reject(String.format(
                "本步骤要求每 %d 天记录 1 次。上次考察为 %s（距今 %d 天），还需等待 %d 天。",
                periodicDays, lastDate, elapsed, remain));
    }
}
