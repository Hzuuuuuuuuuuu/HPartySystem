package com.hparty.develop.rule.impl;

import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.rule.DevContext;
import com.hparty.develop.rule.DevStepRule;
import com.hparty.develop.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 预备期规则（STEP_21 继续教育考察、STEP_22 提出转正申请）。
 *
 * <p>流程图规定预备期为 1 年，且「需要继续考察和教育的，可以延长 1 次预备期，
 * 延长不能少于半年，最长不超过 1 年」。因此期满日不是简单的
 * 「入党宣誓日 + 365 天」，而是**可以顺延的**。</p>
 *
 * <p><b>为什么不能复用 {@link IntervalRule}</b>：{@code IntervalRule} 的基准日期取
 * 「基准步骤首次办结时间」，这是个**不可变的历史事实**。延长预备期改变的是
 * 「什么时候算满期」这个**目标日期**，而不是历史。若沿用固定间隔，
 * 支部大会决定延长 6 个月后，当事人第二天就能推进并再次提出转正申请，
 * 延长决定形同虚设。</p>
 *
 * <p>因此本规则读取 {@code dev_applicant.probation_end_date}，
 * 由流程在「上级党委审批接收预备党员」时置为「预备党员日期 + 1 年」，
 * 每次延长预备期时按月顺延。</p>
 */
@Component
public class ProbationPeriodRule implements DevStepRule {

    @Override
    public String ruleKey() {
        return "PROBATION_PERIOD_RULE";
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public String description() {
        return "须预备期满方可推进，延长预备期后顺延";
    }

    @Override
    public RuleResult validate(DevContext ctx) {
        DevStep step = ctx.getStep();

        // 周期性考察步骤：只记录、不推进时不受期限约束
        boolean periodic = step.getStepType() != null && step.getStepType() == 2;
        boolean advance = ctx.getForm() != null && Boolean.TRUE.equals(ctx.getForm().getAdvance());
        if (periodic && !advance) {
            return RuleResult.pass();
        }

        DevApplicant applicant = ctx.getApplicant();
        LocalDate endDate = resolveProbationEndDate(applicant);
        if (endDate == null) {
            // 历史数据未记录期满日，退回按「预备党员日期 + 1 年」推算；
            // 两者都拿不到时不阻拦，避免误伤早期数据
            return RuleResult.pass();
        }

        LocalDate today = ctx.today();
        if (!today.isBefore(endDate)) {
            return RuleResult.pass();
        }

        long remainDays = ChronoUnit.DAYS.between(today, endDate);
        int extendedCount = applicant.getProbationExtendCount() == null ? 0 : applicant.getProbationExtendCount();
        int extendedMonths = applicant.getProbationExtendMonths() == null ? 0 : applicant.getProbationExtendMonths();

        String extendHint = extendedCount > 0
                ? String.format("（已延长预备期 %d 次、累计 %d 个月）", extendedCount, extendedMonths)
                : "";

        return RuleResult.reject(String.format(
                "预备期未满%s。预备期满日为 %s，还需等待 %d 天（约 %d 个月）。",
                extendHint, endDate, remainDays, Math.max(1, Math.round(remainDays / 30.0))));
    }

    /** 取预备期满日；缺失时按预备党员日期 + 1 年推算 */
    private LocalDate resolveProbationEndDate(DevApplicant applicant) {
        if (applicant.getProbationEndDate() != null) {
            return applicant.getProbationEndDate();
        }
        LocalDate probationary = applicant.getProbationaryDate();
        return probationary == null ? null : probationary.plusYears(1);
    }
}
