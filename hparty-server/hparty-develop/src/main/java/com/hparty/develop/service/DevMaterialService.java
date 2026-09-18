package com.hparty.develop.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hparty.common.exception.BizException;
import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.domain.entity.DevMaterial;
import com.hparty.develop.domain.entity.DevMaterialTemplate;
import com.hparty.develop.domain.entity.DevStage;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.mapper.DevApplicantMapper;
import com.hparty.develop.mapper.DevMaterialMapper;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.vo.SysFileVO;
import com.hparty.system.service.SysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发展党员个人材料业务边界。
 *
 * <p>Controller 只允许客户端给出 applicantId/templateId/file，所有人员、组织、步骤、
 * 材料类型和模板编码都由服务端重新加载并派生。资源级授权以模板 submit_role 为核心，
 * 避免把通用文件上传接口当成业务授权。</p>
 */
@Service
@RequiredArgsConstructor
public class DevMaterialService {

    public static final String SUBMIT_APPLICANT = "APPLICANT";
    public static final String SUBMIT_BRANCH = "BRANCH";
    public static final String SUBMIT_PARENT_ORG = "PARENT_ORG";
    public static final String SUBMIT_TRAINER = "TRAINER";

    private final DevApplicantMapper applicantMapper;
    private final DevMaterialMapper materialMapper;
    private final DevMaterialTemplateService templateService;
    private final DevStepService stepService;
    private final SysFileService fileService;

    /**
     * 上传并归档一份个人发展材料。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long upload(Long applicantId, Long templateId, MultipartFile file) {
        DevApplicant applicant = requireApplicant(applicantId);
        DevMaterialTemplate template = requireTemplate(templateId);
        LoginUser user = SecurityUtils.getLoginUser();

        MaterialAccess access = evaluateAccess(applicant, template, user);
        BizException.throwForbiddenIf(!access.canUpload(), access.reason());

        SysFileVO uploaded = null;
        try {
            uploaded = fileService.uploadForAuthorizedOrg(file, "dev_material", applicant.getApplicantId(), applicant.getOrgId());

            DevMaterial material = new DevMaterial();
            material.setApplicantId(applicant.getApplicantId());
            material.setPersonId(applicant.getPersonId());
            material.setStepCode(template.getStepCode());
            material.setMaterialType(template.getMaterialType());
            material.setTemplateCode(template.getTemplateCode());
            material.setMaterialName(template.getTemplateName());
            material.setFileId(uploaded.getFileId());
            material.setFileUrl(uploaded.getFileUrl());
            material.setSubmitDate(LocalDate.now());
            material.setIsRequired(template.getIsRequired() == null ? 0 : template.getIsRequired());
            materialMapper.insert(material);

            if (!isRepeatable(template)) {
                List<DevMaterial> previous = materialMapper.selectList(new LambdaQueryWrapper<DevMaterial>()
                        .eq(DevMaterial::getApplicantId, applicant.getApplicantId())
                        .eq(DevMaterial::getTemplateCode, template.getTemplateCode())
                        .ne(DevMaterial::getMaterialId, material.getMaterialId()));
                for (DevMaterial old : previous) {
                    materialMapper.deleteById(old.getMaterialId());
                    if (old.getFileId() != null) {
                        fileService.deleteAfterBusinessAuthorization(old.getFileId());
                    }
                }
            }
            return material.getMaterialId();
        } catch (RuntimeException e) {
            if (uploaded != null) {
                fileService.cleanupUploadedFile(uploaded);
            }
            throw e;
        }
    }

    /**
     * 删除当前用户有权管理的一份材料。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long applicantId, Long materialId) {
        DevApplicant applicant = requireApplicant(applicantId);
        BizException.throwIf(materialId == null, "材料ID不能为空");
        DevMaterial material = materialMapper.selectById(materialId);
        BizException.throwIf(material == null || !Objects.equals(material.getApplicantId(), applicantId), "材料不存在");

        DevMaterialTemplate template = findTemplateForMaterial(material);
        LoginUser user = SecurityUtils.getLoginUser();
        MaterialAccess access = evaluateAccess(applicant, template, user);
        BizException.throwForbiddenIf(!access.canDelete(), access.reason());

        materialMapper.deleteById(materialId);
        if (material.getFileId() != null) {
            fileService.deleteAfterBusinessAuthorization(material.getFileId());
        }
    }

    /**
     * 服务端统一计算某模板对当前用户的写能力。
     */
    public MaterialAccess evaluateAccess(DevApplicant applicant,
                                         DevMaterialTemplate template,
                                         LoginUser user) {
        if (applicant == null || template == null || user == null) {
            return MaterialAccess.deny("未登录或材料上下文不存在");
        }
        if (Integer.valueOf(1).equals(template.getIsRoster())) {
            return MaterialAccess.deny("组织台账不通过个人材料入口维护");
        }
        if (!isWindowOpen(applicant, template, user)) {
            return MaterialAccess.deny(windowReason(applicant, template));
        }
        if (user.isSuperAdmin()) {
            return MaterialAccess.allow();
        }

        String submitRole = StrUtil.blankToDefault(template.getSubmitRole(), "").trim().toUpperCase(Locale.ROOT);
        boolean allowed = switch (submitRole) {
            case SUBMIT_APPLICANT -> Objects.equals(user.getPersonId(), applicant.getPersonId())
                    && DataScopeHelper.canAccessData(applicant.getOrgId(), applicant.getPersonId());
            case SUBMIT_BRANCH -> user.hasPerm("develop:material:submit")
                    && user.isBranchLeader()
                    && DataScopeHelper.canAccessData(applicant.getOrgId(), applicant.getPersonId());
            case SUBMIT_PARENT_ORG -> user.hasPerm("develop:material:submit")
                    && user.isCommitteeLevel()
                    && DataScopeHelper.canAccessData(applicant.getOrgId(), applicant.getPersonId());
            case SUBMIT_TRAINER -> user.getPersonId() != null && trainerIds(applicant).contains(user.getPersonId());
            default -> false;
        };

        if (!allowed) {
            return MaterialAccess.deny("当前用户不是该材料的合法出具人或不在数据权限范围内");
        }
        return MaterialAccess.allow();
    }

