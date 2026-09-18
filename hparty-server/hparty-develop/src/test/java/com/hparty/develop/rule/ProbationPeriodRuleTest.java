package com.hparty.develop.rule;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.rule.impl.ProbationPeriodRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预备期规则测试。
 *
 * <p><b>这条规则的由来</b>：早期版本 STEP_21/22 用的是 {@code INTERVAL_RULE}，
 * 基准取「入党宣誓日 + 固定 365 天」这个不可变的历史事实。
 * 结果支部大会决定延长预备期 6 个月后，当事人<b>第二天就能再次推进并申请转正</b> ——
 * 「延长预备期」这个制度设计完全失效。</p>
 *
 * <p>现在改为读 {@code dev_applicant.probation_end_date}（可顺延的目标日期），
 * 延长时按月顺延。本测试锁定这一行为。</p>
 */
class ProbationPeriodRuleTest {

    private final ProbationPeriodRule rule = new ProbationPeriodRule();

    private DevStep step21() {
        return Fixtures.periodicStep("STEP_21", "继续教育考察", 21, 90);
    }

    private DevStep step22() {
        return Fixtures.step("STEP_22", "提出转正申请", 22);
    }

    private DevContext ctx(DevStep step, DevHandleDTO form, DevApplicant applicant) {
        return Fixtures.ctx(step, form, applicant);
    }

    @Test
    @DisplayName("预备期满当日应放行")
    void exactlyOnEndDate() {
        var a = Fixtures.probationaryApplicant(LocalDate.of(2025, 9, 16), Fixtures.TODAY);
        assertTrue(rule.validate(ctx(step22(), Fixtures.pass(), a)).isAllowed(),
                "预备期满日当天视为已满");
    }

    @Test
    @DisplayName("预备期满前一日应被拒绝，并提示还需等待多久")
    void oneDayBeforeEndDate() {
        var a = Fixtures.probationaryApplicant(LocalDate.of(2025, 9, 17), Fixtures.TODAY.plusDays(1));
        RuleResult r = rule.validate(ctx(step22(), Fixtures.pass(), a));
        assertTrue(r.isRejected(), "预备期未满应拒绝");
        assertTrue(r.message().contains("预备期未满"));
        assertTrue(r.message().contains("还需等待"));
    }

    @Test
    @DisplayName("延长预备期后，原期满日已过但新期满日未到 —— 必须被拦住")
    void extensionKeepsBlocking() {
        // 原预备期 2025-08-20 起算 → 本应 2026-08-20 期满（今天 2026-09-16 已过）
        // 但支部大会决定延长 6 个月，期满日顺延为 2027-02-20
        var a = Fixtures.probationaryApplicant(
                LocalDate.of(2025, 8, 20),
                LocalDate.of(2027, 2, 20));
        a.setProbationExtendCount(1);
        a.setProbationExtendMonths(6);

        RuleResult r = rule.validate(ctx(step22(), Fixtures.pass(), a));
        assertTrue(r.isRejected(),
                "延长预备期后必须继续受约束，否则延长决定形同虚设");
        assertTrue(r.message().contains("已延长预备期"), "提示应说明已延长的次数与月数");
        assertTrue(r.message().contains("6"), "应提示累计延长 6 个月");
    }

    @Test
    @DisplayName("周期性考察步骤仅记录、不推进时不受预备期约束")
    void periodicRecordSkipsCheck() {
        // 预备期未满
        var a = Fixtures.probationaryApplicant(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1));

        // advance=false：记录一次考察，应放行
        assertTrue(rule.validate(ctx(step21(), Fixtures.recordOnly(), a)).isAllowed(),
                "仅记录考察不应被预备期拦住");

        // advance=true：推进到下一步，应拦住
        assertTrue(rule.validate(ctx(step21(), Fixtures.passAndAdvance(), a)).isRejected());
    }

    @Test
    @DisplayName("期满日缺失时回退按「预备党员日期 + 1 年」推算")
    void fallsBackToProbationaryDate() {
        var a = Fixtures.probationaryApplicant(LocalDate.of(2025, 9, 16), null);
        a.setProbationEndDate(null);
        assertTrue(rule.validate(ctx(step22(), Fixtures.pass(), a)).isAllowed(),
                "2025-09-16 + 1年 = 2026-09-16，今天恰好期满");

        var tooRecent = Fixtures.probationaryApplicant(LocalDate.of(2026, 1, 1), null);
        tooRecent.setProbationEndDate(null);
        assertTrue(rule.validate(ctx(step22(), Fixtures.pass(), tooRecent)).isRejected());
    }

    @Test
    @DisplayName("历史数据两个日期都缺失时不阻拦（避免误伤早期数据）")
    void bothDatesMissingAllows() {
        var a = Fixtures.applicant();
        a.setProbationaryDate(null);
        a.setProbationEndDate(null);
        assertTrue(rule.validate(ctx(step22(), Fixtures.pass(), a)).isAllowed());
    }
}
