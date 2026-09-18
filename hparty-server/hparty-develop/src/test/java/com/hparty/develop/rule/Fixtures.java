package com.hparty.develop.rule;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.domain.entity.DevMaterial;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.domain.entity.DevStepRecord;
import com.hparty.system.domain.entity.PartyPerson;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则测试夹具。
 *
 * <p>规则类都是纯函数（输入 {@link DevContext}、输出 {@link RuleResult}），不查库，
 * 因此可以脱离 Spring 与数据库直接做单元测试。</p>
 *
 * <p><b>时间固定</b>：所有测试以 {@link #NOW} 为「当前时间」，
 * 通过 {@link DevContext#getNow()} 注入。这样日期推算的断言才是确定的 ——
 * 若用 {@code LocalDateTime.now()}，测试会随运行日期漂移。</p>
 */
public final class Fixtures {

    /** 测试基准时刻：2026-09-16 */
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 10, 0);
    public static final LocalDate TODAY = NOW.toLocalDate();

    private Fixtures() {
    }

    // ==================== 步骤 ====================

    public static DevStep step(String code, String name, int order) {
        DevStep s = new DevStep();
        s.setStepCode(code);
        s.setStepName(name);
        s.setStageCode("STAGE_1");
        s.setStepOrder(order);
        s.setStepType(1);          // 默认单次办理型
        s.setNeedVote(0);
        s.setIsBranch(0);
        return s;
    }

    /** 周期性考察型步骤 */
    public static DevStep periodicStep(String code, String name, int order, int periodicDays) {
        DevStep s = step(code, name, order);
        s.setStepType(2);
        s.setPeriodicDays(periodicDays);
        return s;
    }

    /** 周期 + 期限（STEP_06 培养教育考察：每半年一次、满1年方可推进） */
    public static DevStep periodicWithInterval(String code, String name, int order,
                                               int periodicDays, int intervalDays, String baseStep) {
        DevStep s = periodicStep(code, name, order, periodicDays);
        s.setIntervalDays(intervalDays);
        s.setIntervalBaseStep(baseStep);
        return s;
    }

    /** 期限型步骤（如 STEP_07 距 STEP_03 满 365 天） */
    public static DevStep intervalStep(String code, String name, int order, int intervalDays, String baseStep) {
        DevStep s = step(code, name, order);
        s.setIntervalDays(intervalDays);
        s.setIntervalBaseStep(baseStep);
        return s;
    }

    /** 时限型步骤 */
    public static DevStep deadlineStep(String code, String name, int order, int deadlineDays) {
        DevStep s = step(code, name, order);
        s.setDeadlineDays(deadlineDays);
        return s;
    }

    /** 分支节点（STEP_23） */
    public static DevStep branchStep(String code, String name, int order) {
        DevStep s = step(code, name, order);
        s.setIsBranch(1);
        return s;
    }

    // ==================== 申请人 ====================

    public static DevApplicant applicant() {
        DevApplicant a = new DevApplicant();
        a.setApplicantId(1L);
        a.setPersonId(100L);
        a.setOrgId(2L);
        a.setCurrentStage("STAGE_1");
        a.setCurrentStep("STEP_01");
        a.setStatus(1);
        a.setProbationExtendCount(0);
        a.setProbationExtendMonths(0);
        return a;
    }

    /** 已成为预备党员、预备期至 endDate 的申请人 */
    public static DevApplicant probationaryApplicant(LocalDate probationaryDate, LocalDate probationEndDate) {
        DevApplicant a = applicant();
        a.setProbationaryDate(probationaryDate);
        a.setProbationEndDate(probationEndDate);
        return a;
    }

    public static PartyPerson person(LocalDate birthDate) {
        PartyPerson p = new PartyPerson();
        p.setPersonId(100L);
        p.setName("测试人员");
        p.setBirthDate(birthDate);
        p.setMemberStatus(5);
        return p;
    }

    // ==================== 办理表单 ====================

    /** 通过 */
    public static DevHandleDTO pass() {
        DevHandleDTO f = new DevHandleDTO();
        f.setApplicantId(1L);
        f.setResult(1);
        return f;
    }

    /** 通过并推进（周期性步骤用） */
    public static DevHandleDTO passAndAdvance() {
        DevHandleDTO f = pass();
        f.setAdvance(true);
        return f;
    }

    /** 仅记录一次考察（周期性步骤用） */
    public static DevHandleDTO recordOnly() {
        DevHandleDTO f = pass();
        f.setAdvance(false);
        return f;
    }

    public static DevHandleDTO reject() {
        DevHandleDTO f = new DevHandleDTO();
        f.setApplicantId(1L);
        f.setResult(2);
        return f;
    }

    /** 支部大会表决 */
    public static DevHandleDTO vote(int shouldAttend, int actualAttend, int agree, int oppose, int abstain) {
        DevHandleDTO f = pass();
        DevHandleDTO.VoteDTO v = new DevHandleDTO.VoteDTO();
        v.setShouldAttend(shouldAttend);
        v.setActualAttend(actualAttend);
        v.setAgreeCount(agree);
        v.setOpposeCount(oppose);
        v.setAbstainCount(abstain);
        f.setVote(v);
        return f;
    }

    /** 集中培训 */
    public static DevHandleDTO training(double days, double hours) {
        DevHandleDTO f = pass();
        DevHandleDTO.TrainingDTO t = new DevHandleDTO.TrainingDTO();
        t.setTrainingName("测试培训班");
        t.setTrainDays(java.math.BigDecimal.valueOf(days));
        t.setTrainHours(java.math.BigDecimal.valueOf(hours));
        t.setIsQualified(1);
        f.setTraining(t);
        return f;
    }

    // ==================== 办理记录 ====================

    /** 某步骤于指定日期办结的记录 */
    public static DevStepRecord doneRecord(String stepCode, LocalDate handleDate) {
        DevStepRecord r = new DevStepRecord();
        r.setRecordId(1L);
        r.setApplicantId(1L);
        r.setStepCode(stepCode);
        r.setStageCode("STAGE_1");
        r.setSeqNo(1);
        r.setResult(1);          // 通过
        r.setStatus(1);          // 已办结
        r.setHandleTime(handleDate.atTime(9, 0));
        return r;
    }

    public static DevStepRecord doneRecord(String stepCode, LocalDate handleDate, int seqNo) {
        DevStepRecord r = doneRecord(stepCode, handleDate);
        r.setSeqNo(seqNo);
        return r;
    }

    // ==================== 上下文 ====================

    public static DevContext ctx(DevStep step, DevHandleDTO form, DevApplicant applicant) {
        return ctx(step, form, applicant, new ArrayList<>(), new HashMap<>());
    }

    public static DevContext ctx(DevStep step, DevHandleDTO form, DevApplicant applicant,
                                 List<DevStepRecord> records, Map<String, DevStep> stepMap) {
        return DevContext.builder()
                .step(step)
                .form(form)
                .applicant(applicant)
                .records(records)
                .materials(new ArrayList<DevMaterial>())
                .stepMap(stepMap)
                .now(NOW)
                .build();
    }

    /** 建一个 stepMap（供 DeadlineRule / IntervalRule 解析基准步骤） */
    public static Map<String, DevStep> stepMap(DevStep... steps) {
        Map<String, DevStep> map = new HashMap<>();
        for (DevStep s : steps) {
            map.put(s.getStepCode(), s);
        }
        return map;
    }
}
