package com.hparty.develop.rule.impl;

import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.rule.DevContext;
import com.hparty.develop.rule.DevStepRule;
import com.hparty.develop.rule.RuleResult;
import org.springframework.stereotype.Component;

/**
 * 分支节点规则（STEP_23 支部大会讨论预备党员转正）。
 *
 * <p>这是全流程 25 步中<b>唯一的三出口节点</b>，流程图规定：</p>
 * <ol>
 *   <li>认真履行党员义务、具备党员条件的 → <b>按期转为正式党员</b></li>
 *   <li>需要继续考察和教育的 → 可以<b>延长 1 次预备期</b>，延长不能少于半年，最长不超过 1 年</li>
 *   <li>不履行党员义务、不具备党员条件的 → <b>取消预备党员资格</b></li>
 * </ol>
 *
 * <p>「延长 1 次」是硬性上限 —— 延长后仍不合格的，只能取消资格，不能再次延长。</p>
 */
@Component
public class BranchRule implements DevStepRule {

    /** 延长预备期的最短月数 */
    public static final int MIN_EXTEND_MONTHS = 6;
    /** 延长预备期的最长月数 */
    public static final int MAX_EXTEND_MONTHS = 12;
    /** 全流程允许延长的次数上限 */
    public static final int MAX_EXTEND_TIMES = 1;

    /** 出口类型：按期转正 */
    public static final int TYPE_REGULAR = 1;
    /** 出口类型：延长预备期 */
    public static final int TYPE_EXTEND = 2;
    /** 出口类型：取消预备党员资格 */
    public static final int TYPE_DISQUALIFY = 3;

    @Override
    public String ruleKey() {
        return "BRANCH_RULE";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public String description() {
        return "转正讨论三选一：按期转正 / 延长预备期（6-12 个月，仅限 1 次）/ 取消预备党员资格";
    }

    @Override
    public RuleResult validate(DevContext ctx) {
        // 仅对分支节点生效
        if (ctx.getStep().getIsBranch() == null || ctx.getStep().getIsBranch() != 1) {
            return RuleResult.pass();
        }

        // 驳回/不通过时不涉及出口选择
        if (ctx.getForm() != null && ctx.getForm().getResult() != null && ctx.getForm().getResult() != 1) {
            return RuleResult.pass();
        }

        Integer resultType = ctx.getForm() == null ? null : ctx.getForm().getResultType();
        if (resultType == null) {
            return RuleResult.reject("本步骤为分支节点，请选择大会讨论结果：按期转正 / 延长预备期 / 取消预备党员资格。");
        }

        DevApplicant applicant = ctx.getApplicant();
        int extended = applicant.getProbationExtendCount() == null ? 0 : applicant.getProbationExtendCount();

        return switch (resultType) {
            case TYPE_REGULAR -> RuleResult.pass();

            case TYPE_EXTEND -> {
                if (extended >= MAX_EXTEND_TIMES) {
                    yield RuleResult.reject(String.format(
                            "该同志已延长预备期 %d 次，按规定只能延长 %d 次。"
                                    + "如仍需继续考察，应当取消其预备党员资格。",
                            extended, MAX_EXTEND_TIMES));
                }
                Integer months = ctx.getForm().getExtendMonths();
                if (months == null) {
                    yield RuleResult.reject("选择「延长预备期」时必须填写延长月数。");
                }
                if (months < MIN_EXTEND_MONTHS) {
                    yield RuleResult.reject(String.format(
                            "延长预备期不能少于半年（%d 个月），当前填写 %d 个月。",
                            MIN_EXTEND_MONTHS, months));
                }
                if (months > MAX_EXTEND_MONTHS) {
                    yield RuleResult.reject(String.format(
                            "延长预备期最长不超过 1 年（%d 个月），当前填写 %d 个月。",
                            MAX_EXTEND_MONTHS, months));
                }
                yield RuleResult.pass();
            }

            case TYPE_DISQUALIFY -> RuleResult.pass();

            default -> RuleResult.reject("大会讨论结果取值不合法，应为 1=按期转正 / 2=延长预备期 / 3=取消预备党员资格。");
        };
    }
}
