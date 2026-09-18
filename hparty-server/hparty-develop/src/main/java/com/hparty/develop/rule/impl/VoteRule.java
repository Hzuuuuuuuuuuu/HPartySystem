package com.hparty.develop.rule.impl;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.rule.DevContext;
import com.hparty.develop.rule.DevStepRule;
import com.hparty.develop.rule.RuleResult;
import org.springframework.stereotype.Component;

/**
 * 支部大会表决规则（STEP_15 接收预备党员 / STEP_23 讨论转正）。
 *
 * <p>《中国共产党发展党员工作流程图》原文：</p>
 * <blockquote>
 * 有表决权的到会人数<b>必须超过应到会有表决权人数的半数</b>，才能开会；<br>
 * <b>赞成人数超过应到会有表决权的正式党员的半数</b>，才能通过。
 * </blockquote>
 *
 * <p><b>关键细节</b>：两个「半数」的分母都是<b>应到</b>人数，而不是实到人数。
 * 这一点很容易写错 —— 若误用实到人数作分母，到场人数越少反而越容易通过，
 * 与「保证充分讨论」的制度设计意图正好相反。</p>
 *
 * <p>为规避整数除法取整误差，统一改用「乘 2 后比较」：
 * {@code 实到 * 2 > 应到} 等价于「实到超过应到的一半」。</p>
 */
@Component
public class VoteRule implements DevStepRule {

    @Override
    public String ruleKey() {
        return "VOTE_RULE";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String description() {
        return "支部大会表决：到会人数须超过应到会有表决权人数的半数，赞成票须超过应到会有表决权的正式党员的半数";
    }

    @Override
    public RuleResult validate(DevContext ctx) {
        // 驳回/不通过时不需要表决数据
        if (ctx.getForm() != null && ctx.getForm().getResult() != null && ctx.getForm().getResult() != 1) {
            return RuleResult.pass();
        }

        DevHandleDTO.VoteDTO vote = ctx.getForm() == null ? null : ctx.getForm().getVote();
        if (vote == null) {
            return RuleResult.reject("本步骤需召开支部大会投票表决，请填写应到人数、实到人数与赞成票数。");
        }

        Integer shouldAttend = vote.getShouldAttend();
        Integer actualAttend = vote.getActualAttend();
        Integer agree = vote.getAgreeCount();
        int oppose = vote.getOpposeCount() == null ? 0 : vote.getOpposeCount();
        int abstain = vote.getAbstainCount() == null ? 0 : vote.getAbstainCount();

        if (shouldAttend == null || shouldAttend <= 0) {
            return RuleResult.reject("应到会有表决权的正式党员数必须大于 0。");
        }
        if (actualAttend == null || actualAttend < 0) {
            return RuleResult.reject("请填写实到会有表决权人数。");
        }
        if (agree == null || agree < 0) {
            return RuleResult.reject("请填写赞成票数。");
        }

        // ---- 基础一致性校验 ----
        if (actualAttend > shouldAttend) {
            return RuleResult.reject(String.format(
                    "实到会有表决权人数（%d）不能大于应到人数（%d）。", actualAttend, shouldAttend));
        }
        int total = agree + oppose + abstain;
        if (total > actualAttend) {
            return RuleResult.reject(String.format(
                    "赞成 %d + 反对 %d + 弃权 %d = %d 票，超过实到会有表决权人数 %d，请核对。",
                    agree, oppose, abstain, total, actualAttend));
        }

        // ---- 规则一：开会法定人数（分母是「应到」） ----
        int quorum = halfUp(shouldAttend);
        if (actualAttend * 2 <= shouldAttend) {
            return RuleResult.reject(String.format(
                    "到会人数不足，不能开会。应到会有表决权的正式党员 %d 名，"
                            + "须超过半数（至少 %d 名）方可开会，当前实到 %d 名。",
                    shouldAttend, quorum, actualAttend));
        }

        // ---- 规则二：通过所需赞成票（分母同样是「应到」） ----
        if (agree * 2 <= shouldAttend) {
            return RuleResult.reject(String.format(
                    "赞成票未过半，不能通过。应到会有表决权的正式党员 %d 名，"
                            + "须超过半数（至少 %d 票）赞成方可通过，当前赞成 %d 票、反对 %d 票、弃权 %d 票。",
                    shouldAttend, quorum, agree, oppose, abstain));
        }

        return RuleResult.pass();
    }

    /** 计算「超过半数」所需的最小整数：应到 15 → 8，应到 16 → 9 */
    public static int halfUp(int shouldAttend) {
        return shouldAttend / 2 + 1;
    }
}
