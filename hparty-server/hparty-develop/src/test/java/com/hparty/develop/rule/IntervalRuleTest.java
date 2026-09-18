package com.hparty.develop.rule;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.domain.entity.DevStepRecord;
import com.hparty.develop.rule.impl.IntervalRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时间间隔规则测试。
 *
 * <p>对应流程图中的培养期要求：确定发展对象须经 1 年以上培养教育和考察。</p>
 *
 * <p><b>关键语义</b>：基准日期取「基准步骤<b>首次</b>办结时间」——
 * 这是不可变的历史事实。周期性步骤（STEP_06）会留下多条记录，
 * 起算点必须取第一次，不能取最后一次，否则培养期会被无限推后。</p>
 */
class IntervalRuleTest {

    private final IntervalRule rule = new IntervalRule();

    /** STEP_03 确定为入党积极分子 → 2025-09-16 办结；今天 2026-09-16 恰好满 365 天 */
    private static final LocalDate BASE_DATE = LocalDate.of(2025, 9, 16);

    private DevStep step07() {
        return Fixtures.intervalStep("STEP_07", "确定发展对象", 7, 365, "STEP_03");
    }

    private DevStep step03() {
        return Fixtures.step("STEP_03", "推荐和确定入党积极分子", 3);
    }

    private DevContext ctx(DevStep step, DevHandleDTO form, List<DevStepRecord> records) {
        Map<String, DevStep> map = Fixtures.stepMap(step, step03());
        return Fixtures.ctx(step, form, Fixtures.applicant(), records, map);
    }

    @Test
    @DisplayName("恰好满 365 天应放行")
    void exactlyOnBoundary() {
        List<DevStepRecord> records = List.of(Fixtures.doneRecord("STEP_03", BASE_DATE));
        assertTrue(rule.validate(ctx(step07(), Fixtures.pass(), records)).isAllowed(),
                "2025-09-16 到 2026-09-16 恰好 365 天，应放行");
    }

    @Test
    @DisplayName("差 1 天应被拒绝，且提示还差多少天")
    void oneDayShort() {
        // 基准日推后一天 → 只过了 364 天
        List<DevStepRecord> records = List.of(Fixtures.doneRecord("STEP_03", BASE_DATE.plusDays(1)));
        RuleResult r = rule.validate(ctx(step07(), Fixtures.pass(), records));
        assertTrue(r.isRejected(), "364 天未满 1 年，应拒绝");
        assertTrue(r.message().contains("还需等待"), "提示应说明还需等待多久");
        assertTrue(r.message().contains("1 天"), "应提示还差 1 天，实际提示：" + r.message());
    }

    @Test
    @DisplayName("超过 365 天应放行")
    void beyondBoundary() {
        List<DevStepRecord> records = List.of(Fixtures.doneRecord("STEP_03", BASE_DATE.minusDays(1)));
        assertTrue(rule.validate(ctx(step07(), Fixtures.pass(), records)).isAllowed());
    }

    @Test
    @DisplayName("周期性步骤有多条记录时，起算点取【首次】办结时间而非最后一次")
    void usesFirstRecordForPeriodicStep() {
        // STEP_06 培养教育考察是周期性的，会留下多条记录
        DevStep step06 = Fixtures.periodicWithInterval("STEP_06", "培养教育考察", 6, 180, 365, "STEP_03");
        List<DevStepRecord> records = List.of(
                Fixtures.doneRecord("STEP_03", BASE_DATE),
                // 三次考察记录，最后一次很近
                Fixtures.doneRecord("STEP_06", BASE_DATE.plusDays(180), 1),
                Fixtures.doneRecord("STEP_06", BASE_DATE.plusDays(360), 2));

        Map<String, DevStep> map = Fixtures.stepMap(step06, step03());
        DevContext ctx = Fixtures.ctx(step06, Fixtures.passAndAdvance(), Fixtures.applicant(), records, map);

        // 距 STEP_03 首次办结正好 365 天 → 应放行
        // 若错误地取「最后一次考察时间」作基准，则只过了 5 天，会被误拒
        assertTrue(rule.validate(ctx).isAllowed(),
                "起算点必须取首次办结时间；取最后一次会导致培养期永远不满");
    }

    @Test
    @DisplayName("周期性步骤仅记录、不推进时跳过期限校验")
    void periodicRecordSkipsIntervalCheck() {
        DevStep step06 = Fixtures.periodicWithInterval("STEP_06", "培养教育考察", 6, 180, 365, "STEP_03");
        // 只过了 30 天
        List<DevStepRecord> records = List.of(Fixtures.doneRecord("STEP_03", Fixtures.TODAY.minusDays(30)));
        Map<String, DevStep> map = Fixtures.stepMap(step06, step03());

        // advance=false：只记录一次考察，不应受期限约束
        DevContext recordOnly = Fixtures.ctx(step06, Fixtures.recordOnly(), Fixtures.applicant(), records, map);
        assertTrue(rule.validate(recordOnly).isAllowed(),
                "仅记录考察时不应校验期限，否则半年一次的考察永远记不成");

        // advance=true：推进到下一步，此时才校验
        DevContext advance = Fixtures.ctx(step06, Fixtures.passAndAdvance(), Fixtures.applicant(), records, map);
        assertTrue(rule.validate(advance).isRejected(), "推进时才应校验期限");
    }

    @Test
    @DisplayName("基准步骤无记录时回退到申请人档案上的日期字段")
    void fallsBackToApplicantDate() {
        var applicant = Fixtures.applicant();
        applicant.setActivistDate(BASE_DATE);         // STEP_03 → activistDate
        Map<String, DevStep> map = Fixtures.stepMap(step07(), step03());
        // 历史数据可能没有 STEP_03 的办理记录，但档案上有确定日期
        DevContext ctx = Fixtures.ctx(step07(), Fixtures.pass(), applicant, List.of(), map);
        assertTrue(rule.validate(ctx).isAllowed());

        var tooRecent = Fixtures.applicant();
        tooRecent.setActivistDate(Fixtures.TODAY.minusDays(100));
        DevContext ctx2 = Fixtures.ctx(step07(), Fixtures.pass(), tooRecent, List.of(), map);
        assertTrue(rule.validate(ctx2).isRejected());
    }

    @Test
    @DisplayName("基准步骤既无记录也无档案日期时应拒绝，而不是放行")
    void rejectsWhenBaseUnknown() {
        Map<String, DevStep> map = Fixtures.stepMap(step07(), step03());
        DevContext ctx = Fixtures.ctx(step07(), Fixtures.pass(), Fixtures.applicant(), List.of(), map);
        // applicant.activistDate 为 null → 无法确定基准
        RuleResult r = rule.validate(ctx);
        assertTrue(r.isRejected(), "基准不明时应保守拒绝，不能默认放行");
        assertTrue(r.message().contains("尚未完成"));
    }

    @Test
    @DisplayName("未配置期限或基准步骤时直接放行")
    void noIntervalConfigured() {
        DevStep plain = Fixtures.step("STEP_19", "编入党支部和党小组", 19);
        assertTrue(rule.validate(ctx(plain, Fixtures.pass(), List.of())).isAllowed());

        DevStep noBase = Fixtures.step("STEP_X", "无基准", 26);
        noBase.setIntervalDays(365);      // 有期限但没基准步骤
        assertTrue(rule.validate(ctx(noBase, Fixtures.pass(), List.of())).isAllowed());
    }
}
