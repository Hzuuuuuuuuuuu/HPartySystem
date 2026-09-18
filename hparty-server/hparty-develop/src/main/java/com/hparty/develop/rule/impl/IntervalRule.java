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
 * 时间间隔规则（硬性约束）。
 *
 * <p>对应流程图中的培养期要求：</p>
 * <ul>
 *   <li>STEP_06 培养教育考察 —— 距确定为入党积极分子须满 <b>1 年</b>（365 天）</li>
 *   <li>STEP_07 确定发展对象 —— 须经 <b>1 年以上</b>培养教育和考察</li>
 *   <li>STEP_21 继续教育考察 —— 预备期 <b>1 年</b></li>
 *   <li>STEP_22 提出转正申请 —— 预备期满 1 年方可提出</li>
 * </ul>
 *
 * <p>与 {@link DeadlineRule} 不同，这里是**不可逾越**的硬性条件，
 * 未满期限一律 {@code REJECT}。</p>
 */
@Component
public class IntervalRule implements DevStepRule {

    @Override
    public String ruleKey() {
        return "INTERVAL_RULE";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String description() {
        return "距基准步骤须满规定期限（如培养教育满 1 年）";
    }

    @Override
    public RuleResult validate(DevContext ctx) {
        DevStep step = ctx.getStep();

        // 周期性考察步骤：只在「推进到下一步」时才校验期限。
        // 单纯记录一次考察不应受期限约束，否则半年一次的考察永远记不成。
        if (isPeriodic(step) && !isAdvance(ctx)) {
            return RuleResult.pass();
        }

        Integer intervalDays = step.getIntervalDays();
        if (intervalDays == null || intervalDays <= 0) {
            return RuleResult.pass();
        }

        String baseStepCode = step.getIntervalBaseStep();
        if (baseStepCode == null || baseStepCode.isBlank()) {
            return RuleResult.pass();
        }

        Optional<LocalDate> baseDate = resolveBaseDate(ctx, baseStepCode);
        if (baseDate.isEmpty()) {
            // 基准步骤尚未办结，交由流程状态机本身拦（当前步骤不可能是这个）
            return RuleResult.reject(String.format(
                    "尚未完成【%s】，无法办理本步骤。", stepNameOf(ctx, baseStepCode)));
        }

        long elapsed = ChronoUnit.DAYS.between(baseDate.get(), ctx.today());
        if (elapsed >= intervalDays) {
            return RuleResult.pass();
        }

        long remain = intervalDays - elapsed;
        return RuleResult.reject(String.format(
                "距【%s】仅 %d 天，须满 %d 天方可办理本步骤，还需等待 %d 天（约 %d 个月）。",
                stepNameOf(ctx, baseStepCode), elapsed, intervalDays, remain,
                Math.max(1, Math.round(remain / 30.0))));
    }

    /**
     * 解析基准日期。
     * <p>优先取基准步骤的实际办结时间；若该步骤没有留下记录
     * （例如历史数据导入），退回到申请人档案上的对应日期字段。</p>
     */
    private Optional<LocalDate> resolveBaseDate(DevContext ctx, String baseStepCode) {
        // STEP_06 是周期性步骤，起算点取**首次**通过时间
        Optional<LocalDate> fromRecord = ctx.firstPassTime(baseStepCode)
                .map(java.time.LocalDateTime::toLocalDate);
        if (fromRecord.isPresent()) {
            return fromRecord;
        }

        // 回退：按基准步骤编码取申请人档案上的日期字段
        var applicant = ctx.getApplicant();
        return switch (baseStepCode) {
            case "STEP_01" -> Optional.ofNullable(applicant.getApplyDate());
            case "STEP_03" -> Optional.ofNullable(applicant.getActivistDate());
            case "STEP_07" -> Optional.ofNullable(applicant.getCandidateDate());
            case "STEP_17", "STEP_20" -> Optional.ofNullable(applicant.getProbationaryDate());
            default -> Optional.empty();
        };
    }

    private String stepNameOf(DevContext ctx, String stepCode) {
        DevStep s = ctx.step(stepCode);
        return s == null ? stepCode : s.getStepName();
    }

    /** 是否为周期性考察型步骤（step_type=2） */
    private boolean isPeriodic(DevStep step) {
        return step.getStepType() != null && step.getStepType() == 2;
    }

    /** 本次提交是否为「推进到下一步」 */
    private boolean isAdvance(DevContext ctx) {
        return ctx.getForm() != null && Boolean.TRUE.equals(ctx.getForm().getAdvance());
    }
}