    /** 当前模板是否允许多份历史材料。周期性步骤按多份处理。 */
    public boolean isRepeatable(DevMaterialTemplate template) {
        if (template == null || StrUtil.isBlank(template.getStepCode())) {
            return false;
        }
        DevStep step = stepService.getByCode(template.getStepCode());
        return Integer.valueOf(2).equals(step.getStepType());
    }

    /**
     * 当前模板已经归档的有效材料。
     */
    public List<DevMaterial> listTemplateMaterials(Long applicantId, DevMaterialTemplate template) {
        if (applicantId == null || template == null || StrUtil.isBlank(template.getTemplateCode())) {
            return List.of();
        }
        return materialMapper.selectList(new LambdaQueryWrapper<DevMaterial>()
                .eq(DevMaterial::getApplicantId, applicantId)
                .eq(DevMaterial::getTemplateCode, template.getTemplateCode())
                .orderByDesc(DevMaterial::getMaterialId));
    }

    /**
     * 当前步骤由申请人本人出具的必备材料缺失清单。
     */
    public List<String> missingRequiredApplicantMaterials(DevApplicant applicant, String stepCode) {
        if (applicant == null || StrUtil.isBlank(stepCode)) {
            return List.of();
        }
        List<DevMaterialTemplate> required = templateService.listByStep(stepCode).stream()
                .filter(t -> SUBMIT_APPLICANT.equalsIgnoreCase(t.getSubmitRole()))
                .filter(t -> Integer.valueOf(1).equals(t.getIsRequired()))
                .filter(t -> !Integer.valueOf(1).equals(t.getIsRoster()))
                .toList();
        if (required.isEmpty()) {
            return List.of();
        }

        Set<String> uploadedCodes = materialMapper.selectList(new LambdaQueryWrapper<DevMaterial>()
                        .eq(DevMaterial::getApplicantId, applicant.getApplicantId())
                        .eq(DevMaterial::getStepCode, stepCode))
                .stream()
                .map(DevMaterial::getTemplateCode)
                .filter(StrUtil::isNotBlank)
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<String> missing = new ArrayList<>();
        for (DevMaterialTemplate template : required) {
            if (!uploadedCodes.contains(template.getTemplateCode().trim().toUpperCase(Locale.ROOT))) {
                missing.add(template.getTemplateName());
            }
        }
        return missing;
    }

