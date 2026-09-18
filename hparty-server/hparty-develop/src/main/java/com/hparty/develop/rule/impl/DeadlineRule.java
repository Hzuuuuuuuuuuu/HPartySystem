package com.hparty.develop.rule.impl;

import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.rule.DevContext;
import com.hparty.develop.rule.DevStepRule;
import com.hparty.develop.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 办理时限规则。
 *
 * <p>对应流程图中的硬性时限：</p>
 * <ul>
 *   <li>STEP_02 党组织派人谈话 —— 收到入党申请书后 <b>1 个月内</b>（30 天）</li>
 *   <li>STEP_17 上级党委审批 —— <b>3 个月内</b>（90 天）</li>
 *   <li>STEP_24 上级党委审批 —— <b>3 个月内</b>（90 天）</li>
 * </ul>
 *
 * <p><b>设计取舍</b>：超期返回 {@code WARN} 而非 {@code REJECT}。
 * 党务工作中超期了事情仍然必须办，若硬性拦截，经办人会绕过系统线下处理，
 * 反而让系统里的数据失真。系统要做的是把超期情况如实暴露出来（记录 is_overdue 并提醒）。</p>
 */
@Component
public class DeadlineRule implements DevStepRule {

    @Override
    public String ruleKey() {
        return "DEADLINE_RULE";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String description() {
        return "须在规定时限内办结，超期将记录并提醒";
    }

    @Override
    public RuleResult validate(DevContext ctx) {
        DevStep step = ctx.getStep();
        Integer deadlineDays = step.getDeadlineDays();
        if (deadlineDays == null || deadlineDays <= 0) {
            return RuleResult.pass();
        }

        Optional<LocalDate> baseDate = resolveBaseDate(ctx);
        if (baseDate.isEmpty()) {
            // 找不到起算点，不做判断，避免误报
            return RuleResult.pass();
        }

        LocalDate deadline = baseDate.get().plusDays(deadlineDays);
        LocalDate today = ctx.today();

        if (!today.isAfter(deadline)) {
            return RuleResult.pass();
        }

        long overdueDays = ChronoUnit.DAYS.between(deadline, today);
        return RuleResult.warn(String.format(
                "本步骤应于 %s 前办结，现已超期 %d 天。请说明情况并尽快办理。",
                deadline, overdueDays));
    }

    /**
     * 计算时限起算点。
     * <p>STEP_02 从「递交入党申请书」之日起算；
     * 其余步骤从**上一步骤**办结之日起算。</p>
     */
    private Optional<LocalDate> resolveBaseDate(DevContext ctx) {
        DevStep step = ctx.getStep();

        // STEP_02 的起算点是申请人递交申请书的时间
        if ("STEP_02".equals(step.getStepCode())) {
            return Optional.ofNullable(ctx.getApplicant().getApplyDate())
                    .or(() -> ctx.firstPassTime("STEP_01").map(java.time.LocalDateTime::toLocalDate));
        }

        // 其余步骤：上一步骤的办结时间
        int prevOrder = step.getStepOrder() - 1;
        if (prevOrder < 1) {
            return Optional.empty();
        }
        String prevStepCode = ctx.getStepMap().values().stream()
                .filter(s -> s.getStepOrder() != null && s.getStepOrder() == prevOrder)
                .map(DevStep::getStepCode)
                .findFirst()
                .orElse(null);
        if (prevStepCode == null) {
            return Optional.empty();
        }
        return ctx.lastPassTime(prevStepCode).map(java.time.LocalDateTime::toLocalDate);
    }
}
