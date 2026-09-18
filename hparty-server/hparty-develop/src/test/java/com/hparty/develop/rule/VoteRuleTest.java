package com.hparty.develop.rule;

import com.hparty.develop.rule.impl.VoteRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支部大会表决规则测试。
 *
 * <p>这是全系统最容易写错、后果最严重的一条规则。流程图规定：</p>
 * <blockquote>
 * 有表决权的到会人数必须超过应到会有表决权人数的半数，才能开会；<br>
 * 赞成人数超过应到会有表决权的正式党员的半数，才能通过。
 * </blockquote>
 *
 * <p><b>核心陷阱</b>：两个半数的分母都是「<b>应到</b>」而不是「实到」。
 * 若误用实到人数作分母，到场人数越少反而越容易通过 ——
 * 与「保证充分讨论」的制度设计意图正好相反。</p>
 */
class VoteRuleTest {

    private final VoteRule rule = new VoteRule();

    @Test
    @DisplayName("应到 16 人时，到会须至少 9 人（8 人恰好半数，不够）")
    void quorumBoundary() {
        // 应到16、实到16、赞成16 —— 对照组，确保其它条件是满足的
        assertTrue(rule.validate(ctx(Fixtures.vote(16, 16, 16, 0, 0))).isAllowed());

        // 实到 8 = 恰好半数，未「超过半数」→ 不能开会
        RuleResult r8 = rule.validate(ctx(Fixtures.vote(16, 8, 8, 0, 0)));
        assertTrue(r8.isRejected(), "8/16 恰好半数，不应允许开会");
        assertTrue(r8.message().contains("到会人数不足"), "提示应指出到会人数不足");

        // 实到 9 > 半数 → 可以开会
        assertTrue(rule.validate(ctx(Fixtures.vote(16, 9, 9, 0, 0))).isAllowed());
    }

    @Test
    @DisplayName("应到 15 人时，须至少 8 人（7.5 向上取整，不能是 7）")
    void quorumBoundaryOdd() {
        // 应到15 时半数=7.5
        RuleResult r7 = rule.validate(ctx(Fixtures.vote(15, 7, 7, 0, 0)));
        assertTrue(r7.isRejected(), "7/15 未超过半数（7.5），不应允许开会");

        assertTrue(rule.validate(ctx(Fixtures.vote(15, 8, 8, 0, 0))).isAllowed(),
                "8/15 超过半数，应允许");
    }

    @Test
    @DisplayName("赞成票的分母是「应到」不是「实到」—— 这是最易写错之处")
    void agreeDenominatorIsShouldAttend() {
        // 应到16、实到9（刚够开会）、赞成9 —— 全部到会者都赞成
        // 若分母误用「实到」9，则会误判为「9 > 4.5，通过」
        // 正确算法：分母是应到16，需 > 8，即至少 9 票 → 9 > 8 通过
        assertTrue(rule.validate(ctx(Fixtures.vote(16, 9, 9, 0, 0))).isAllowed(),
                "9/16 赞成票超过应到半数（8），应通过");

        // 换个场景：应到 30、实到 16（刚够开会）、赞成 16（全部到会者赞成）
        // 若分母误用「实到」16，会误判 16 > 8 通过
        // 正确算法：分母是应到30，需 > 15，即至少 16 票 → 16 > 15 通过（恰好通过）
        assertTrue(rule.validate(ctx(Fixtures.vote(30, 16, 16, 0, 0))).isAllowed());

        // 应到 30、实到 16、赞成 15 —— 15 未超过 15（半数），不通过
        RuleResult r = rule.validate(ctx(Fixtures.vote(30, 16, 15, 1, 0)));
        assertTrue(r.isRejected(), "15/30 未超过半数，不应通过");
        assertTrue(r.message().contains("赞成票未过半"));
    }

    @Test
    @DisplayName("赞成票未过半时给出可读的提示，含应到人数与所需票数")
    void rejectMessageIsInformative() {
        RuleResult r = rule.validate(ctx(Fixtures.vote(16, 16, 7, 9, 0)));
        assertTrue(r.isRejected());
        String msg = r.message();
        assertTrue(msg.contains("16"), "提示应含应到人数");
        assertTrue(msg.contains("9"), "提示应含所需票数（9）");
        assertTrue(msg.contains("7"), "提示应含实际赞成票数");
    }

    @Test
    @DisplayName("空值与一致性校验")
    void validationGuards() {
        // 未填表决数据
        RuleResult none = rule.validate(ctx(Fixtures.pass()));
        assertTrue(none.isRejected());
        assertTrue(none.message().contains("需召开支部大会投票表决"));

        // 应到为 0
        assertTrue(rule.validate(ctx(Fixtures.vote(0, 0, 0, 0, 0))).isRejected());

        // 实到大于应到
        assertTrue(rule.validate(ctx(Fixtures.vote(10, 11, 10, 0, 0))).isRejected());

        // 票数合计超过实到人数
        RuleResult sum = rule.validate(ctx(Fixtures.vote(16, 10, 6, 4, 3)));
        assertTrue(sum.isRejected(), "6+4+3=13 > 实到10，应被拒绝");
        assertTrue(sum.message().contains("超过实到"));
    }

    @Test
    @DisplayName("驳回与不通过时无需填写表决数据")
    void nonPassResultSkipsVoteValidation() {
        // 驳回时没有 vote 字段，不应因「未填表决数据」被拦
        assertTrue(rule.validate(ctx(Fixtures.reject())).isAllowed());
    }

    @Test
    @DisplayName("halfUp 辅助方法与规则口径一致")
    void halfUpMatchesRule() {
        // 规则里用「乘 2 比较」规避整除取整误差，这里验证辅助方法的取值与之相符
        assertEquals(8, VoteRule.halfUp(15), "应到15 应至少8票");
        assertEquals(9, VoteRule.halfUp(16), "应到16 应至少9票");
        assertEquals(1, VoteRule.halfUp(1));
        assertEquals(2, VoteRule.halfUp(2));
    }

    private DevContext ctx(com.hparty.develop.domain.dto.DevHandleDTO form) {
        return Fixtures.ctx(Fixtures.step("STEP_15", "支部大会讨论", 15), form, Fixtures.applicant());
    }
}
