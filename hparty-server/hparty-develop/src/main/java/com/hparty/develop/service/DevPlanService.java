package com.hparty.develop.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.develop.domain.dto.DevPlanQuery;
import com.hparty.develop.domain.entity.DevPlan;
import com.hparty.develop.domain.entity.DevPlanQuota;
import com.hparty.develop.domain.vo.DevPlanProgressVO;
import com.hparty.develop.mapper.DevPlanMapper;
import com.hparty.develop.mapper.DevPlanQuotaMapper;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 发展党员年度计划与指标服务。
 *
 * <p><b>进度口径</b>：本年度 {@code dev_applicant} 中 {@code current_step} 已达到
 * STEP_07（确定发展对象）及之后的人数 ÷ {@code plan_count}。
 * 「已达到」通过与 {@code dev_step.step_order >= 7} 比较判定（不硬编码步骤编码）；
 * 「本年度」取 {@code candidate_date}（确定为发展对象日期）所在年份，
 * 缺失时退回 {@code apply_date}。统计范围含计划组织的整棵子树。</p>
 */
@Service
@RequiredArgsConstructor
public class DevPlanService {

    private final DevPlanMapper planMapper;
    private final DevPlanQuotaMapper quotaMapper;

    // ------------------------------------------------------------------
    // 计划
    // ------------------------------------------------------------------

