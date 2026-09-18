package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.party.domain.dto.DisciplineQuery;
import com.hparty.party.domain.entity.DisciplineParticipant;
import com.hparty.party.domain.entity.DisciplineStudy;
import com.hparty.party.service.DisciplineStudyService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 党纪学习教育接口。
 */
@Tag(name = "25-党纪学习教育")
@RestController
@RequestMapping("/party/discipline")
@RequiredArgsConstructor
public class DisciplineStudyController {

    private final DisciplineStudyService studyService;

    @Operation(summary = "分页查询党纪学习")
    @SaCheckPermission("discipline:list")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(DisciplineQuery query) {
        return R.ok(studyService.page(query));
    }

    @Operation(summary = "党纪学习详情")
    @SaCheckPermission("discipline:list")
    @GetMapping("/{studyId}")
    public R<Map<String, Object>> detail(@PathVariable Long studyId) {
        return R.ok(studyService.detail(studyId));
    }

    @Operation(summary = "新增党纪学习")
    @SaCheckPermission("discipline:add")
    @PostMapping
    public R<Long> add(@RequestBody DisciplineStudy study) {
        return R.ok("新增成功", studyService.add(study));
    }

    @Operation(summary = "修改党纪学习")
    @SaCheckPermission("discipline:edit")
    @PutMapping
    public R<Void> update(@RequestBody DisciplineStudy study) {
        studyService.update(study);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除党纪学习")
    @SaCheckPermission("discipline:remove")
    @DeleteMapping("/{studyId}")
    public R<Void> remove(@PathVariable Long studyId) {
        studyService.remove(studyId);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "参与名单")
    @SaCheckPermission("discipline:list")
    @GetMapping("/{studyId}/participants")
    public R<List<DisciplineParticipant>> participants(@PathVariable Long studyId) {
        return R.ok(studyService.listParticipants(studyId));
    }

    @Operation(summary = "保存参与名单（全量替换）")
    @SaCheckPermission(value = {"discipline:add", "discipline:edit"}, mode = SaMode.OR)
    @PostMapping("/{studyId}/participants")
    public R<Void> saveParticipants(@PathVariable Long studyId,
                                    @RequestBody List<DisciplineParticipant> participants) {
        studyService.saveParticipants(studyId, participants);
        return R.ok("保存成功", null);
    }
}
