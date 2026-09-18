package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.party.domain.dto.EducationQuery;
import com.hparty.party.domain.entity.EduActivity;
import com.hparty.party.domain.entity.EduParticipant;
import com.hparty.party.service.EduActivityService;
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
 * 党员教育管理接口。
 */
@Tag(name = "24-党员教育管理")
@RestController
@RequestMapping("/party/education")
@RequiredArgsConstructor
public class EduActivityController {

    private final EduActivityService activityService;

    @Operation(summary = "分页查询教育活动")
    @SaCheckPermission("education:list")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(EducationQuery query) {
        return R.ok(activityService.page(query));
    }

    @Operation(summary = "教育统计")
    @SaCheckPermission("education:list")
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(required = false) Long orgId) {
        return R.ok(activityService.statistics(orgId));
    }

    @Operation(summary = "教育活动详情")
    @SaCheckPermission("education:list")
    @GetMapping("/{activityId}")
    public R<Map<String, Object>> detail(@PathVariable Long activityId) {
        return R.ok(activityService.detail(activityId));
    }

    @Operation(summary = "新增教育活动")
    @SaCheckPermission("education:add")
    @PostMapping
    public R<Long> add(@RequestBody EduActivity activity) {
        return R.ok("新增成功", activityService.add(activity));
    }

    @Operation(summary = "修改教育活动")
    @SaCheckPermission("education:edit")
    @PutMapping
    public R<Void> update(@RequestBody EduActivity activity) {
        activityService.update(activity);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除教育活动")
    @SaCheckPermission("education:remove")
    @DeleteMapping("/{activityId}")
    public R<Void> remove(@PathVariable Long activityId) {
        activityService.remove(activityId);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "参与名单")
    @SaCheckPermission("education:list")
    @GetMapping("/{activityId}/participants")
    public R<List<EduParticipant>> participants(@PathVariable Long activityId) {
        return R.ok(activityService.listParticipants(activityId));
    }

    @Operation(summary = "保存参与名单（全量替换）")
    @SaCheckPermission(value = {"education:add", "education:edit"}, mode = SaMode.OR)
    @PostMapping("/{activityId}/participants")
    public R<Void> saveParticipants(@PathVariable Long activityId,
                                    @RequestBody List<EduParticipant> participants) {
        activityService.saveParticipants(activityId, participants);
        return R.ok("保存成功", null);
    }
}
