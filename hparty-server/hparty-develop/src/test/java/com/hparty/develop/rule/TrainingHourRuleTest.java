package com.hparty.develop.rule;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.rule.impl.TrainingHourRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集中培训规则测试（STEP_11）。
 *
 * <p>流程图要求「不少于 3 天<b>或</b>不少于 24 学时」——
 * 天数与学时是<b>或</b>的关系，满足其一即可，这是最容易写错的地方
 * （写成「且」会让只满足其一的正常培训被判不合格）。</p>
 */
class TrainingHourRuleTest {

    private final TrainingHourRule rule = new TrainingHourRule();

    private DevContext ctx(DevHandleDTO form) {
        DevStep step = Fixtures.step("STEP_11", "开展集中培训", 11);
        step.setMinTrainingDays(3);
        step.setMinTrainingHours(24);
        return Fixtures.ctx(step, form, Fixtures.applicant());
    }

    @Test
    @DisplayName("恰好 3 天、0 学时 —— 满足天数条件即可通过（或关系）")
    void daysOnly() {
        assertTrue(rule.validate(ctx(Fixtures.training(3, 0))).isAllowed(),
                "3 天 0 学时：满足「不少于3天」，应通过（天数与学时为或关系）");
    }

    @Test
    @DisplayName("恰好 24 学时、0 天 —— 满足学时条件即可通过")
    void hoursOnly() {
        assertTrue(rule.validate(ctx(Fixtures.training(0, 24))).isAllowed(),
                "0 天 24 学时：满足「不少于24学时」，应通过");
    }

    @Test
    @DisplayName("超出边界应通过")
    void beyondBoundary() {
        assertTrue(rule.validate(ctx(Fixtures.training(5, 40))).isAllowed());
        assertTrue(rule.validate(ctx(Fixtures.training(2, 24))).isAllowed(), "2天24学时：学时达标");
        assertTrue(rule.validate(ctx(Fixtures.training(3, 12))).isAllowed(), "3天12学时：天数达标");
    }

    @Test
    @DisplayName("两项都不达标应拒绝，并给出可读提示")
    void bothInsufficient() {
        RuleResult r = rule.validate(ctx(Fixtures.training(2, 12)));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("不足"));
        assertTrue(r.message().contains("3"), "提示应含要求的天数");
        assertTrue(r.message().contains("24"), "提示应含要求的学时");
    }

    @Test
    @DisplayName("未填培训信息应拒绝")
    void missingTraining() {
        RuleResult r = rule.validate(ctx(Fixtures.pass()));   // 没有 training 字段
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("未经培训"), "提示应引用流程图原文的告诫");
    }

    @Test
    @DisplayName("培训未结业应拒绝")
    void notQualified() {
        DevHandleDTO f = Fixtures.training(5, 40);
        f.getTraining().setIsQualified(0);
        RuleResult r = rule.validate(ctx(f));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("未结业"));
    }

    @Test
    @DisplayName("驳回/不通过时无需填写培训信息")
    void nonPassResultSkips() {
        assertTrue(rule.validate(ctx(Fixtures.reject())).isAllowed());
    }

    @Test
    @DisplayName("步骤未配置要求时用默认值 3 天 / 24 学时")
    void usesDefaultsWhenNotConfigured() {
        DevStep bare = Fixtures.step("STEP_11", "开展集中培训", 11);   // 未设 minTrainingDays/Hours
        DevContext ctx = Fixtures.ctx(bare, Fixtures.training(2, 12), Fixtures.applicant());
        assertTrue(rule.validate(ctx).isRejected(), "默认要求 3 天或 24 学时，2天12学时应被拒");
    }
}
