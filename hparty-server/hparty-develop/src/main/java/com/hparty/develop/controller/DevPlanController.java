package com.hparty.develop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.develop.domain.dto.DevPlanQuery;
import com.hparty.develop.domain.entity.DevPlan;
import com.hparty.develop.domain.entity.DevPlanQuota;
import com.hparty.develop.domain.vo.DevPlanProgressVO;
import com.hparty.develop.service.DevPlanService;
import com.hparty.framework.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 发展党员年度计划与指标接口。
 */
@Tag(name = "10-发展党员")
@RestController
@RequestMapping("/develop/plan")
@RequiredArgsConstructor
public class DevPlanController {

    private final DevPlanService planService;

    @Operation(summary = "年度计划分页")
    @SaCheckPermission("develop:plan:list")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(DevPlanQuery query) {
        return R.ok(planService.page(query));
    }

    @Operation(summary = "年度计划完成进度")
    @SaCheckPermission("develop:plan:list")
    @GetMapping("/progress")
    public R<DevPlanProgressVO> progress(@RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) Long orgId) {
        return R.ok(planService.progress(year, orgId));
    }

    @Operation(summary = "年度计划详情（含指标分解）")
    @SaCheckPermission("develop:plan:list")
    @GetMapping("/{planId}")
    public R<Map<String, Object>> detail(@PathVariable Long planId) {
        return R.ok(planService.detail(planId));
    }

    @Operation(summary = "下达年度计划")
    @SaCheckPermission("develop:plan:add")
    @OperLog(title = "发展党员年度计划", businessType = OperLog.BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody DevPlan plan) {
        return R.ok("下达成功", planService.add(plan));
    }

    @Operation(summary = "修改年度计划")
    @SaCheckPermission("develop:plan:edit")
    @OperLog(title = "发展党员年度计划", businessType = OperLog.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody DevPlan plan) {
        planService.update(plan);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除年度计划（仅草稿）")
    @SaCheckPermission("develop:plan:remove")
    @OperLog(title = "发展党员年度计划", businessType = OperLog.BusinessType.DELETE)
    @DeleteMapping("/{planId}")
    public R<Void> remove(@PathVariable Long planId) {
        planService.remove(planId);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "分配指标（全量替换）")
    @SaCheckPermission("develop:plan:edit")
    @OperLog(title = "发展党员年度计划", businessType = OperLog.BusinessType.UPDATE)
    @PostMapping("/{planId}/quota")
    public R<Void> saveQuotas(@PathVariable Long planId,
                              @RequestBody List<DevPlanQuota> quotas) {
        planService.saveQuotas(planId, quotas);
        return R.ok("指标已保存", null);
    }
}
