package com.hparty.develop.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.enums.HandleResult;
import com.hparty.common.enums.MemberStatus;
import com.hparty.common.exception.BizException;
import com.hparty.develop.domain.dto.DevApplicantDTO;
import com.hparty.develop.domain.dto.DevApplicantQuery;
import com.hparty.develop.domain.entity.*;
import com.hparty.develop.domain.vo.DevApplicantCardVO;
import com.hparty.develop.domain.vo.DevTimelineVO;
import com.hparty.develop.mapper.DevApplicantMapper;
import com.hparty.develop.mapper.DevMaterialMapper;
import com.hparty.develop.mapper.DevStepRecordMapper;
import com.hparty.develop.rule.DevRuleEngine;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.entity.PartyPerson;
import com.hparty.system.domain.entity.SysDept;
import com.hparty.system.mapper.PartyPersonMapper;
import com.hparty.system.mapper.SysDeptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 发展党员查询服务：卡片墙、25 步时间轴、阶段统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevApplicantService {

    private final DevApplicantMapper applicantMapper;
    private final DevStepRecordMapper recordMapper;
    private final DevMaterialMapper materialMapper;
    private final PartyPersonMapper personMapper;
    private final SysDeptMapper deptMapper;
    private final DevStepService stepService;
    private final DevMaterialTemplateService templateService;
    private final DevMaterialService materialService;
    private final DevRuleEngine ruleEngine;

    // ==================== 卡片墙（图5） ====================

    /**
     * 分页查询发展党员卡片。
     */
    public PageResult<DevApplicantCardVO> pageCards(DevApplicantQuery query) {
        QueryWrapper<DevApplicant> wrapper = new QueryWrapper<>();

        // 数据权限：支部书记看本支部，党委书记看下辖
        DataScopeHelper.apply(wrapper);

        if (StrUtil.isNotBlank(query.getCurrentStage())) {
            wrapper.eq("current_stage", query.getCurrentStage());
        }
        if (StrUtil.isNotBlank(query.getCurrentStep())) {
            wrapper.eq("current_step", query.getCurrentStep());
        }
        if (query.getStatus() != null) {
            wrapper.eq("status", query.getStatus());
        }
        if (query.getOrgId() != null) {
            wrapper.eq("org_id", query.getOrgId());
        }

        // 关键字按姓名查：先从人员表捞出匹配的 personId
        if (StrUtil.isNotBlank(query.getKeyword())) {
            List<Long> personIds = searchPersonIds(query.getKeyword());
            if (personIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in("person_id", personIds);
        }

        wrapper.orderByDesc("apply_date").orderByDesc("applicant_id");

        Page<DevApplicant> page = applicantMapper.selectPage(PageUtils.toPage(query), wrapper);

        List<DevApplicantCardVO> cards = toCards(page.getRecords());
        return new PageResult<>(cards, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 按姓名或手机号找人 */
    private List<Long> searchPersonIds(String keyword) {
        return personMapper.selectList(new LambdaQueryWrapper<PartyPerson>()
                        .like(PartyPerson::getName, keyword)
                        .or()
                        .like(PartyPerson::getPhone, keyword)
                        .select(PartyPerson::getPersonId))
                .stream().map(PartyPerson::getPersonId).toList();
    }

    /** 批量把申请人实例转成卡片 VO，避免 N+1 查询 */
    private List<DevApplicantCardVO> toCards(List<DevApplicant> applicants) {
        if (applicants.isEmpty()) {
            return List.of();
        }

        Set<Long> personIds = applicants.stream()
                .map(DevApplicant::getPersonId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> orgIds = applicants.stream()
                .map(DevApplicant::getOrgId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, PartyPerson> personMap = personIds.isEmpty() ? Map.of()
                : personMapper.selectBatchIds(personIds).stream()
                .collect(Collectors.toMap(PartyPerson::getPersonId, Function.identity()));
        Map<Long, SysDept> deptMap = orgIds.isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(orgIds).stream()
                .collect(Collectors.toMap(SysDept::getOrgId, Function.identity()));

        Map<String, DevStep> stepMap = stepService.stepMap();
        Map<String, String> stageNames = stepService.listStages().stream()
                .collect(Collectors.toMap(DevStage::getStageCode, DevStage::getStageName));

        // 当前步骤的应办结时间，从待办记录里取
        Map<Long, DevStepRecord> pendingMap = loadPendingRecords(
                applicants.stream().map(DevApplicant::getApplicantId).toList());

        List<DevApplicantCardVO> list = new ArrayList<>(applicants.size());
        for (DevApplicant a : applicants) {
            DevApplicantCardVO vo = new DevApplicantCardVO();
            vo.setApplicantId(a.getApplicantId());
            vo.setPersonId(a.getPersonId());
            vo.setOrgId(a.getOrgId());
            vo.setCurrentStage(a.getCurrentStage());
            vo.setCurrentStep(a.getCurrentStep());
            vo.setStatus(a.getStatus());
            vo.setStatusLabel(DevFlowService.statusLabel(a.getStatus()));
            vo.setProgress(a.getProgress());
            vo.setApplyDate(a.getApplyDate());
            vo.setActivistDate(a.getActivistDate());
            vo.setCandidateDate(a.getCandidateDate());
            vo.setProbationaryDate(a.getProbationaryDate());

            PartyPerson person = personMap.get(a.getPersonId());
            if (person != null) {
                vo.setPersonName(person.getName());
                vo.setAvatar(person.getAvatar());
                vo.setSex(person.getSex());
                vo.setMemberStatus(person.getMemberStatus());
                vo.setMemberStatusLabel(MemberStatus.labelOf(person.getMemberStatus()));
            }

            SysDept dept = deptMap.get(a.getOrgId());
            if (dept != null) {
                vo.setOrgName(dept.getOrgName());
            }

            DevStep step = stepMap.get(a.getCurrentStep());
            if (step != null) {
                vo.setCurrentStepName(step.getStepName());
            }
            vo.setCurrentStageName(stageNames.get(a.getCurrentStage()));

            DevStepRecord pending = pendingMap.get(a.getApplicantId());
            if (pending != null && pending.getDeadlineTime() != null) {
                vo.setDeadlineTime(pending.getDeadlineTime());
                vo.setOverdue(LocalDateTime.now().isAfter(pending.getDeadlineTime()));
            } else {
                vo.setOverdue(false);
            }

            list.add(vo);
        }
        return list;
    }

    /** 批量取各申请人当前步骤的待办记录 */
    private Map<Long, DevStepRecord> loadPendingRecords(List<Long> applicantIds) {
        if (applicantIds.isEmpty()) {
            return Map.of();
        }
        List<DevStepRecord> records = recordMapper.selectList(new LambdaQueryWrapper<DevStepRecord>()
                .in(DevStepRecord::getApplicantId, applicantIds)
                .eq(DevStepRecord::getStatus, 2));
        return records.stream().collect(Collectors.toMap(
                DevStepRecord::getApplicantId, Function.identity(), (a, b) -> a));
    }

    // ==================== 25 步时间轴 ====================

    /**
     * 构建某人的 25 步时间轴详情。
     */
    public DevTimelineVO timeline(Long applicantId) {
        DevApplicant applicant = applicantMapper.selectById(applicantId);
        if (applicant == null) {
            throw new BizException("发展对象不存在");
        }
        if (!DataScopeHelper.canAccessData(applicant.getOrgId(), applicant.getPersonId())) {
            throw BizException.forbidden("无权查看其他党组织的发展对象");
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();

        DevTimelineVO vo = new DevTimelineVO();
        vo.setApplicantId(applicant.getApplicantId());
        vo.setPersonId(applicant.getPersonId());
        vo.setOrgId(applicant.getOrgId());
        vo.setCurrentStage(applicant.getCurrentStage());
        vo.setCurrentStep(applicant.getCurrentStep());
        vo.setStatus(applicant.getStatus());
        vo.setStatusLabel(DevFlowService.statusLabel(applicant.getStatus()));
        vo.setProgress(applicant.getProgress());
        vo.setApplyDate(applicant.getApplyDate());
        vo.setActivistDate(applicant.getActivistDate());
        vo.setCandidateDate(applicant.getCandidateDate());
        vo.setProbationaryDate(applicant.getProbationaryDate());
        vo.setFullMemberDate(applicant.getFullMemberDate());
        vo.setProbationEndDate(applicant.getProbationEndDate());
        vo.setProbationExtendCount(applicant.getProbationExtendCount());
        vo.setProbationExtendMonths(applicant.getProbationExtendMonths());

        // 人员与组织信息
        PartyPerson person = applicant.getPersonId() == null ? null
                : personMapper.selectById(applicant.getPersonId());
        if (person != null) {
            vo.setPersonName(person.getName());
            vo.setAvatar(person.getAvatar());
        }
        SysDept dept = applicant.getOrgId() == null ? null : deptMapper.selectById(applicant.getOrgId());
        if (dept != null) {
            vo.setOrgName(dept.getOrgName());
        }

        // 相关人员姓名
        vo.setBranchSecretaryName(nameOf(applicant.getBranchSecretaryId()));
        vo.setTrainerNames(namesOf(applicant.getTrainerIds()));
        vo.setIntroducerNames(namesOf(applicant.getIntroducerIds()));

        // 办理记录与材料
        List<DevStepRecord> records = recordMapper.selectList(new LambdaQueryWrapper<DevStepRecord>()
                .eq(DevStepRecord::getApplicantId, applicantId)
                .orderByAsc(DevStepRecord::getHandleTime));
        Map<String, List<DevStepRecord>> recordsByStep = records.stream()
                .collect(Collectors.groupingBy(DevStepRecord::getStepCode));

        List<DevMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<DevMaterial>()
                .eq(DevMaterial::getApplicantId, applicantId));
        Map<String, List<DevMaterial>> materialsByStep = materials.stream()
                .filter(m -> m.getStepCode() != null)
                .collect(Collectors.groupingBy(DevMaterial::getStepCode));
        Map<String, List<DevMaterial>> materialsByTemplateCode = materials.stream()
                .filter(m -> StrUtil.isNotBlank(m.getTemplateCode()))
                .collect(Collectors.groupingBy(
                        m -> m.getTemplateCode().trim().toUpperCase(Locale.ROOT)));

        /*
         * 已上传材料的匹配依据，分两级：
         *
         * 1) template_code —— 材料记录显式声明了自己对应哪份模板（精确）。
         * 2) material_type + step_code —— 兜底（宽松）。
         *
         * 为什么需要第 1 级：50 份模板里有 36 份的 material_type 都是 OTHER，
         * 且同一步骤上往往挂着好几份 OTHER（如 STEP_23 上有 5 份、STEP_13 上有 5 份）。
         * 若只按类型匹配，上传其中任意一份就会让同步骤的所有 OTHER 材料都显示「已上传」，
         * 「材料齐备度」这个指标就完全失去意义了。
         */
        Set<String> uploadedTemplateCodes = materials.stream()
                .map(DevMaterial::getTemplateCode)
                .filter(StrUtil::isNotBlank)
                .map(t -> t.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // 宽松键只由「未声明 template_code」的材料贡献。
        // 否则一条精确指定了 1-2-1 的材料，会通过 OTHER|STEP_02 这个宽松键
        // 把同步骤同类型的 1-2-2 也带成「已上传」—— 那就等于白改。
        Set<String> uploadedTypeStepKeys = materials.stream()
                .filter(m -> StrUtil.isBlank(m.getTemplateCode()))
                .filter(m -> StrUtil.isNotBlank(m.getMaterialType()) && StrUtil.isNotBlank(m.getStepCode()))
                .map(m -> typeStepKey(m.getMaterialType(), m.getStepCode()))
                .collect(Collectors.toSet());

        // 材料模板按步骤/阶段分组，一次取全量避免逐步查库。
        Map<String, List<DevMaterialTemplate>> templatesByStep = templateService.groupByStep();
        Map<String, List<DevMaterialTemplate>> stageTemplates = templateService.listAll().stream()
                .filter(t -> StrUtil.isBlank(t.getStepCode()))
                .filter(t -> !Integer.valueOf(1).equals(t.getIsRoster()))
                .collect(Collectors.groupingBy(DevMaterialTemplate::getStageCode,
                        LinkedHashMap::new, Collectors.toList()));

        // 按阶段组装
        List<DevStep> allSteps = stepService.listSteps();
        DevStep currentStep = stepService.stepMap().get(applicant.getCurrentStep());

        // 当前阶段与步骤的名称
        if (currentStep != null) {
            vo.setCurrentStepName(currentStep.getStepName());
        }
        stepService.listStages().stream()
                .filter(s -> s.getStageCode().equals(applicant.getCurrentStage()))
                .findFirst()
                .ifPresent(s -> vo.setCurrentStageName(s.getStageName()));

        for (DevStage stage : stepService.listStages()) {
            DevTimelineVO.StageNode stageNode = new DevTimelineVO.StageNode();
            stageNode.setStageCode(stage.getStageCode());
            stageNode.setStageName(stage.getStageName());
            stageNode.setStageOrder(stage.getStageOrder());
            stageNode.setDescription(stage.getDescription());

            List<DevStep> stageSteps = allSteps.stream()
                    .filter(s -> stage.getStageCode().equals(s.getStageCode()))
                    .toList();

            int done = 0;
            for (DevStep step : stageSteps) {
                DevTimelineVO.StepNode stepNode = buildStepNode(
                        step, applicant, currentStep, recordsByStep, materialsByStep,
                        templatesByStep, materialsByTemplateCode,
                        uploadedTemplateCodes, uploadedTypeStepKeys, loginUser);
                if ("DONE".equals(stepNode.getStatus())) {
                    done++;
                }
                stageNode.getSteps().add(stepNode);
            }
            stageNode.setDoneCount(done);
            stageNode.setTotalCount(stageSteps.size());
            stageNode.setStatus(judgeStageStatus(done, stageSteps.size(), stage.getStageCode(), applicant));

            for (DevMaterialTemplate template : stageTemplates.getOrDefault(stage.getStageCode(), List.of())) {
                stageNode.getMaterialTemplates().add(toMaterialTemplateNode(
                        template, applicant, materialsByTemplateCode,
                        uploadedTemplateCodes, uploadedTypeStepKeys, loginUser));
            }

            vo.getStages().add(stageNode);
        }

        // 材料齐备度汇总：步骤材料 + 非 roster 的个人阶段材料。
        for (DevTimelineVO.StageNode stageNode : vo.getStages()) {
            countRequiredMaterials(vo, stageNode.getMaterialTemplates());
            for (DevTimelineVO.StepNode stepNode : stageNode.getSteps()) {
                countRequiredMaterials(vo, stepNode.getMaterialTemplates());
            }
        }

        return vo;
    }

    private void countRequiredMaterials(DevTimelineVO vo,
                                        List<DevTimelineVO.MaterialTemplateNode> templates) {
        for (DevTimelineVO.MaterialTemplateNode mt : templates) {
            if (mt.getIsRequired() == null || mt.getIsRequired() != 1 || isRoster(mt.getIsRoster())) {
                continue;
            }
            vo.setRequiredMaterialCount(vo.getRequiredMaterialCount() + 1);
            if (Boolean.TRUE.equals(mt.getUploaded())) {
                vo.setUploadedMaterialCount(vo.getUploadedMaterialCount() + 1);
            }
        }
    }

    /** 是否组织台账/名册 */
    private boolean isRoster(Integer isRoster) {
        return isRoster != null && isRoster == 1;
    }

    /** 组装单个步骤节点 */
    private DevTimelineVO.StepNode buildStepNode(DevStep step, DevApplicant applicant, DevStep currentStep,
                                                 Map<String, List<DevStepRecord>> recordsByStep,
                                                 Map<String, List<DevMaterial>> materialsByStep,
                                                 Map<String, List<DevMaterialTemplate>> templatesByStep,
                                                 Map<String, List<DevMaterial>> materialsByTemplateCode,
                                                 Set<String> uploadedTemplateCodes,
                                                 Set<String> uploadedTypeStepKeys,
                                                 LoginUser loginUser) {
        DevTimelineVO.StepNode node = new DevTimelineVO.StepNode();
        node.setStepCode(step.getStepCode());
        node.setStepName(step.getStepName());
        node.setStepOrder(step.getStepOrder());
        node.setStageCode(step.getStageCode());
        node.setStepType(step.getStepType());
        node.setHandleRoles(step.getHandleRoles());
        node.setMaterialDesc(step.getMaterialDesc());
        node.setDescription(step.getDescription());
        node.setIsBranch(step.getIsBranch());
        node.setRules(ruleEngine.describeRules(step));

        // 状态判定
        node.setStatus(judgeStepStatus(step, applicant, currentStep));

        // 最近一次办理
        List<DevStepRecord> stepRecords = recordsByStep.getOrDefault(step.getStepCode(), List.of());
        stepRecords.stream()
                .filter(r -> r.getHandleTime() != null && r.getStatus() != null && r.getStatus() == 1)
                .max(Comparator.comparing(DevStepRecord::getHandleTime))
                .ifPresent(r -> {
                    node.setRecordId(r.getRecordId());
                    node.setResult(r.getResult());
                    node.setResultLabel(HandleResult.labelOf(r.getResult()));
                    node.setOpinion(r.getOpinion());
                    node.setContent(r.getContent());
                    node.setHandleName(r.getHandleName());
                    node.setHandleTime(r.getHandleTime());
                });

        // 待办记录的应办结时间
        stepRecords.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 2)
                .findFirst()
                .ifPresent(r -> {
                    node.setDeadlineTime(r.getDeadlineTime());
                    node.setOverdue(r.getDeadlineTime() != null
                            && LocalDateTime.now().isAfter(r.getDeadlineTime()));
                });

        // 周期性步骤的历史记录（每半年的考察记录）
        if (step.getStepType() != null && step.getStepType() == 2) {
            stepRecords.stream()
                    .filter(r -> r.getResult() != null && r.getStatus() != null && r.getStatus() == 1)
                    .sorted(Comparator.comparing(DevStepRecord::getHandleTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(r -> {
                        DevTimelineVO.RecordNode rn = new DevTimelineVO.RecordNode();
                        rn.setRecordId(r.getRecordId());
                        rn.setSeqNo(r.getSeqNo());
                        rn.setResult(r.getResult());
                        rn.setResultLabel(HandleResult.labelOf(r.getResult()));
                        rn.setOpinion(r.getOpinion());
                        rn.setContent(r.getContent());
                        rn.setHandleName(r.getHandleName());
                        rn.setHandleTime(r.getHandleTime());
                        node.getHistory().add(rn);
                    });
        }

        // 该步骤已归档的材料
        for (DevMaterial m : materialsByStep.getOrDefault(step.getStepCode(), List.of())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialId", m.getMaterialId());
            item.put("materialType", m.getMaterialType());
            item.put("materialName", m.getMaterialName());
            item.put("fileUrl", m.getFileUrl());
            item.put("submitDate", m.getSubmitDate());
            node.getMaterials().add(item);
        }

        // 该步骤需要的材料模板（空白模板/填写样例/填写说明/服务端能力）
        for (DevMaterialTemplate t : templatesByStep.getOrDefault(step.getStepCode(), List.of())) {
            node.getMaterialTemplates().add(toMaterialTemplateNode(
                    t, applicant, materialsByTemplateCode,
                    uploadedTemplateCodes, uploadedTypeStepKeys, loginUser));
        }

        // “本人提交”只属于当前 APPLICANT 办理步骤，不能被未来步骤或组织办理步骤误显示。
        if ("CURRENT".equals(node.getStatus())
                && containsHandleRole(step.getHandleRoles(), "APPLICANT")
                && loginUser.hasPerm("develop:applicant:self-submit")
                && Objects.equals(loginUser.getPersonId(), applicant.getPersonId())) {
            List<String> missing = materialService.missingRequiredApplicantMaterials(applicant, step.getStepCode());
            if (missing.isEmpty()) {
                node.setCanSelfSubmit(true);
            } else {
                node.setCanSelfSubmit(false);
                node.setSelfSubmitBlockedReason("请先上传必备材料：" + String.join("、", missing));
            }
        }

        return node;
    }

    /** 材料模板实体 → 时间轴上的材料节点，并附带服务端计算的操作能力。 */
    private DevTimelineVO.MaterialTemplateNode toMaterialTemplateNode(
            DevMaterialTemplate t,
            DevApplicant applicant,
            Map<String, List<DevMaterial>> materialsByTemplateCode,
            Set<String> uploadedTemplateCodes,
            Set<String> uploadedTypeStepKeys,
            LoginUser loginUser) {
        DevTimelineVO.MaterialTemplateNode mt = new DevTimelineVO.MaterialTemplateNode();
        mt.setTemplateId(t.getTemplateId());
        mt.setTemplateCode(t.getTemplateCode());
        mt.setTemplateName(t.getTemplateName());
        mt.setMaterialType(t.getMaterialType());
        mt.setIsRequired(t.getIsRequired());
        mt.setIsRoster(t.getIsRoster());
        mt.setSubmitRole(t.getSubmitRole());
        mt.setSubmitRoleLabel(DevMaterialTemplateService.submitRoleLabel(t.getSubmitRole()));
        mt.setHasBlank(StrUtil.isNotBlank(t.getBlankFile()));
        mt.setHasSample(StrUtil.isNotBlank(t.getSampleFile()));
        mt.setFillNote(t.getFillNote());
        mt.setUploaded(isMaterialUploaded(t, uploadedTemplateCodes, uploadedTypeStepKeys));
        mt.setRepeatable(materialService.isRepeatable(t));

        List<DevMaterial> templateMaterials = StrUtil.isBlank(t.getTemplateCode())
                ? List.of()
                : materialsByTemplateCode.getOrDefault(
                        t.getTemplateCode().trim().toUpperCase(Locale.ROOT), List.of());
        mt.setUploadedCount(templateMaterials.size());
        if (!templateMaterials.isEmpty()) {
            DevMaterial latest = templateMaterials.stream()
                    .max(Comparator.comparingLong(DevMaterial::getMaterialId))
                    .orElse(templateMaterials.get(0));
            mt.setMaterialId(latest.getMaterialId());
            mt.setFileUrl(latest.getFileUrl());
            mt.setCanPreview(StrUtil.isNotBlank(latest.getFileUrl()));
        } else {
            mt.setCanPreview(false);
        }

        DevMaterialService.MaterialAccess access = materialService.evaluateAccess(applicant, t, loginUser);
        mt.setCanUpload(access.canUpload());
        mt.setCanDelete(access.canDelete() && !templateMaterials.isEmpty());
        return mt;
    }

    /**
     * 判断某份模板对应的材料是否已上传。
     *
     * <p>优先按 {@code template_code} 精确匹配；材料记录未填 template_code 时，
     * 退化为按 {@code material_type + step_code} 匹配。两级都不中即视为未上传。</p>
     */
    private boolean isMaterialUploaded(DevMaterialTemplate t,
                                       Set<String> uploadedTemplateCodes,
                                       Set<String> uploadedTypeStepKeys) {
        if (StrUtil.isNotBlank(t.getTemplateCode())
                && uploadedTemplateCodes.contains(t.getTemplateCode().trim().toUpperCase(Locale.ROOT))) {
            return true;
        }
        if (StrUtil.isBlank(t.getMaterialType()) || StrUtil.isBlank(t.getStepCode())) {
            return false;
        }
        return uploadedTypeStepKeys.contains(typeStepKey(t.getMaterialType(), t.getStepCode()));
    }

    private boolean containsHandleRole(String handleRoles, String expected) {
        if (StrUtil.isBlank(handleRoles)) {
            return false;
        }
        return Arrays.stream(handleRoles.split(","))
                .map(String::trim)
                .anyMatch(expected::equalsIgnoreCase);
    }

    /** 「材料类型 + 步骤」的组合键，用于宽松匹配 */
    private static String typeStepKey(String materialType, String stepCode) {
        return materialType.trim().toUpperCase(Locale.ROOT) + "|" + stepCode.trim().toUpperCase(Locale.ROOT);
    }

    /** 步骤状态 */
    private String judgeStepStatus(DevStep step, DevApplicant applicant, DevStep currentStep) {
        if (applicant.getStatus() != null && applicant.getStatus() == DevFlowService.STATUS_TERMINATED
                && step.getStepCode().equals(applicant.getCurrentStep())) {
            return "TERMINATED";
        }
        if (currentStep == null) {
            return "PENDING";
        }
        int cmp = Integer.compare(step.getStepOrder(), currentStep.getStepOrder());
        if (cmp < 0) {
            return "DONE";
        }
        if (cmp == 0) {
            return "CURRENT";
        }
        return "PENDING";
    }

    /** 阶段状态 */
    private String judgeStageStatus(int done, int total, String stageCode, DevApplicant applicant) {
        if (done >= total) {
            return "DONE";
        }
        if (stageCode != null && stageCode.equals(applicant.getCurrentStage())) {
            return "CURRENT";
        }
        return done > 0 ? "CURRENT" : "PENDING";
    }

    // ==================== 统计 ====================

    /**
     * 阶段分布统计，用于「阶段统计」页面。
     */
    public Map<String, Object> statistics() {
        QueryWrapper<DevApplicant> wrapper = new QueryWrapper<>();
        DataScopeHelper.apply(wrapper);
        wrapper.eq("status", DevFlowService.STATUS_RUNNING);

        List<DevApplicant> running = applicantMapper.selectList(wrapper);

        Map<String, Long> byStage = running.stream()
                .filter(a -> a.getCurrentStage() != null)
                .collect(Collectors.groupingBy(DevApplicant::getCurrentStage, Collectors.counting()));

        List<Map<String, Object>> stages = new ArrayList<>();
        for (DevStage stage : stepService.listStages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stageCode", stage.getStageCode());
            item.put("stageName", stage.getStageName());
            item.put("count", byStage.getOrDefault(stage.getStageCode(), 0L));
            stages.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", running.size());
        result.put("stages", stages);
        return result;
    }

    // ==================== 增删改 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long add(DevApplicantDTO dto) {
        /*
         * 唯一性检查必须**含逻辑删除的行**。
         *
         * uk_applicant_person(person_id) 建在 person_id 单列上，被逻辑删除的行
         * 依然占用这个索引。若只用 MyBatis-Plus 的普通查询（会自动追加
         * del_flag = 0），就看不出残留行，插入时直接撞唯一键 —— 用户只看到
         * 一个无信息量的 500，且该人员永久无法再纳入发展流程。
         *
         * 因此：存在未删除的记录 → 明确报错；只存在已删除的残留 → 物理清理后重建。
         */
        List<Map<String, Object>> existing = applicantMapper.selectAllByPersonId(dto.getPersonId());
        boolean hasActive = existing.stream()
                .anyMatch(m -> toInt(m.get("delFlag")) == 0);
        if (hasActive) {
            throw new BizException("该人员已存在发展党员流程记录");
        }
        for (Map<String, Object> row : existing) {
            Long staleId = toLong(row.get("applicantId"));
            if (staleId != null) {
                applicantMapper.hardDeleteRecords(staleId);
                applicantMapper.hardDeleteById(staleId);
            }
        }

        // 确定归属组织。
        // 1) 传入的组织必须在自己数据权限内，否则可以往别的支部塞人；
        // 2) 当前账号可能没有归属组织（如超管、纯管理账号），此时 org_id 会是 null，
        //    直接插入会被数据库的 NOT NULL 拒绝，报出无信息量的 500 —— 这里提前拦下。
        Long orgId = dto.getOrgId() != null ? dto.getOrgId() : SecurityUtils.getOrgId();
        if (orgId == null) {
            throw new BizException("当前账号未分配所属党组织，无法创建发展对象。"
                    + "请先为该账号指定所属党组织，或在提交时显式指定组织。");
        }
        if (!DataScopeHelper.canAccessData(orgId, dto.getPersonId())) {
            throw BizException.forbidden("无权在指定党组织下创建发展对象");
        }

        DevApplicant applicant = new DevApplicant();
        applicant.setPersonId(dto.getPersonId());
        applicant.setOrgId(orgId);
        applicant.setCurrentStage(StrUtil.blankToDefault(dto.getCurrentStage(), "STAGE_1"));
        applicant.setCurrentStep(StrUtil.blankToDefault(dto.getCurrentStep(), "STEP_01"));
        applicant.setStatus(DevFlowService.STATUS_RUNNING);
        applicant.setProgress(stepService.calcProgress(
                stepService.getByCode(applicant.getCurrentStep()).getStepOrder()));
        applicant.setApplyDate(dto.getApplyDate());
        applicant.setBranchSecretaryId(dto.getBranchSecretaryId());
        applicant.setTrainerIds(joinIds(dto.getTrainerIds()));
        applicant.setIntroducerIds(joinIds(dto.getIntroducerIds()));
        applicant.setVolunteerBookNo(dto.getVolunteerBookNo());
        applicant.setProbationExtendCount(0);
        applicant.setRemark(dto.getRemark());
        try {
            applicantMapper.insert(applicant);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发下两个请求同时通过了上面的检查，由唯一索引兜底
            throw new BizException("该人员已存在发展党员流程记录，请刷新后重试");
        }

        // 为当前步骤建待办记录
        DevStep step = stepService.getByCode(applicant.getCurrentStep());
        DevStepRecord pending = new DevStepRecord();
        pending.setApplicantId(applicant.getApplicantId());
        pending.setPersonId(applicant.getPersonId());
        pending.setOrgId(applicant.getOrgId());
        pending.setStepCode(step.getStepCode());
        pending.setStageCode(step.getStageCode());
        pending.setSeqNo(1);
        pending.setStatus(2);
        recordMapper.insert(pending);

        return applicant.getApplicantId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(DevApplicantDTO dto) {
        if (dto.getApplicantId() == null) {
            throw new BizException("申请人实例ID不能为空");
        }
        DevApplicant applicant = applicantMapper.selectById(dto.getApplicantId());
        if (applicant == null) {
            throw new BizException("发展对象不存在");
        }
        if (!DataScopeHelper.canAccessData(applicant.getOrgId(), applicant.getPersonId())) {
            throw BizException.forbidden("无权修改其他党组织的发展对象");
        }

        DevApplicant update = new DevApplicant();
        update.setApplicantId(dto.getApplicantId());
        update.setBranchSecretaryId(dto.getBranchSecretaryId());
        update.setTrainerIds(joinIds(dto.getTrainerIds()));
        update.setIntroducerIds(joinIds(dto.getIntroducerIds()));
        update.setVolunteerBookNo(dto.getVolunteerBookNo());
        update.setApplyDate(dto.getApplyDate());
        update.setRemark(dto.getRemark());
        applicantMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long applicantId) {
        DevApplicant applicant = applicantMapper.selectById(applicantId);
        if (applicant == null) {
            throw new BizException("发展对象不存在");
        }
        if (!DataScopeHelper.canAccessData(applicant.getOrgId(), applicant.getPersonId())) {
            throw BizException.forbidden("无权删除其他党组织的发展对象");
        }
        applicantMapper.deleteById(applicantId);
        // 记录逻辑删除即可，历史轨迹保留
        recordMapper.delete(new LambdaQueryWrapper<DevStepRecord>()
                .eq(DevStepRecord::getApplicantId, applicantId)
                .eq(DevStepRecord::getStatus, 2));
    }

    // ==================== 工具 ====================

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private Long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private String nameOf(Long personId) {
        if (personId == null) {
            return null;
        }
        PartyPerson p = personMapper.selectById(personId);
        return p == null ? null : p.getName();
    }

    private String namesOf(String ids) {
        if (StrUtil.isBlank(ids)) {
            return null;
        }
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim).filter(StrUtil::isNotBlank)
                .map(Long::valueOf).toList();
        if (idList.isEmpty()) {
            return null;
        }
        return personMapper.selectBatchIds(idList).stream()
                .map(PartyPerson::getName)
                .collect(Collectors.joining("、"));
    }
}