    private boolean isWindowOpen(DevApplicant applicant, DevMaterialTemplate template, LoginUser user) {
        DevStep currentStep = stepService.stepMap().get(applicant.getCurrentStep());
        if (currentStep == null) {
            return false;
        }
        boolean running = Integer.valueOf(DevFlowService.STATUS_RUNNING).equals(applicant.getStatus());
        if (!running && !user.isSuperAdmin()) {
            return false;
        }

        if (StrUtil.isNotBlank(template.getStepCode())) {
            DevStep templateStep = stepService.stepMap().get(template.getStepCode());
            return templateStep != null && templateStep.getStepOrder() <= currentStep.getStepOrder();
        }

        Integer templateStageOrder = stageOrder(template.getStageCode());
        Integer currentStageOrder = stageOrder(currentStep.getStageCode());
        return templateStageOrder != null && currentStageOrder != null && templateStageOrder <= currentStageOrder;
    }

    private String windowReason(DevApplicant applicant, DevMaterialTemplate template) {
        if (applicant != null && !Integer.valueOf(DevFlowService.STATUS_RUNNING).equals(applicant.getStatus())) {
            return "流程已结束，普通用户不能继续修改材料";
        }
        if (template != null && StrUtil.isNotBlank(template.getStepCode())) {
            return "不能提前上传未来步骤材料";
        }
        return "当前阶段尚未开始，不能提前上传材料";
    }

    private Integer stageOrder(String stageCode) {
        if (StrUtil.isBlank(stageCode)) {
            return null;
        }
        return stepService.listStages().stream()
                .filter(stage -> stageCode.equals(stage.getStageCode()))
                .map(DevStage::getStageOrder)
                .findFirst()
                .orElse(null);
    }

    private DevApplicant requireApplicant(Long applicantId) {
        BizException.throwIf(applicantId == null, "申请人实例ID不能为空");
        DevApplicant applicant = applicantMapper.selectById(applicantId);
        BizException.throwIf(applicant == null, "发展对象不存在");
        return applicant;
    }

    private DevMaterialTemplate requireTemplate(Long templateId) {
        BizException.throwIf(templateId == null, "材料模板ID不能为空");
        return templateService.getById(templateId);
    }

    private DevMaterialTemplate findTemplateForMaterial(DevMaterial material) {
        BizException.throwIf(material == null || StrUtil.isBlank(material.getTemplateCode()), "材料缺少模板归属，无法执行该操作");
        return templateService.listAll().stream()
                .filter(t -> material.getTemplateCode().equalsIgnoreCase(t.getTemplateCode()))
                .findFirst()
                .orElseThrow(() -> new BizException("材料模板不存在：" + material.getTemplateCode()));
    }

    private Set<Long> trainerIds(DevApplicant applicant) {
        if (applicant == null || StrUtil.isBlank(applicant.getTrainerIds())) {
            return Set.of();
        }
        return Arrays.stream(applicant.getTrainerIds().split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(value -> {
                    try {
                        return Long.valueOf(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public record MaterialAccess(boolean canUpload, boolean canDelete, String reason) {
        public static MaterialAccess allow() {
            return new MaterialAccess(true, true, null);
        }

        public static MaterialAccess deny(String reason) {
            return new MaterialAccess(false, false, reason);
        }
    }
}
