package com.hparty.develop.rule;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.rule.impl.BranchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STEP_23 转正讨论三出口规则测试。
 *
 * <p>流程图规定三个出口：按期转正 / 延长预备期（≥半年、≤1年、<b>仅 1 次</b>）/ 取消预备党员资格。</p>
 *
 * <p>「仅 1 次」是硬性上限 —— 延长后仍不合格的，只能取消资格，不能再次延长。</p>
 */
class BranchRuleTest {

    private final BranchRule rule = new BranchRule();

    private DevStep step23() {
        return Fixtures.branchStep("STEP_23", "支部大会讨论", 23);
    }

    private DevHandleDTO branch(Integer resultType, Integer extendMonths) {
        DevHandleDTO f = Fixtures.pass();
        f.setResultType(resultType);
        f.setExtendMonths(extendMonths);
        return f;
    }

    private DevContext ctx(DevHandleDTO form, DevApplicant applicant) {
        return Fixtures.ctx(step23(), form, applicant);
    }

    private DevContext ctx(DevHandleDTO form) {
        return ctx(form, Fixtures.applicant());
    }

    @Test
    @DisplayName("出口一：按期转为正式党员")
    void regularTransfer() {
        assertTrue(rule.validate(ctx(branch(BranchRule.TYPE_REGULAR, null))).isAllowed());
    }

    @Test
    @DisplayName("出口二：延长 6 个月与 12 个月是合法边界")
    void extendBoundaries() {
        assertTrue(rule.validate(ctx(branch(BranchRule.TYPE_EXTEND, 6))).isAllowed(), "6 个月是下限，应允许");
        assertTrue(rule.validate(ctx(branch(BranchRule.TYPE_EXTEND, 12))).isAllowed(), "12 个月是上限，应允许");
        assertTrue(rule.validate(ctx(branch(BranchRule.TYPE_EXTEND, 9))).isAllowed());
    }

    @Test
    @DisplayName("出口二：延长 5 个月（不足半年）应拒绝")
    void extendTooShort() {
        RuleResult r = rule.validate(ctx(branch(BranchRule.TYPE_EXTEND, 5)));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("不能少于半年"));
    }

    @Test
    @DisplayName("出口二：延长 13 个月（超过 1 年）应拒绝")
    void extendTooLong() {
        RuleResult r = rule.validate(ctx(branch(BranchRule.TYPE_EXTEND, 13)));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("最长不超过 1 年"));
    }

    @Test
    @DisplayName("出口二：未填延长月数应拒绝")
    void extendWithoutMonths() {
        RuleResult r = rule.validate(ctx(branch(BranchRule.TYPE_EXTEND, null)));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("必须填写延长月数"));
    }

    @Test
    @DisplayName("延长只能 1 次 —— 已延长过的不能再延长")
    void extendOnlyOnce() {
        DevApplicant already = Fixtures.applicant();
        already.setProbationExtendCount(1);

        RuleResult r = rule.validate(ctx(branch(BranchRule.TYPE_EXTEND, 6), already));
        assertTrue(r.isRejected(), "已延长过一次，不得再次延长");
        assertTrue(r.message().contains("只能延长"));
        assertTrue(r.message().contains("取消"), "提示应引导走取消资格出口");
    }

    @Test
    @DisplayName("出口三：取消预备党员资格")
    void disqualify() {
        assertTrue(rule.validate(ctx(branch(BranchRule.TYPE_DISQUALIFY, null))).isAllowed());
    }

    @Test
    @DisplayName("分支节点未选出口应拒绝")
    void missingResultType() {
        RuleResult r = rule.validate(ctx(branch(null, null)));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("请选择"));
    }

    @Test
    @DisplayName("非法出口值应拒绝")
    void invalidResultType() {
        assertTrue(rule.validate(ctx(branch(99, null))).isRejected());
    }

    @Test
    @DisplayName("非分支节点不受此规则约束")
    void nonBranchStepSkips() {
        DevStep normal = Fixtures.step("STEP_15", "支部大会讨论", 15);
        DevContext ctx = Fixtures.ctx(normal, branch(null, null), Fixtures.applicant());
        assertTrue(rule.validate(ctx).isAllowed(),
                "非分支节点不应因未填 resultType 被拦");
    }

    @Test
    @DisplayName("驳回/不通过时不涉及出口选择")
    void nonPassResultSkips() {
        assertTrue(rule.validate(ctx(Fixtures.reject())).isAllowed());
    }
}