    /** 分页查询年度计划。 */
    public PageResult<Map<String, Object>> page(DevPlanQuery query) {
        LambdaQueryWrapper<DevPlan> wrapper = new LambdaQueryWrapper<>();
        // 本表无 person_id 列，personColumn 传 null
        DataScopeHelper.apply(wrapper, "org_id", null);
        if (query.getPlanYear() != null) {
            wrapper.eq(DevPlan::getPlanYear, query.getPlanYear());
        }
        if (query.getOrgId() != null) {
            wrapper.eq(DevPlan::getOrgId, query.getOrgId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(DevPlan::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.like(DevPlan::getDescription, query.getKeyword().trim());
        }
        wrapper.orderByDesc(DevPlan::getPlanYear).orderByDesc(DevPlan::getPlanId);

        Page<DevPlan> page = planMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> orgNames = loadOrgNames(page.getRecords().stream()
                .map(DevPlan::getOrgId).toList());
        return PageResult.of(page, p -> toVO(p, orgNames));
    }

    /** 计划详情（含指标分解与进度）。 */
    public Map<String, Object> detail(Long planId) {
        DevPlan plan = get(planId);
        Map<String, Object> vo = toVO(plan, loadOrgNames(List.of(plan.getOrgId())));
        DevPlanProgressVO progress = progressOf(plan);
        // 只回带组织名与完成率的指标视图，避免与 progress.quotas 重复
        vo.put("quotas", progress.getQuotas());
        vo.put("progress", progress);
        return vo;
    }

    /** 按主键取计划并做越权校验。 */
    public DevPlan get(Long planId) {
        if (planId == null) {
            throw new BizException("计划ID不能为空");
        }
        DevPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("年度计划不存在");
        }
        if (!DataScopeHelper.canAccessOrg(plan.getOrgId())) {
            throw BizException.forbidden("无权查看其他党组织的年度计划");
        }
        return plan;
    }

    /** 下达计划。 */
    @Transactional(rollbackFor = Exception.class)
    public Long add(DevPlan plan) {
        if (plan.getPlanYear() == null) {
            throw new BizException("请填写计划年度");
        }
        BizException.throwIf(plan.getPlanCount() == null || plan.getPlanCount() < 0,
                "请填写计划发展党员数");

        // 组织 ID 不无条件信任客户端
        Long orgId = plan.getOrgId() != null ? plan.getOrgId() : SecurityUtils.getOrgId();
        if (orgId == null) {
            throw new BizException("当前账号未分配所属党组织，无法创建。");
        }
        if (!DataScopeHelper.canAccessOrg(orgId)) {
            throw BizException.forbidden("无权在指定党组织下创建年度计划");
        }

        Long exists = planMapper.selectCount(new LambdaQueryWrapper<DevPlan>()
                .eq(DevPlan::getOrgId, orgId)
                .eq(DevPlan::getPlanYear, plan.getPlanYear()));
        if (exists != null && exists > 0) {
            throw new BizException(plan.getPlanYear() + " 年度该党组织的计划已存在");
        }

        plan.setPlanId(null);
        plan.setOrgId(orgId);
        if (plan.getStatus() == null) {
            plan.setStatus(1);
        }
        if (plan.getIssueDate() == null) {
            plan.setIssueDate(LocalDate.now());
        }
        if (plan.getIssueOrgId() == null) {
            // 下达组织默认取当前登录用户所属组织（上级党委）
            plan.setIssueOrgId(SecurityUtils.getOrgId());
        }
        try {
            planMapper.insert(plan);
        } catch (DuplicateKeyException e) {
            throw new BizException(plan.getPlanYear() + " 年度该党组织的计划已存在");
        }
        return plan.getPlanId();
    }

    /** 修改计划。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(DevPlan plan) {
        if (plan.getPlanId() == null) {
            throw new BizException("计划ID不能为空");
        }
        DevPlan exists = get(plan.getPlanId());
        // 组织与年度是唯一键的一部分，不允许改
        plan.setOrgId(exists.getOrgId());
        plan.setPlanYear(exists.getPlanYear());
        planMapper.updateById(plan);
    }

    /** 删除计划（仅草稿）。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long planId) {
        DevPlan plan = get(planId);
        if (!Objects.equals(plan.getStatus(), 0)) {
            throw new BizException("只有草稿状态的计划才能删除");
        }
        planMapper.deleteById(planId);
        quotaMapper.delete(new LambdaQueryWrapper<DevPlanQuota>()
                .eq(DevPlanQuota::getPlanId, planId));
    }

    // ------------------------------------------------------------------
    // 指标分解
    // ------------------------------------------------------------------

    /** 某计划的指标分解列表。 */
    public List<DevPlanQuota> listQuotas(Long planId) {
        return quotaMapper.selectList(new LambdaQueryWrapper<DevPlanQuota>()
                .eq(DevPlanQuota::getPlanId, planId)
                .orderByAsc(DevPlanQuota::getQuotaId));
    }

    /**
     * 分配指标（**全量替换**：先删旧指标再插新指标，与参与类子表一致）。
     *
     * <p>各下级组织的名额之和不得超过计划总数，否则上级下达的数字就对不上账了。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveQuotas(Long planId, List<DevPlanQuota> quotas) {
        DevPlan plan = get(planId);
        String planPath = planMapper.selectOrgPath(plan.getOrgId());

        int sum = 0;
        Set<Long> orgIds = new HashSet<>();
        if (quotas != null) {
            for (DevPlanQuota quota : quotas) {
                if (quota.getOrgId() == null) {
                    throw new BizException("请选择被分配的组织");
                }
                if (quota.getQuotaCount() == null || quota.getQuotaCount() < 0) {
                    throw new BizException("分配名额不能为空且不能为负数");
                }
                if (quota.getOrgId().equals(plan.getOrgId())) {
                    throw new BizException("指标应分解给下级组织，不能分配给计划所属组织本身");
                }
                if (!orgIds.add(quota.getOrgId())) {
                    throw new BizException("同一组织在一次分配中只能出现一次");
                }
                // 只能分解给本单位的下级组织（物化路径前缀判定，与数据权限同一套口径）
                String quotaPath = planMapper.selectOrgPath(quota.getOrgId());
                if (planPath == null || quotaPath == null || !quotaPath.startsWith(planPath)) {
                    throw new BizException("指标只能分解给本单位的下级组织");
                }
                if (!DataScopeHelper.canAccessOrg(quota.getOrgId())) {
                    throw BizException.forbidden("无权向指定组织分配指标");
                }
                sum += quota.getQuotaCount();
            }
        }
        int planCount = plan.getPlanCount() == null ? 0 : plan.getPlanCount();
        if (sum > planCount) {
            throw new BizException("指标合计 " + sum + " 名，超过计划总数 " + planCount + " 名");
        }

        quotaMapper.delete(new LambdaQueryWrapper<DevPlanQuota>()
                .eq(DevPlanQuota::getPlanId, planId));
        if (quotas != null) {
            for (DevPlanQuota quota : quotas) {
                quota.setQuotaId(null);
                quota.setPlanId(planId);
                quotaMapper.insert(quota);
            }
        }
    }

    // ------------------------------------------------------------------
    // 进度
    // ------------------------------------------------------------------

    /**
     * 计划完成进度。
     *
     * @param year  计划年度；为 null 时取当前自然年
     * @param orgId 指定组织；为 null 时取当前用户所属组织
     * @return 进度；该组织该年度没有计划时返回 reachedCount/planCount 为空壳（不报错，
     *         便于「发展阶段统计」页面在无计划时静默展示）
     */
    public DevPlanProgressVO progress(Integer year, Long orgId) {
        int planYear = year == null ? LocalDate.now().getYear() : year;
        Long targetOrgId = orgId != null ? orgId : SecurityUtils.getOrgId();
        if (targetOrgId == null) {
            // 超管等未分配所属组织的账号：退到根组织（党委）。
            // 党委的计划通过指标分解覆盖了下辖各支部，正是「整体进度」的自然视角；
            // 若不兜底，最需要看全局的账号反而一条进度都看不到。
            targetOrgId = planMapper.selectRootOrgId();
            if (targetOrgId == null) {
                throw new BizException("系统中还没有党组织，无法查询计划进度");
            }
        }
        if (!DataScopeHelper.canAccessOrg(targetOrgId)) {
            throw BizException.forbidden("无权查看该组织的年度计划");
        }

        DevPlanProgressVO vo = new DevPlanProgressVO();
        vo.setOrgId(targetOrgId);
        vo.setPlanYear(planYear);
        vo.setOrgName(planMapper.selectOrgName(targetOrgId));

        DevPlan plan = planMapper.selectOne(new LambdaQueryWrapper<DevPlan>()
                .eq(DevPlan::getOrgId, targetOrgId)
                .eq(DevPlan::getPlanYear, planYear)
                .last("LIMIT 1"));
        if (plan == null) {
            vo.setPlanCount(0);
            vo.setReachedCount(0L);
            vo.setRate(BigDecimal.ZERO);
            vo.setQuotas(List.of());
            return vo;
        }
        DevPlanProgressVO full = progressOf(plan);
        full.setOrgName(vo.getOrgName());
        return full;
    }

    /** 单条计划的进度。 */
    private DevPlanProgressVO progressOf(DevPlan plan) {
        String orgPath = planMapper.selectOrgPath(plan.getOrgId());
        DevPlanProgressVO vo = new DevPlanProgressVO();
        vo.setPlanId(plan.getPlanId());
        vo.setOrgId(plan.getOrgId());
        vo.setOrgName(planMapper.selectOrgName(plan.getOrgId()));
        vo.setPlanYear(plan.getPlanYear());
        vo.setPlanCount(plan.getPlanCount());
        vo.setActivistTarget(plan.getActivistTarget());
        vo.setStatus(plan.getStatus());

        if (orgPath == null) {
            vo.setReachedCount(0L);
            vo.setRate(BigDecimal.ZERO);
            vo.setQuotas(List.of());
            return vo;
        }

        long reached = planMapper.countReachedStep07(orgPath, plan.getPlanYear());
        vo.setReachedCount(reached);
        vo.setRate(rate(reached, plan.getPlanCount()));

        // 指标分解：逐组织显示名额与实际达标人数
        List<DevPlanQuota> quotas = listQuotas(plan.getPlanId());
        Map<Long, Long> reachedByOrg = new LinkedHashMap<>();
        for (Map<String, Object> row : planMapper.countReachedStep07ByOrg(orgPath, plan.getPlanYear())) {
            Object orgIdValue = row.get("org_id");
            Object cntValue = row.get("cnt");
            if (orgIdValue instanceof Number id && cntValue instanceof Number cnt) {
                reachedByOrg.put(id.longValue(), cnt.longValue());
            }
        }
        List<DevPlanProgressVO.QuotaProgress> items = new ArrayList<>();
        for (DevPlanQuota quota : quotas) {
            DevPlanProgressVO.QuotaProgress qp = new DevPlanProgressVO.QuotaProgress();
            qp.setQuotaId(quota.getQuotaId());
            qp.setOrgId(quota.getOrgId());
            qp.setOrgName(planMapper.selectOrgName(quota.getOrgId()));
            qp.setQuotaCount(quota.getQuotaCount());
            qp.setRemark(quota.getRemark());
            long orgReached = reachedByOrg.getOrDefault(quota.getOrgId(), 0L);
            qp.setReachedCount(orgReached);
            qp.setRate(rate(orgReached, quota.getQuotaCount()));
            items.add(qp);
        }
        vo.setQuotas(items);
        return vo;
    }

    /** 完成率（%，保留 1 位小数）；计划数为 0 时返回 0。 */
    private BigDecimal rate(long reached, Integer planCount) {
        if (planCount == null || planCount <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(reached).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(planCount), 1, RoundingMode.HALF_UP);
    }

    private Map<Long, String> loadOrgNames(List<Long> orgIds) {
        List<Long> ids = orgIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new LinkedHashMap<>();
        for (Map<String, Object> row : planMapper.selectOrgNames(ids)) {
            Object id = row.get("org_id");
            Object name = row.get("org_name");
            if (id instanceof Number num) {
                map.put(num.longValue(), name == null ? null : String.valueOf(name));
            }
        }
        return map;
    }

    private Map<String, Object> toVO(DevPlan p, Map<Long, String> orgNames) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("planId", p.getPlanId());
        item.put("orgId", p.getOrgId());
        item.put("orgName", orgNames.get(p.getOrgId()));
        item.put("planYear", p.getPlanYear());
        item.put("planCount", p.getPlanCount());
        item.put("activistTarget", p.getActivistTarget());
        item.put("status", p.getStatus());
        item.put("statusLabel", statusLabel(p.getStatus()));
        item.put("issueOrgId", p.getIssueOrgId());
        item.put("issueDate", p.getIssueDate());
        item.put("description", p.getDescription());
        return item;
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "已下达";
            case 2 -> "执行中";
            case 3 -> "已完成";
            default -> String.valueOf(status);
        };
    }
}
