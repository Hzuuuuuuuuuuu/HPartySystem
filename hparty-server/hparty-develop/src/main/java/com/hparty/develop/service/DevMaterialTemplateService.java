package com.hparty.develop.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hparty.common.exception.BizException;
import com.hparty.develop.domain.entity.DevMaterialTemplate;
import com.hparty.develop.mapper.DevMaterialTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 发展党员材料模板服务。
 *
 * <p>模板数据是静态的（50 份表格），且被 25 步时间轴高频读取，
 * 因此照 {@link DevStepService} 的做法加内存缓存 + 缓存失效。</p>
 */
@Service
@RequiredArgsConstructor
public class DevMaterialTemplateService {

    /** 材料提交/出具方编码 → 中文 */
    private static final Map<String, String> SUBMIT_ROLE_LABEL = Map.of(
            "APPLICANT", "本人",
            "BRANCH", "党支部",
            "TRAINER", "培养联系人",
            "PARENT_ORG", "上级党委");

    private final DevMaterialTemplateMapper templateMapper;

    /** 模板缓存，按 order_num 升序 */
    private volatile List<DevMaterialTemplate> cache;

    // ==================== 查询 ====================

    /** 全部模板，按 order_num 升序，供管理页与材料模板页使用 */
    public List<DevMaterialTemplate> listAll() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = templateMapper.selectList(new LambdaQueryWrapper<DevMaterialTemplate>()
                            .orderByAsc(DevMaterialTemplate::getOrderNum)
                            .orderByAsc(DevMaterialTemplate::getTemplateId));
                }
            }
        }
        return cache;
    }

    /** 某个步骤的材料，按 order_num 升序 */
    public List<DevMaterialTemplate> listByStep(String stepCode) {
        if (StrUtil.isBlank(stepCode)) {
            return List.of();
        }
        return listAll().stream()
                .filter(t -> stepCode.equals(t.getStepCode()))
                .toList();
    }

    /**
     * 某个阶段的材料，含未挂步骤的阶段通用/全程通用材料。
     *
     * <p>stage_code 是 NOT NULL 列，因此「该阶段的全部材料」天然包含 step_code 为 NULL 的通用材料。</p>
     */
    public List<DevMaterialTemplate> listByStage(String stageCode) {
        if (StrUtil.isBlank(stageCode)) {
            return List.of();
        }
        return listAll().stream()
                .filter(t -> stageCode.equals(t.getStageCode()))
                .toList();
    }

    /** 阶段 + 步骤组合过滤，两个参数都可为空 */
    public List<DevMaterialTemplate> listBy(String stageCode, String stepCode) {
        return listAll().stream()
                .filter(t -> StrUtil.isBlank(stageCode) || stageCode.equals(t.getStageCode()))
                .filter(t -> StrUtil.isBlank(stepCode) || stepCode.equals(t.getStepCode()))
                .toList();
    }

    /** 按步骤编码分组，供时间轴一次性装配（避免每个步骤都查一次库） */
    public Map<String, List<DevMaterialTemplate>> groupByStep() {
        return listAll().stream()
                .filter(t -> StrUtil.isNotBlank(t.getStepCode()))
                .collect(Collectors.groupingBy(DevMaterialTemplate::getStepCode,
                        LinkedHashMap::new, Collectors.toList()));
    }

    /** 按模板ID取，不存在时抛业务异常 */
    public DevMaterialTemplate getById(Long templateId) {
        DevMaterialTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BizException("材料模板不存在");
        }
        return template;
    }

    /** 提交方编码 → 中文标签，未知编码原样返回 */
    public static String submitRoleLabel(String submitRole) {
        if (StrUtil.isBlank(submitRole)) {
            return null;
        }
        return SUBMIT_ROLE_LABEL.getOrDefault(submitRole, submitRole);
    }

    /** 清理缓存（模板配置变更后调用） */
    public void evictCache() {
        synchronized (this) {
            cache = null;
        }
    }

    /** 模板编号 → 模板，供按编号查找 */
    public Map<String, DevMaterialTemplate> codeMap() {
        return listAll().stream().collect(Collectors.toMap(
                DevMaterialTemplate::getTemplateCode, Function.identity(),
                (a, b) -> a, LinkedHashMap::new));
    }
}
