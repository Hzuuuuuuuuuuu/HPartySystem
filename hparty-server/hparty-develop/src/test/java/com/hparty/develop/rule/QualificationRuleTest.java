package com.hparty.develop.rule;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.rule.impl.QualificationRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 资格条件规则测试。
 *
 * <p>按步骤分派：01 年龄、05 培养联系人、09 入党介绍人、13 预审前置条件。</p>
 *
 * <p><b>注意</b>：培养联系人/入党介绍人的「正式党员身份」校验在 Service 层
 * （`DevFlowService.validateFullMembers`）完成，因为它需要查库。
 * 本规则只校验数量与自荐这类无需查库的条件。</p>
 */
class QualificationRuleTest {

    private final QualificationRule rule = new QualificationRule();

    private DevContext ctx(String stepCode, int order, String stepName, DevHandleDTO form, DevApplicant applicant) {
        return Fixtures.ctx(Fixtures.step(stepCode, stepName, order), form, applicant);
    }

    // ==================== STEP_01 递交入党申请书 ====================

    @Test
    @DisplayName("STEP_01：年满 18 周岁可通过")
    void step01Adult() {
        // 申请日 2026-09-16，出生于 2000-01-01 → 26 岁
        var person = Fixtures.person(LocalDate.of(2000, 1, 1));
        var ctx = Fixtures.ctx(Fixtures.step("STEP_01", "递交入党申请书", 1),
                Fixtures.pass(), Fixtures.applicant());
        ctx.setPerson(person);
        assertTrue(rule.validate(ctx).isAllowed());
    }

