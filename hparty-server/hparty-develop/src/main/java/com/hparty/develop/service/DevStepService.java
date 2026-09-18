package com.hparty.develop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hparty.common.exception.BizException;
import com.hparty.develop.domain.entity.DevStage;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.mapper.DevStageMapper;
import com.hparty.develop.mapper.DevStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 阶段与步骤模板服务。
 *
 * <p>模板数据是静态的（5 个阶段、25 个步骤），且被流程办理高频读取。
 * 这里用内存缓存 + 写操作后失效的策略，避免每次办理都打 25 行查询。</p>
 */
@Service
@RequiredArgsConstructor
public class DevStepService {

    private final DevStageMapper stageMapper;
    private final DevStepMapper stepMapper;

    /** 步骤模板缓存，按 step_order 升序 */
    private volatile List<DevStep> stepCache;
    /** 阶段模板缓存，按 stage_order 升序 */
    private volatile List<DevStage> stageCache;

    // ==================== 阶段 ====================

    public List<DevStage> listStages() {
        if (stageCache == null) {
            synchronized (this) {
                if (stageCache == null) {
                    stageCache = stageMapper.selectList(
                            new LambdaQueryWrapper<DevStage>().orderByAsc(DevStage::getStageOrder));
                }
            }
        }
        return stageCache;
    }

    // ==================== 步骤 ====================

    /** 全部 25 个步骤，按流程顺序 */
    public List<DevStep> listSteps() {
        if (stepCache == null) {
            synchronized (this) {
                if (stepCache == null) {
                    stepCache = stepMapper.selectList(
                            new LambdaQueryWrapper<DevStep>().orderByAsc(DevStep::getStepOrder));
                }
            }
        }
        return stepCache;
    }

    /** 步骤编码 → 步骤模板 */
    public Map<String, DevStep> stepMap() {
        return listSteps().stream().collect(Collectors.toMap(
                DevStep::getStepCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    public DevStep getByCode(String stepCode) {
        DevStep step = stepMap().get(stepCode);
        if (step == null) {
            throw new BizException("步骤模板不存在：" + stepCode);
        }
        return step;
    }

    /** 下一步骤，已是最后一步则返回 null */
    public DevStep nextOf(String stepCode) {
        DevStep current = getByCode(stepCode);
        return listSteps().stream()
                .filter(s -> s.getStepOrder() > current.getStepOrder())
                .min(Comparator.comparingInt(DevStep::getStepOrder))
                .orElse(null);
    }

    /** 上一步骤，已是第一步则返回 null */
    public DevStep prevOf(String stepCode) {
        DevStep current = getByCode(stepCode);
        return listSteps().stream()
                .filter(s -> s.getStepOrder() < current.getStepOrder())
                .max(Comparator.comparingInt(DevStep::getStepOrder))
                .orElse(null);
    }

    /** 某阶段的全部步骤 */
    public List<DevStep> listByStage(String stageCode) {
        return listSteps().stream()
                .filter(s -> stageCode.equals(s.getStageCode()))
                .toList();
    }

    /** 总步骤数，用于计算进度百分比 */
    public int totalStepCount() {
        return listSteps().size();
    }

    /** 按办理顺序计算进度（1..25 → 4%..100%） */
    public int calcProgress(Integer stepOrder) {
        int total = totalStepCount();
        if (total <= 0 || stepOrder == null) {
            return 0;
        }
        return Math.min(100, (int) Math.round(stepOrder * 100.0 / total));
    }

    /** 清理缓存（步骤模板变更后调用） */
    public void evictCache() {
        synchronized (this) {
            stepCache = null;
            stageCache = null;
        }
    }
}
