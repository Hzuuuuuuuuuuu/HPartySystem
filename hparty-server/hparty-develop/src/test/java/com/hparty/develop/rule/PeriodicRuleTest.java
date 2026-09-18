package com.hparty.develop.rule;

import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.domain.entity.DevStepRecord;
import com.hparty.develop.rule.impl.PeriodicRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 周期性考察规则测试。
 *
 * <p>限制 STEP_06 / STEP_21 两次考察记录之间的最小间隔，
 * 防止一次性补录多条充数。</p>
 */
class PeriodicRuleTest {

    private final PeriodicRule rule = new PeriodicRule();

    /** STEP_06 培养教育考察：每半年一次 */
    private DevStep step06() {
        return Fixtures.periodicStep("STEP_06", "培养教育考察", 6, 180);
    }

    private DevContext ctx(List<DevStepRecord> records) {
        return Fixtures.ctx(step06(), Fixtures.pass(), Fixtures.applicant(), records, Fixtures.stepMap());
    }

    @Test
    @DisplayName("首次记录不受间隔限制")
    void firstRecordAlwaysAllowed() {
        assertTrue(rule.validate(ctx(List.of())).isAllowed(),
                "还没有任何考察记录时，第一次记录应放行");
    }

    @Test
    @DisplayName("距上次考察已满 180 天，可以再记录")
    void intervalSatisfied() {
        List<DevStepRecord> records = List.of(
                Fixtures.doneRecord("STEP_06", Fixtures.TODAY.minusDays(184), 1));
        assertTrue(rule.validate(ctx(records)).isAllowed());
    }

    @Test
    @DisplayName("距上次考察恰好 180 天，可以再记录")
    void exactInterval() {
        List<DevStepRecord> records = List.of(
                Fixtures.doneRecord("STEP_06", Fixtures.TODAY.minusDays(180), 1));
        assertTrue(rule.validate(ctx(records)).isAllowed(), "恰好满 180 天应放行");
    }

    @Test
    @DisplayName("距上次考察不足 180 天，应拒绝并提示还需等待")
    void intervalNotSatisfied() {
        List<DevStepRecord> records = List.of(
                Fixtures.doneRecord("STEP_06", Fixtures.TODAY.minusDays(92), 1));
        RuleResult r = rule.validate(ctx(records));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("每 180 天"));
        assertTrue(r.message().contains("还需等待"));
        assertTrue(r.message().contains("88"), "应算出还差 88 天，实际：" + r.message());
    }

    @Test
    @DisplayName("取【最后一条】记录计算间隔，而非第一条")
    void usesLatestRecord() {
        // 首次在 400 天前（已远超 180 天），但最近一次在 10 天前
        List<DevStepRecord> records = List.of(
                Fixtures.doneRecord("STEP_06", Fixtures.TODAY.minusDays(400), 1),
                Fixtures.doneRecord("STEP_06", Fixtures.TODAY.minusDays(10), 2));
        assertTrue(rule.validate(ctx(records)).isRejected(),
                "应按最近一次考察计算间隔，否则可借首次记录规避频率限制");
    }

    @Test
    @DisplayName("推进到下一步时不校验记录频率")
    void advanceSkipsPeriodicCheck() {
        List<DevStepRecord> records = List.of(
                Fixtures.doneRecord("STEP_06", Fixtures.TODAY.minusDays(10), 1));
        DevContext ctx = Fixtures.ctx(step06(), Fixtures.passAndAdvance(),
                Fixtures.applicant(), records, Fixtures.stepMap());
        assertTrue(rule.validate(ctx).isAllowed(),
                "推进动作本身不产生考察记录，不应受频率限制");
    }

    @Test
    @DisplayName("非周期性步骤直接放行")
    void nonPeriodicStepSkips() {
        DevStep plain = Fixtures.step("STEP_07", "确定发展对象", 7);   // stepType=1
        DevContext ctx = Fixtures.ctx(plain, Fixtures.pass(), Fixtures.applicant());
        assertTrue(rule.validate(ctx).isAllowed());
    }
}