    @Test
    @DisplayName("STEP_01：未满 18 周岁应拒绝")
    void step01Minor() {
        // 申请日 2026-09-16，出生于 2010-01-01 → 16 岁
        var person = Fixtures.person(LocalDate.of(2010, 1, 1));
        var ctx = Fixtures.ctx(Fixtures.step("STEP_01", "递交入党申请书", 1),
                Fixtures.pass(), Fixtures.applicant());
        ctx.setPerson(person);

        RuleResult r = rule.validate(ctx);
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("18"), "提示应说明最低年龄要求");
    }

    @Test
    @DisplayName("STEP_01：恰好满 18 周岁可通过")
    void step01ExactlyEighteen() {
        var person = Fixtures.person(Fixtures.TODAY.minusYears(18));
        var ctx = Fixtures.ctx(Fixtures.step("STEP_01", "递交入党申请书", 1),
                Fixtures.pass(), Fixtures.applicant());
        ctx.setPerson(person);
        assertTrue(rule.validate(ctx).isAllowed(), "生日当天满 18 周岁即可");
    }

    @Test
    @DisplayName("STEP_01：人员档案缺失应拒绝")
    void step01NoPerson() {
        var ctx = Fixtures.ctx(Fixtures.step("STEP_01", "递交入党申请书", 1),
                Fixtures.pass(), Fixtures.applicant());
        // person 未设置
        assertTrue(rule.validate(ctx).isRejected());
    }

    // ==================== STEP_05 指定培养联系人 ====================

    private DevHandleDTO trainers(List<Long> ids) {
        DevHandleDTO f = Fixtures.pass();
        f.setTrainerIds(ids);
        return f;
    }

    @Test
    @DisplayName("STEP_05：1-2 名培养联系人合法")
    void step05ValidCounts() {
        assertTrue(rule.validate(ctx("STEP_05", 5, "指定培养联系人", trainers(List.of(200L)), Fixtures.applicant())).isAllowed());
        assertTrue(rule.validate(ctx("STEP_05", 5, "指定培养联系人", trainers(List.of(200L, 201L)), Fixtures.applicant())).isAllowed());
    }

    @Test
    @DisplayName("STEP_05：未指定或超过 2 名应拒绝")
    void step05InvalidCounts() {
        assertTrue(rule.validate(ctx("STEP_05", 5, "指定培养联系人", trainers(List.of()), Fixtures.applicant())).isRejected());
        assertTrue(rule.validate(ctx("STEP_05", 5, "指定培养联系人", trainers(null), Fixtures.applicant())).isRejected());

        RuleResult tooMany = rule.validate(ctx("STEP_05", 5, "指定培养联系人",
                trainers(List.of(200L, 201L, 202L)), Fixtures.applicant()));
        assertTrue(tooMany.isRejected());
        assertTrue(tooMany.message().contains("最多 2 名"));
    }

    @Test
    @DisplayName("STEP_05：不能把申请人本人指定为培养联系人")
    void step05NotSelf() {
        DevApplicant a = Fixtures.applicant();     // personId = 100
        RuleResult r = rule.validate(ctx("STEP_05", 5, "指定培养联系人", trainers(List.of(100L)), a));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("本人"));
    }

    // ==================== STEP_09 确定入党介绍人 ====================

    private DevHandleDTO introducers(List<Long> ids) {
        DevHandleDTO f = Fixtures.pass();
        f.setIntroducerIds(ids);
        return f;
    }

    @Test
    @DisplayName("STEP_09：恰好 2 名不同的入党介绍人可通过")
    void step09Valid() {
        assertTrue(rule.validate(ctx("STEP_09", 9, "确定入党介绍人",
                introducers(List.of(200L, 201L)), Fixtures.applicant())).isAllowed());
    }

    @Test
    @DisplayName("STEP_09：数量不是 2 名应拒绝")
    void step09WrongCount() {
        RuleResult one = rule.validate(ctx("STEP_09", 9, "确定入党介绍人",
                introducers(List.of(200L)), Fixtures.applicant()));
        assertTrue(one.isRejected());
        assertTrue(one.message().contains("2 名"));

        assertTrue(rule.validate(ctx("STEP_09", 9, "确定入党介绍人",
                introducers(List.of(200L, 201L, 202L)), Fixtures.applicant())).isRejected());
        assertTrue(rule.validate(ctx("STEP_09", 9, "确定入党介绍人",
                introducers(null), Fixtures.applicant())).isRejected());
    }

    @Test
    @DisplayName("STEP_09：两名介绍人不能是同一人")
    void step09Duplicate() {
        RuleResult r = rule.validate(ctx("STEP_09", 9, "确定入党介绍人",
                introducers(List.of(200L, 200L)), Fixtures.applicant()));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("同一人"));
    }

    @Test
    @DisplayName("STEP_09：申请人本人不能当自己的入党介绍人")
    void step09NotSelf() {
        RuleResult r = rule.validate(ctx("STEP_09", 9, "确定入党介绍人",
                introducers(List.of(100L, 201L)), Fixtures.applicant()));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("本人"));
    }

    // ==================== STEP_13 上级党委预审 ====================

    @Test
    @DisplayName("STEP_13：尚未确定为发展对象时应拒绝")
    void step13NotYetCandidate() {
        DevApplicant a = Fixtures.applicant();
        a.setCandidateDate(null);
        RuleResult r = rule.validate(ctx("STEP_13", 13, "上级党委预审", Fixtures.pass(), a));
        assertTrue(r.isRejected());
        assertTrue(r.message().contains("发展对象"));
    }

    @Test
    @DisplayName("STEP_13：已确定为发展对象可通过")
    void step13Candidate() {
        DevApplicant a = Fixtures.applicant();
        a.setCandidateDate(LocalDate.of(2026, 7, 1));
        assertTrue(rule.validate(ctx("STEP_13", 13, "上级党委预审", Fixtures.pass(), a)).isAllowed());
    }

    // ==================== 其它步骤 ====================

    @Test
    @DisplayName("未纳入本规则的步骤直接放行")
    void otherStepsSkip() {
        assertTrue(rule.validate(ctx("STEP_10", 10, "进行政治审查", Fixtures.pass(), Fixtures.applicant())).isAllowed());
        assertTrue(rule.validate(ctx("STEP_20", 20, "入党宣誓", Fixtures.pass(), Fixtures.applicant())).isAllowed());
    }
}
