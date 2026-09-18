package com.hparty.develop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hparty.common.core.ResultCode;
import com.hparty.common.enums.HandleResult;
import com.hparty.common.enums.MemberStatus;
import com.hparty.common.exception.BizException;
import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.*;
import com.hparty.develop.domain.vo.DevHandleResultVO;
import com.hparty.develop.mapper.*;
import com.hparty.develop.rule.DevContext;
import com.hparty.develop.rule.DevRuleEngine;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.entity.PartyPerson;
import com.hparty.system.mapper.PartyPersonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 发展党员流程状态机 —— 整个系统的核心。
 *
 * <p>负责推进 / 驳回 / 终止 25 个步骤，并在跨越阶段边界时同步人员档案状态。
 * 规则校验委托给 {@link DevRuleEngine}，本类只处理「状态怎么流转」。</p>
 *
 * <h3>流转规则</h3>
 * <table border="1">
 *   <tr><th>办理结论</th><th>状态变化</th></tr>
 *   <tr><td>通过（单次步骤）</td><td>推进到下一步骤</td></tr>
 *   <tr><td>通过（周期性步骤，advance=false）</td><td>停留本步骤，追加一条考察记录</td></tr>
 *   <tr><td>通过（周期性步骤，advance=true）</td><td>推进到下一步骤</td></tr>
 *   <tr><td>通过（STEP_23 + 按期转正）</td><td>推进到 STEP_24</td></tr>
 *   <tr><td>通过（STEP_23 + 延长预备期）</td><td>退回 STEP_21，延长次数 +1</td></tr>
 *   <tr><td>通过（STEP_23 + 取消资格）</td><td>流程终止，人员状态回退为「群众」</td></tr>
 *   <tr><td>驳回</td><td>退回上一步骤</td></tr>
 *   <tr><td>不通过</td><td>流程终止</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevFlowService {

    private final DevApplicantMapper applicantMapper;
    private final DevStepRecordMapper recordMapper;
    private final DevMaterialMapper materialMapper;
    private final DevVoteMapper voteMapper;
    private final DevTalkMapper talkMapper;
    private final DevTrainingMapper trainingMapper;
    private final DevPoliticalReviewMapper reviewMapper;
    private final PartyPersonMapper personMapper;

    private final DevStepService stepService;
    private final DevRuleEngine ruleEngine;
    private final DevMaterialService materialService;

    // ==================== 常量 ====================

    /** 办理结论：通过 */
    public static final int RESULT_PASS = 1;
    /** 办理结论：驳回，退回上一步 */
    public static final int RESULT_REJECT = 2;
    /** 办理结论：不通过，流程终止 */
    public static final int RESULT_FAIL = 3;

    /** 流程状态：进行中 */
    public static final int STATUS_RUNNING = 1;
    /** 流程状态：已完成（转为正式党员） */
    public static final int STATUS_FINISHED = 2;
    /** 流程状态：已终止（取消资格等） */
    public static final int STATUS_TERMINATED = 3;

    /** 记录状态：已办结 */
    private static final int RECORD_DONE = 1;
    /** 记录状态：待办 */
    private static final int RECORD_TODO = 2;
    /** 记录状态：已作废 */
    private static final int RECORD_CANCELLED = 3;

    /** 周期性考察型步骤 */
    private static final int STEP_TYPE_PERIODIC = 2;

    // ==================== 对外入口 ====================

    /**
     * 办理当前步骤。
     *
     * @param dto 办理表单
     * @return 办理后的流程状态
     */
    @Transactional(rollbackFor = Exception.class)
    public DevHandleResultVO handle(DevHandleDTO dto) {
        DevApplicant applicant = loadApplicant(dto.getApplicantId());
        LoginUser operator = SecurityUtils.getLoginUser();
        DevStep step = stepService.getByCode(applicant.getCurrentStep());

        checkHandlePermission(step, applicant, operator);

        // 构建上下文并执行规则校验，不通过会抛 BizException
        DevContext ctx = buildContext(applicant, step, dto, operator);
        List<String> warnings = ruleEngine.checkOrThrow(ctx);

        // 作废本步骤原先的待办记录，并取回它上面记录的应办结时间
        LocalDateTime plannedDeadline =
                cancelPendingRecords(applicant.getApplicantId(), step.getStepCode());

        // 保存步骤专属数据（表决 / 培训 / 谈话 / 政审）
        saveStepData(ctx, dto);

        // 按结论流转
        FlowOutcome outcome = switch (dto.getResult()) {
            case RESULT_PASS -> onPass(applicant, step, dto);
            case RESULT_REJECT -> onReject(applicant, step, dto);
            case RESULT_FAIL -> onFail(applicant, step, dto);
            default -> throw new BizException("办理结论取值不合法，应为 1=通过 / 2=驳回 / 3=不通过");
        };

        // 写办理记录
        saveRecord(ctx, dto, step, outcome, plannedDeadline);

        // 同步人员档案，同时更新内存中的申请人实例
        syncPerson(applicant, step, outcome, dto);

        // 统一落库。**必须放在 syncPerson 之后** —— syncPerson 会把
        // activist_date / candidate_date / probationary_date / probation_end_date
        // 写到 applicant 对象上，若在这之前就 updateById，这些字段只停留在内存里，
        // 永远不会持久化。后果是 STEP_13 的预审规则读不到 candidateDate，
        // 判断为「尚未确定为发展对象」，流程在第 13 步永久卡死。
        applicantMapper.updateById(applicant);

        log.info("发展党员办理: applicant={} step={} result={} -> {}",
                applicant.getApplicantId(), step.getStepCode(), dto.getResult(), outcome.describe());

        return buildResult(applicant, outcome, warnings);
    }

    /**
     * 仅校验不落库，供前端在提交前预览规则提示。
     */
    public List<String> preview(DevHandleDTO dto) {
        DevApplicant applicant = loadApplicant(dto.getApplicantId());
        LoginUser operator = SecurityUtils.getLoginUser();
        DevStep step = stepService.getByCode(applicant.getCurrentStep());
        checkHandlePermission(step, applicant, operator);

        DevContext ctx = buildContext(applicant, step, dto, operator);
        List<com.hparty.develop.rule.RuleResult> results = ruleEngine.validate(ctx);

        return results.stream()
                .filter(r -> r.level() != com.hparty.develop.rule.RuleResult.Level.PASS)
                .map(r -> (r.isRejected() ? "[阻断] " : "[提醒] ") + r.message())
                .toList();
    }

    /**
     * 申请人本人提交由本人办理的步骤。
     * <p>该入口不要求通用 {@code develop:applicant:handle}，但仍执行三层校验：
     * 本人身份、当前步骤 handle_roles 包含 APPLICANT、本人必备材料已齐。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public DevHandleResultVO selfSubmit(Long applicantId) {
        DevApplicant applicant = loadApplicant(applicantId);
        LoginUser operator = SecurityUtils.getLoginUser();
        DevStep step = stepService.getByCode(applicant.getCurrentStep());

        BizException.throwForbiddenIf(operator.getPersonId() == null
                        || !operator.getPersonId().equals(applicant.getPersonId()),
                "只能提交本人的发展党员流程");
        BizException.throwForbiddenIf(!containsHandleRole(step, "APPLICANT"),
                "当前步骤不是申请人本人办理步骤");
        checkOrgAccess(applicant, operator);

        List<String> missing = materialService.missingRequiredApplicantMaterials(applicant, step.getStepCode());
        BizException.throwIf(!missing.isEmpty(), "请先上传必备材料：" + String.join("、", missing));

        DevHandleDTO dto = new DevHandleDTO();
        dto.setApplicantId(applicantId);
        dto.setResult(RESULT_PASS);
        dto.setAdvance(true);
        return handle(dto);
    }

    private boolean containsHandleRole(DevStep step, String expected) {
        if (step == null || step.getHandleRoles() == null || step.getHandleRoles().isBlank()) {
            return false;
        }
        return Arrays.stream(step.getHandleRoles().split(","))
                .map(String::trim)
                .anyMatch(expected::equalsIgnoreCase);
    }

    // ==================== 流转分支 ====================

    /** 通过 */
    private FlowOutcome onPass(DevApplicant applicant, DevStep step, DevHandleDTO dto) {
        // 分支节点：STEP_23 转正讨论的三个出口
        if (step.getIsBranch() != null && step.getIsBranch() == 1) {
            return onBranch(applicant, step, dto);
        }

        // 周期性考察步骤且未选择推进 —— 停留本步骤，追加一条考察记录
        boolean periodic = step.getStepType() != null && step.getStepType() == STEP_TYPE_PERIODIC;
        boolean advance = Boolean.TRUE.equals(dto.getAdvance());
        if (periodic && !advance) {
            // 停留：仍指向同一步骤
            applicant.setProgress(stepService.calcProgress(step.getStepOrder()));
            return new FlowOutcome(OutcomeType.STAY, step, null, "已记录本次考察，仍需继续考察。");
        }

        // 常规推进
        DevStep next = stepService.nextOf(step.getStepCode());
        if (next == null) {
            // 已是最后一步，流程走完
            applicant.setStatus(STATUS_FINISHED);
            applicant.setFinishTime(LocalDateTime.now());
            applicant.setProgress(100);
            return new FlowOutcome(OutcomeType.FINISH, step, null, "全部 25 个步骤已办结，发展党员流程完成。");
        }

        applicant.setCurrentStep(next.getStepCode());
        applicant.setCurrentStage(next.getStageCode());
        applicant.setProgress(stepService.calcProgress(next.getStepOrder()));
        applicant.setStatus(STATUS_RUNNING);

        return new FlowOutcome(OutcomeType.ADVANCE, step, next,
                String.format("已通过，流转至【%s %s】。", next.getStepCode(), next.getStepName()));
    }

    /** STEP_23 三出口 */
    private FlowOutcome onBranch(DevApplicant applicant, DevStep step, DevHandleDTO dto) {
        Integer resultType = dto.getResultType();
        if (resultType == null) {
            throw new BizException("请选择支部大会讨论结果");
        }

        return switch (resultType) {
            // 出口一：按期转为正式党员，进入 STEP_24 上级党委审批
            case 1 -> {
                DevStep next = stepService.nextOf(step.getStepCode());
                if (next == null) {
                    throw new BizException("流程配置异常：STEP_23 之后没有下一步骤");
                }
                applicant.setCurrentStep(next.getStepCode());
                applicant.setCurrentStage(next.getStageCode());
                applicant.setProgress(stepService.calcProgress(next.getStepOrder()));
                yield new FlowOutcome(OutcomeType.ADVANCE, step, next,
                        String.format("支部大会通过，按期转为正式党员，流转至【%s %s】。",
                                next.getStepCode(), next.getStepName()));
            }

            // 出口二：延长预备期，退回 STEP_21 继续教育考察
            case 2 -> {
                Integer months = dto.getExtendMonths();

                // 顺延预备期满日。这是「延长预备期」真正产生约束力的地方 ——
                // 只记次数而不动期满日的话，当事人第二天就能再次推进并申请转正。
                LocalDate currentEnd = applicant.getProbationEndDate() != null
                        ? applicant.getProbationEndDate()
                        : (applicant.getProbationaryDate() == null
                        ? LocalDate.now() : applicant.getProbationaryDate().plusYears(1));
                LocalDate newEnd = currentEnd.plusMonths(months);

                DevStep back = stepService.getByCode("STEP_21");
                applicant.setCurrentStep(back.getStepCode());
                applicant.setCurrentStage(back.getStageCode());
                applicant.setProbationEndDate(newEnd);
                applicant.setProbationExtendCount(
                        (applicant.getProbationExtendCount() == null ? 0 : applicant.getProbationExtendCount()) + 1);
                applicant.setProbationExtendMonths(
                        (applicant.getProbationExtendMonths() == null ? 0 : applicant.getProbationExtendMonths()) + months);
                applicant.setProgress(stepService.calcProgress(back.getStepOrder()));

                yield new FlowOutcome(OutcomeType.STAY, step, back, String.format(
                        "支部大会决定延长预备期 %d 个月，退回【STEP_21 继续教育考察】继续考察。"
                                + "预备期满日由 %s 顺延至 %s。", months, currentEnd, newEnd));
            }

            // 出口三：取消预备党员资格，流程终止
            case 3 -> {
                applicant.setStatus(STATUS_TERMINATED);
                applicant.setFinishTime(LocalDateTime.now());
                applicant.setTerminateReason(dto.getOpinion() == null
                        ? "支部大会决定取消预备党员资格" : dto.getOpinion());
                yield new FlowOutcome(OutcomeType.TERMINATE, step, null,
                        "支部大会决定取消预备党员资格，流程终止。");
            }

            default -> throw new BizException("支部大会讨论结果取值不合法");
        };
    }

    /** 驳回：退回上一步 */
    private FlowOutcome onReject(DevApplicant applicant, DevStep step, DevHandleDTO dto) {
        DevStep prev = stepService.prevOf(step.getStepCode());
        if (prev == null) {
            throw new BizException("已处于第一个步骤，无法退回。");
        }
        applicant.setCurrentStep(prev.getStepCode());
        applicant.setCurrentStage(prev.getStageCode());
        applicant.setProgress(stepService.calcProgress(prev.getStepOrder()));
        return new FlowOutcome(OutcomeType.ROLLBACK, step, prev,
                String.format("已驳回，退回【%s %s】重新办理。", prev.getStepCode(), prev.getStepName()));
    }

    /** 不通过：流程终止 */
    private FlowOutcome onFail(DevApplicant applicant, DevStep step, DevHandleDTO dto) {
        applicant.setStatus(STATUS_TERMINATED);
        applicant.setFinishTime(LocalDateTime.now());
        applicant.setTerminateReason(dto.getOpinion() == null ? "办理不通过，流程终止" : dto.getOpinion());
        return new FlowOutcome(OutcomeType.TERMINATE, step, null, "办理不通过，发展党员流程终止。");
    }

    // ==================== 持久化 ====================

    /** 保存步骤办理记录 */
    private void saveRecord(DevContext ctx, DevHandleDTO dto, DevStep step, FlowOutcome outcome,
                            LocalDateTime plannedDeadline) {
        DevApplicant applicant = ctx.getApplicant();
        LoginUser operator = ctx.getOperator();

        // 周期性步骤的序号：只数**已办结**的记录。
        // 若把待办也数进去，序号会恒比真实考察次数大 1。
        int seqNo = (int) ctx.countDoneRecords(step.getStepCode()) + 1;

        DevStepRecord record = new DevStepRecord();
        record.setApplicantId(applicant.getApplicantId());
        record.setPersonId(applicant.getPersonId());
        record.setOrgId(applicant.getOrgId());
        record.setStepCode(step.getStepCode());
        record.setStageCode(step.getStageCode());
        record.setSeqNo(seqNo);
        record.setResult(dto.getResult());
        record.setOpinion(dto.getOpinion());
        record.setContent(dto.getContent());
        record.setHandleUserId(operator.getUserId());
        record.setHandlePersonId(operator.getPersonId());
        record.setHandleName(operator.getNickName() == null ? operator.getUsername() : operator.getNickName());
        record.setHandleOrgId(operator.getOrgId());
        LocalDateTime now = LocalDateTime.now();
        record.setHandleTime(now);
        // 沿用建待办时算好的应办结时间，而不是写成办理时刻 ——
        // 否则真实的期限与超期事实会被抹掉，超期统计无数据可依。
        record.setDeadlineTime(plannedDeadline);
        record.setIsOverdue(plannedDeadline != null && now.isAfter(plannedDeadline) ? 1 : 0);
        record.setNextStepCode(outcome.nextStep() == null ? null : outcome.nextStep().getStepCode());
        record.setStatus(RECORD_DONE);
        recordMapper.insert(record);

        // 为接下来的步骤建待办记录，保证任何时刻都有且仅有一条"当前待办"
        switch (outcome.type()) {
            case ADVANCE -> createPendingRecord(applicant, outcome.nextStep());
            // 周期性步骤记录一次后停留在原步骤，仍需一条待办
            case STAY -> createPendingRecord(
                    applicant, outcome.nextStep() == null ? step : outcome.nextStep());
            // 驳回退回上一步，该步骤需要重新办理，同样要有待办 ——
            // 否则退回后这条工作项就从"待办视图"和催办统计里消失了
            case ROLLBACK -> createPendingRecord(applicant, outcome.nextStep());
            // FINISH 与 TERMINATE 没有后续步骤，不建待办
            default -> {
            }
        }
    }

    /** 创建下一步的待办记录 */
    private void createPendingRecord(DevApplicant applicant, DevStep nextStep) {
        DevStepRecord pending = new DevStepRecord();
        pending.setApplicantId(applicant.getApplicantId());
        pending.setPersonId(applicant.getPersonId());
        pending.setOrgId(applicant.getOrgId());
        pending.setStepCode(nextStep.getStepCode());
        pending.setStageCode(nextStep.getStageCode());
        pending.setSeqNo(1);
        pending.setStatus(RECORD_TODO);
        pending.setDeadlineTime(calcDeadline(applicant, nextStep));
        recordMapper.insert(pending);
    }

    /** 计算下一步骤的应办结时间 */
    private LocalDateTime calcDeadline(DevApplicant applicant, DevStep step) {
        // STEP_02 从递交入党申请书之日起算 1 个月
        if ("STEP_02".equals(step.getStepCode()) && applicant.getApplyDate() != null) {
            return applicant.getApplyDate().plusDays(30).atTime(23, 59, 59);
        }
        if (step.getDeadlineDays() != null && step.getDeadlineDays() > 0) {
            return LocalDateTime.now().plusDays(step.getDeadlineDays());
        }
        return null;
    }

    /**
     * 作废某步骤残留的待办记录，并返回其上记录的应办结时间。
     *
     * @return 待办记录上的 deadline_time，没有待办或未设期限时返回 null
     */
    private LocalDateTime cancelPendingRecords(Long applicantId, String stepCode) {
        LambdaQueryWrapper<DevStepRecord> pendingQuery = new LambdaQueryWrapper<DevStepRecord>()
                .eq(DevStepRecord::getApplicantId, applicantId)
                .eq(DevStepRecord::getStepCode, stepCode)
                .eq(DevStepRecord::getStatus, RECORD_TODO);

        LocalDateTime plannedDeadline = recordMapper.selectList(pendingQuery).stream()
                .map(DevStepRecord::getDeadlineTime)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        DevStepRecord update = new DevStepRecord();
        update.setStatus(RECORD_CANCELLED);
        recordMapper.update(update, pendingQuery);

        return plannedDeadline;
    }

    /**
     * 校验培养联系人 / 入党介绍人必须是正式党员。
     *
     * <p>流程图要求两者均须为「正式党员」，且入党介绍人「受留党察看处分、
     * 尚未恢复党员权利的不能担任」。这里校验前者（后者缺少处分数据，暂无法判定）。
     * 顺带拦截不存在的人员 ID —— 前端下拉被绕过时可能传入任意值。</p>
     */
    private void validateFullMembers(List<Long> personIds, String roleName) {
        if (personIds == null || personIds.isEmpty()) {
            return;
        }
        List<PartyPerson> persons = personMapper.selectBatchIds(personIds);
        if (persons.size() != personIds.size()) {
            throw new BizException(roleName + "中存在无效的人员，请重新选择。");
        }
        for (PartyPerson p : persons) {
            Integer status = p.getMemberStatus();
            // 正式党员与流动党员都属于正式党员身份
            boolean isFullMember = status != null
                    && (status == MemberStatus.FULL_MEMBER.getCode()
                    || status == MemberStatus.FLOWING.getCode());
            if (!isFullMember) {
                throw new BizException(String.format("%s必须是正式党员，%s当前为「%s」。",
                        roleName, p.getName(), MemberStatus.labelOf(status)));
            }
        }
    }

    /** 保存步骤专属数据：表决 / 培训 / 谈话 / 政审 */
    private void saveStepData(DevContext ctx, DevHandleDTO dto) {
        Long applicantId = ctx.getApplicant().getApplicantId();
        String stepCode = ctx.getStep().getStepCode();

        // 支部大会表决（STEP_15 / STEP_23）
        if (dto.getVote() != null && dto.getResult() == RESULT_PASS) {
            saveVote(applicantId, stepCode, dto);
        }

        // 集中培训（STEP_11）
        if (dto.getTraining() != null && dto.getResult() == RESULT_PASS) {
            saveTraining(applicantId, dto);
        }

        // 谈话记录（STEP_02 / STEP_16）
        if (dto.getTalk() != null && dto.getResult() == RESULT_PASS) {
            saveTalk(applicantId, stepCode, dto);
        }

        // 政治审查（STEP_10）
        if (dto.getPoliticalReview() != null && dto.getResult() == RESULT_PASS) {
            savePoliticalReview(applicantId, dto);
        }

        // 指定培养联系人（STEP_05）—— 须为 1-2 名正式党员
        if (dto.getTrainerIds() != null && !dto.getTrainerIds().isEmpty()) {
            validateFullMembers(dto.getTrainerIds(), "培养联系人");
            DevApplicant update = new DevApplicant();
            update.setApplicantId(applicantId);
            update.setTrainerIds(dto.getTrainerIds().stream()
                    .map(String::valueOf).collect(Collectors.joining(",")));
            applicantMapper.updateById(update);
            ctx.getApplicant().setTrainerIds(update.getTrainerIds());
        }

        // 确定入党介绍人（STEP_09）—— 须为 2 名正式党员
        if (dto.getIntroducerIds() != null && !dto.getIntroducerIds().isEmpty()) {
            validateFullMembers(dto.getIntroducerIds(), "入党介绍人");
            DevApplicant update = new DevApplicant();
            update.setApplicantId(applicantId);
            update.setIntroducerIds(dto.getIntroducerIds().stream()
                    .map(String::valueOf).collect(Collectors.joining(",")));
            applicantMapper.updateById(update);
            ctx.getApplicant().setIntroducerIds(update.getIntroducerIds());
        }
    }

    private void saveVote(Long applicantId, String stepCode, DevHandleDTO dto) {
        DevHandleDTO.VoteDTO v = dto.getVote();
        DevVote vote = new DevVote();
        vote.setApplicantId(applicantId);
        vote.setStepCode(stepCode);
        vote.setMeetingDate(v.getMeetingDate());
        vote.setMeetingPlace(v.getMeetingPlace());
        vote.setShouldAttend(v.getShouldAttend());
        vote.setActualAttend(v.getActualAttend());

        int quorum = com.hparty.develop.rule.impl.VoteRule.halfUp(v.getShouldAttend());
        vote.setQuorumRequired(quorum);
        vote.setIsQuorumMet(v.getActualAttend() * 2 > v.getShouldAttend() ? 1 : 0);
        vote.setAgreeCount(v.getAgreeCount());
        vote.setOpposeCount(v.getOpposeCount() == null ? 0 : v.getOpposeCount());
        vote.setAbstainCount(v.getAbstainCount() == null ? 0 : v.getAbstainCount());
        vote.setPassRequired(quorum);
        vote.setIsPassed(v.getAgreeCount() * 2 > v.getShouldAttend() ? 1 : 0);
        vote.setResultType(dto.getResultType());
        vote.setExtendMonths(dto.getExtendMonths());
        vote.setHostName(v.getHostName());
        vote.setRecorderName(v.getRecorderName());
        vote.setContent(v.getContent());
        voteMapper.insert(vote);
    }

    private void saveTraining(Long applicantId, DevHandleDTO dto) {
        DevHandleDTO.TrainingDTO t = dto.getTraining();
        DevTraining training = new DevTraining();
        training.setApplicantId(applicantId);
        training.setTrainingName(t.getTrainingName());
        training.setOrganizer(t.getOrganizer());
        training.setStartDate(t.getStartDate());
        training.setEndDate(t.getEndDate());
        training.setTrainDays(t.getTrainDays());
        training.setTrainHours(t.getTrainHours());
        training.setIsQualified(t.getIsQualified() == null ? 1 : t.getIsQualified());
        training.setRemark(t.getRemark());
        trainingMapper.insert(training);
    }

    private void saveTalk(Long applicantId, String stepCode, DevHandleDTO dto) {
        DevHandleDTO.TalkDTO t = dto.getTalk();
        DevTalk talk = new DevTalk();
        talk.setApplicantId(applicantId);
        talk.setStepCode(stepCode);
        talk.setTalkType("STEP_02".equals(stepCode) ? 1 : 2);
        talk.setTalkDate(t.getTalkDate());
        talk.setTalkPlace(t.getTalkPlace());
        talk.setTalkerName(t.getTalkerName());
        talk.setTalkerPosition(t.getTalkerPosition());
        talk.setContent(t.getContent());
        talk.setConclusion(t.getConclusion());
        talkMapper.insert(talk);
    }

    private void savePoliticalReview(Long applicantId, DevHandleDTO dto) {
        DevHandleDTO.PoliticalReviewDTO p = dto.getPoliticalReview();
        DevPoliticalReview review = new DevPoliticalReview();
        review.setApplicantId(applicantId);
        review.setReviewDate(p.getReviewDate());
        review.setAttitude(p.getAttitude());
        review.setHistory(p.getHistory());
        review.setLawAbide(p.getLawAbide());
        review.setRelatives(p.getRelatives());
        review.setMethod(p.getMethod());
        review.setConclusion(p.getConclusion());
        review.setReviewResult(p.getReviewResult() == null ? 1 : p.getReviewResult());
        reviewMapper.insert(review);
    }

    // ==================== 人员档案同步 ====================

    /**
     * 同步 {@code party_person} 的状态与关键日期。
     * <p>这一步让「人员档案」始终反映其在发展流程中的真实位置，
     * 避免流程表和人员表各说各话。</p>
     */
    private void syncPerson(DevApplicant applicant, DevStep step, FlowOutcome outcome, DevHandleDTO dto) {
        if (dto.getResult() != RESULT_PASS) {
            // 终止时回退为「群众」
            if (outcome.type() == OutcomeType.TERMINATE) {
                updatePersonStatus(applicant.getPersonId(), MemberStatus.MASS.getCode(),
                        MemberStatus.MASS.getLabel(), 0);
            }
            return;
        }

        String stepCode = step.getStepCode();
        LocalDate today = LocalDate.now();

        // 关键步骤：更新人员状态与日期字段
        switch (stepCode) {
            case "STEP_01" -> {
                // 注意：入党申请书的日期是申请人**实际递交**的日期，档案上已有记录
                // （建档时录入或本次办理表单带入）。办理日期只是党组织的接收时间，
                // 不能覆盖它 —— 否则后续所有以申请书日期为起算点的时限都会算错
                // （如 STEP_02 须在收到申请书后 1 个月内完成谈话）。
                LocalDate applyDate = applicant.getApplyDate() != null ? applicant.getApplyDate() : today;
                updateApplicantDate(applicant, "applyDate", applyDate);
                updatePersonStatus(applicant.getPersonId(), MemberStatus.APPLICANT.getCode(),
                        MemberStatus.APPLICANT.getLabel(), 0);
                updatePersonDate(applicant.getPersonId(), "applyDate", applyDate);
            }
            case "STEP_03" -> {
                updateApplicantDate(applicant, "activistDate", today);
                updatePersonStatus(applicant.getPersonId(), MemberStatus.ACTIVIST.getCode(),
                        MemberStatus.ACTIVIST.getLabel(), 0);
                updatePersonDate(applicant.getPersonId(), "activistDate", today);
            }
            case "STEP_07" -> {
                updateApplicantDate(applicant, "candidateDate", today);
                updatePersonStatus(applicant.getPersonId(), MemberStatus.CANDIDATE.getCode(),
                        MemberStatus.CANDIDATE.getLabel(), 0);
                updatePersonDate(applicant.getPersonId(), "candidateDate", today);
            }
            case "STEP_17" -> {
                updateApplicantDate(applicant, "probationaryDate", today);
                // 预备期满日 = 成为预备党员之日 + 1 年。后续 STEP_21 推进、
                // STEP_22 提出转正申请都以它为准，延长预备期时在此基础上顺延。
                applicant.setProbationEndDate(today.plusYears(1));
                updatePersonStatus(applicant.getPersonId(), MemberStatus.PROBATIONARY.getCode(),
                        "中共预备党员", 1);
                updatePersonDate(applicant.getPersonId(), "probationaryDate", today);
            }
            case "STEP_23" -> {
                // 转正讨论的三个出口中，只有「取消预备党员资格」需要立刻回收党员身份。
                // 按期转正与延长预备期都不在此处改身份：按期转正要等 STEP_24 上级党委
                // 审批通过才生效（党龄从预备期满之日算起），延长预备期则仍是预备党员。
                if (dto.getResultType() != null
                        && dto.getResultType() == com.hparty.develop.rule.impl.BranchRule.TYPE_DISQUALIFY) {
                    updatePersonStatus(applicant.getPersonId(), MemberStatus.MASS.getCode(),
                            MemberStatus.MASS.getLabel(), 0);
                }
            }
            case "STEP_24" -> {
                // 流程图：党员的党龄从预备期满转为正式党员之日算起。
                // 用 probationEndDate 而非「预备党员日期 + 1 年」，因为延长过预备期的
                // 人其期满日已经顺延。
                LocalDate probationary = applicant.getProbationaryDate();
                LocalDate fullDate = applicant.getProbationEndDate() != null
                        ? applicant.getProbationEndDate()
                        : (probationary == null ? today : probationary.plusYears(1));
                updateApplicantDate(applicant, "fullMemberDate", fullDate);
                updatePersonStatus(applicant.getPersonId(), MemberStatus.FULL_MEMBER.getCode(),
                        "中共党员", 1);
                updatePersonDate(applicant.getPersonId(), "fullMemberDate", fullDate);
            }
            default -> {
                // 其余步骤不改状态
            }
        }
    }

    /** 通过反射式 setter 更新申请人日期字段过于脆弱，这里显式分支 */
    private void updateApplicantDate(DevApplicant applicant, String field, LocalDate date) {
        switch (field) {
            case "applyDate" -> applicant.setApplyDate(date);
            case "activistDate" -> applicant.setActivistDate(date);
            case "candidateDate" -> applicant.setCandidateDate(date);
            case "probationaryDate" -> applicant.setProbationaryDate(date);
            case "fullMemberDate" -> applicant.setFullMemberDate(date);
            default -> {
            }
        }
    }

    private void updatePersonStatus(Long personId, Integer memberStatus, String politicalStatus, int isMember) {
        if (personId == null) {
            return;
        }
        PartyPerson person = new PartyPerson();
        person.setPersonId(personId);
        person.setMemberStatus(memberStatus);
        person.setPoliticalStatus(politicalStatus);
        person.setIsMember(isMember);
        personMapper.updateById(person);
    }

    private void updatePersonDate(Long personId, String field, LocalDate date) {
        if (personId == null) {
            return;
        }
        PartyPerson person = new PartyPerson();
        person.setPersonId(personId);
        switch (field) {
            case "applyDate" -> person.setApplyDate(date);
            case "activistDate" -> person.setActivistDate(date);
            case "candidateDate" -> person.setCandidateDate(date);
            case "probationaryDate" -> person.setProbationaryDate(date);
            case "fullMemberDate" -> person.setFullMemberDate(date);
            default -> {
                return;
            }
        }
        personMapper.updateById(person);
    }

    // ==================== 定时任务支持 ====================

    /**
     * 把已超期的待办记录标记出来，供定时任务调用。
     *
     * <p>{@code DevStepRecord.isOverdue} 原本只在办结时计算一次，待办记录上的该列恒为 0 ——
     * 卡片墙的「超期」是读取时用 {@code now > deadlineTime} 现算的，不依赖这列。
     * 但**统计与预警需要能按列查询**（否则每次都要全表扫描并逐行现算），
     * 因此这里把结果落库。</p>
     *
     * <p>幂等：只更新尚未标记过的行，重复执行不会产生额外影响。</p>
     *
     * @return 本次新标记为超期的记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int markOverduePendingRecords() {
        // 这里用 eq(isOverdue, 0) 而不是 ne(isOverdue, 1)。
        // is_overdue 是 NOT NULL DEFAULT 0，两者语义完全等价，但 **UPDATE 语句里
        // 不能出现 `<>`** —— 见 docs/05-开发规范.md 的「BlockAttack 拦截器」一节：
        // 带逻辑删除的表上，UPDATE 的 WHERE 一旦含 `<>` 就会被误判为全表更新而抛异常。
        // 本表目前没有 del_flag 所以侥幸可用，但换成 = 0 就没有这个隐患了。
        return recordMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DevStepRecord>()
                .set(DevStepRecord::getIsOverdue, 1)
                .eq(DevStepRecord::getStatus, RECORD_TODO)
                .eq(DevStepRecord::getIsOverdue, 0)
                .isNotNull(DevStepRecord::getDeadlineTime)
                .lt(DevStepRecord::getDeadlineTime, LocalDateTime.now()));
    }

    /**
     * 统计当前超期的待办数量，供定时任务输出预警摘要。
     */
    public long countOverduePendingRecords() {
        Long count = recordMapper.selectCount(new LambdaQueryWrapper<DevStepRecord>()
                .eq(DevStepRecord::getStatus, RECORD_TODO)
                .isNotNull(DevStepRecord::getDeadlineTime)
                .lt(DevStepRecord::getDeadlineTime, LocalDateTime.now()));
        return count == null ? 0 : count;
    }

    // ==================== 校验 ====================

    private DevApplicant loadApplicant(Long applicantId) {
        DevApplicant applicant = applicantMapper.selectById(applicantId);
        if (applicant == null) {
            throw new BizException("发展对象不存在");
        }
        if (applicant.getStatus() != null && applicant.getStatus() != STATUS_RUNNING) {
            throw new BizException("该发展对象流程已结束（"
                    + statusLabel(applicant.getStatus()) + "），无法继续办理");
        }
        return applicant;
    }

    /** 组织越权校验 */
    private void checkOrgAccess(DevApplicant applicant, LoginUser operator) {
        if (operator.isSuperAdmin()) {
            return;
        }
        if (!DataScopeHelper.canAccessData(applicant.getOrgId(), applicant.getPersonId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权办理其他党组织的发展对象");
        }
    }

    /**
     * 办理角色校验。
     *
     * <p>{@code dev_step.handle_roles} 里的标记分三类：</p>
     * <ul>
     *   <li>角色标识（如 {@code BRANCH_SECRETARY}）—— 比对当前用户的角色集合</li>
     *   <li>{@code APPLICANT} —— 必须是流程当事人本人</li>
     *   <li>组织/会议体标识（{@code BRANCH_COMMITTEE} 支委会、
     *       {@code BRANCH_ASSEMBLY} 支部大会、{@code PARENT_ORG} 上级党委等）
     *       —— 比对用户所属组织的类型与级别</li>
     * </ul>
     * <p>命中任意一个标记即视为有权限。</p>
     */
    private void checkHandlePermission(DevStep step, DevApplicant applicant, LoginUser operator) {
        checkOrgAccess(applicant, operator);

        if (operator.isSuperAdmin()) {
            return;
        }

        String roles = step.getHandleRoles();
        if (roles == null || roles.isBlank()) {
            return;
        }

        for (String raw : roles.split(",")) {
            if (matchesToken(raw.trim(), applicant, operator)) {
                return;
            }
        }

        throw new BizException(ResultCode.FORBIDDEN, String.format(
                "【%s %s】应由「%s」办理，您当前的角色无权操作。",
                step.getStepCode(), step.getStepName(), roleLabels(roles)));
    }

    /** 判断当前用户是否命中某个办理标记 */
    private boolean matchesToken(String token, DevApplicant applicant, LoginUser operator) {
        return switch (token) {
            // 当事人本人
            case "APPLICANT" -> operator.getPersonId() != null
                    && operator.getPersonId().equals(applicant.getPersonId());

            // 支部委员会 / 支部大会 —— 支部班子成员即可办理
            case "BRANCH_COMMITTEE", "BRANCH_ASSEMBLY" -> operator.isBranchLeader();

            // 上级党委 / 再上一级党委 / 县级党委组织部门 —— 需党委级组织的用户
            case "PARENT_ORG", "GRANDPARENT_ORG", "COUNTY_ORG" -> operator.isCommitteeLevel();

            // 其余按角色标识比对
            default -> operator.hasRole(token);
        };
    }

    /** 把 handle_roles 里的标记翻译成中文，用于错误提示 */
    private String roleLabels(String roles) {
        Map<String, String> labels = Map.ofEntries(
                Map.entry("SUPER_ADMIN", "超级管理员"),
                Map.entry("PARTY_SECRETARY", "党委书记"),
                Map.entry("BRANCH_SECRETARY", "支部书记"),
                Map.entry("BRANCH_DEPUTY", "支部副书记"),
                Map.entry("ORG_COMMITTEE", "组织委员"),
                Map.entry("PROP_COMMITTEE", "宣传委员"),
                Map.entry("DISC_COMMITTEE", "纪检委员"),
                Map.entry("GROUP_LEADER", "党小组长"),
                Map.entry("PARTY_MEMBER", "普通党员"),
                Map.entry("APPLICANT", "本人"),
                Map.entry("TRAINER", "培养联系人"),
                Map.entry("BRANCH_COMMITTEE", "支部委员会"),
                Map.entry("BRANCH_ASSEMBLY", "支部大会"),
                Map.entry("PARENT_ORG", "上级党委"),
                Map.entry("GRANDPARENT_ORG", "再上一级党委组织部门"),
                Map.entry("COUNTY_ORG", "县级党委组织部门"));

        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .map(t -> labels.getOrDefault(t, t))
                .collect(Collectors.joining(" 或 "));
    }

    // ==================== 上下文与结果 ====================

    private DevContext buildContext(DevApplicant applicant, DevStep step,
                                    DevHandleDTO dto, LoginUser operator) {
        List<DevStepRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<DevStepRecord>()
                        .eq(DevStepRecord::getApplicantId, applicant.getApplicantId())
                        .orderByAsc(DevStepRecord::getHandleTime));

        List<DevMaterial> materials = materialMapper.selectList(
                new LambdaQueryWrapper<DevMaterial>()
                        .eq(DevMaterial::getApplicantId, applicant.getApplicantId()));

        PartyPerson person = applicant.getPersonId() == null
                ? null : personMapper.selectById(applicant.getPersonId());

        return DevContext.builder()
                .applicant(applicant)
                .person(person)
                .step(step)
                .form(dto)
                .operator(operator)
                .records(records)
                .materials(materials)
                .stepMap(stepService.stepMap())
                .build();
    }

    private DevHandleResultVO buildResult(DevApplicant applicant, FlowOutcome outcome,
                                          List<String> warnings) {
        DevHandleResultVO vo = new DevHandleResultVO();
        vo.setApplicantId(applicant.getApplicantId());
        vo.setCurrentStage(applicant.getCurrentStage());
        vo.setCurrentStep(applicant.getCurrentStep());
        vo.setStatus(applicant.getStatus());
        vo.setStatusLabel(statusLabel(applicant.getStatus()));
        vo.setProgress(applicant.getProgress());
        vo.setResultText(outcome.message());
        vo.setFinished(outcome.type() == OutcomeType.FINISH);
        vo.setWarnings(warnings == null ? List.of() : warnings);

        DevStep current = stepService.stepMap().get(applicant.getCurrentStep());
        if (current != null) {
            vo.setCurrentStepName(current.getStepName());
        }
        stepService.listStages().stream()
                .filter(s -> s.getStageCode().equals(applicant.getCurrentStage()))
                .findFirst()
                .ifPresent(s -> vo.setCurrentStageName(s.getStageName()));

        return vo;
    }

    public static String statusLabel(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case STATUS_RUNNING -> "进行中";
            case STATUS_FINISHED -> "已完成";
            case STATUS_TERMINATED -> "已终止";
            case 4 -> "已中止";
            default -> "";
        };
    }

    /** 流转结果的内部表示 */
    private record FlowOutcome(OutcomeType type, DevStep fromStep, DevStep nextStep, String message) {
        String describe() {
            return type + (nextStep == null ? "" : " -> " + nextStep.getStepCode());
        }
    }

    private enum OutcomeType {
        /** 推进到下一步 */
        ADVANCE,
        /** 停留在本步骤（周期性考察 / 延长预备期） */
        STAY,
        /** 退回上一步 */
        ROLLBACK,
        /** 流程终止 */
        TERMINATE,
        /** 全部步骤办结 */
        FINISH
    }
}
