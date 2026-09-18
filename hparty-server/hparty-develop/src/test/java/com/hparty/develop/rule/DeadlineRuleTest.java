package com.hparty.develop.rule;

import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.domain.entity.DevStepRecord;
import com.hparty.develop.rule.impl.DeadlineRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 办理时限规则测试。
 *
 * <p><b>这条规则只警告、不阻断，是有意为之</b>：党务工作中超期了事情仍然必须办。
 * 若硬性拦截，经办人会绕过系统线下处理，反而让系统数据失真。
 * 系统要做的是把超期情况如实暴露出来。</p>
 *
 * <p>本测试锁定「超期只 WARN 不 REJECT」这一行为 —— 防止后续有人「顺手修正」成阻断。</p>
 */
class DeadlineRuleTest {

    private final DeadlineRule rule = new DeadlineRule();

    private DevContext step02Ctx(DevApplicant applicant) {
        DevStep step02 = Fixtures.deadlineStep("STEP_02", "党组织派人谈话", 2, 30);
        return Fixtures.ctx(step02, Fixtures.pass(), applicant);
    }

    @Test
    @DisplayName("STEP_02 超期时只警告，不阻断办理")
    void overdueProducesWarningNotRejection() {
        DevApplicant a = Fixtures.applicant();
        a.setApplyDate(Fixtures.TODAY.minusDays(46));   // 申请书递交于 46 天前，限期 30 天

        RuleResult r = rule.validate(step02Ctx(a));

        assertFalse(r.isRejected(), "超期不得阻断办理 —— 党务工作超期了事情仍要办");
        assertTrue(r.isWarn(), "应返回 WARN 级别");
        assertTrue(r.isAllowed(), "WARN 应当被允许继续");
        assertTrue(r.message().contains("超期"), "提示应说明已超期");
        assertTrue(r.message().contains("16"), "应算出超期 16 天，实际：" + r.message());
    }

    @Test
    @DisplayName("期限内应通过")
    void withinDeadline() {
        DevApplicant a = Fixtures.applicant();
        a.setApplyDate(Fixtures.TODAY.minusDays(15));
        assertEquals(RuleResult.Level.PASS, rule.validate(step02Ctx(a)).level());
    }

    @Test
    @DisplayName("恰好第 30 天仍在期限内（超期从第 31 天算起）")
    void exactlyOnDeadline() {
        DevApplicant a = Fixtures.applicant();
        a.setApplyDate(Fixtures.TODAY.minusDays(30));
        assertEquals(RuleResult.Level.PASS, rule.validate(step02Ctx(a)).level(),
                "第 30 天当天不算超期");
    }

    @Test
    @DisplayName("未配置期限的步骤直接放行")
    void noDeadlineConfigured() {
        DevStep plain = Fixtures.step("STEP_19", "编入党支部和党小组", 19);
        DevContext ctx = Fixtures.ctx(plain, Fixtures.pass(), Fixtures.applicant());
        assertEquals(RuleResult.Level.PASS, rule.validate(ctx).level());
    }

    @Test
    @DisplayName("非 STEP_02 的步骤从上一步办结时间起算")
    void otherStepsCountFromPreviousStep() {
        // STEP_17 上级党委审批，限期 90 天，起算点是上一步（STEP_16）的办结时间
        DevStep step16 = Fixtures.step("STEP_16", "上级党委派人谈话", 16);
        DevStep step17 = Fixtures.deadlineStep("STEP_17", "上级党委审批", 17, 90);
        Map<String, DevStep> map = Fixtures.stepMap(step16, step17);

        // 上一步 100 天前办结 → 超期 10 天
        List<DevStepRecord> overdue = List.of(Fixtures.doneRecord("STEP_16", Fixtures.TODAY.minusDays(100)));
        DevContext ctx = Fixtures.ctx(step17, Fixtures.pass(), Fixtures.applicant(), overdue, map);
        RuleResult r = rule.validate(ctx);
        assertTrue(r.isWarn(), "超期 10 天应警告");
        assertTrue(r.message().contains("10"), "应算出超期 10 天，实际：" + r.message());

        // 上一步 10 天前办结 → 未超期
        List<DevStepRecord> fresh = List.of(Fixtures.doneRecord("STEP_16", Fixtures.TODAY.minusDays(10)));
        DevContext ctx2 = Fixtures.ctx(step17, Fixtures.pass(), Fixtures.applicant(), fresh, map);
        assertEquals(RuleResult.Level.PASS, rule.validate(ctx2).level());
    }

    @Test
    @DisplayName("找不到起算点时不误报")
    void unknownBaseDateIsSilent() {
        // STEP_02 但申请人没有申请日期，也没有 STEP_01 记录
        DevApplicant a = Fixtures.applicant();
        a.setApplyDate(null);
        assertEquals(RuleResult.Level.PASS, rule.validate(step02Ctx(a)).level(),
                "起算点缺失时应放行，不能凭空报超期");
    }
}
