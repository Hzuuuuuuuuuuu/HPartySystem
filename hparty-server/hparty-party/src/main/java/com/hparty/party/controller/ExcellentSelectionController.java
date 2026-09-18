package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.party.domain.dto.ExcellentCandidateBatchDTO;
import com.hparty.party.domain.dto.ExcellentSelectionQuery;
import com.hparty.party.domain.entity.ExcellentCandidate;
import com.hparty.party.domain.entity.ExcellentSelection;
import com.hparty.party.service.ExcellentSelectionService;
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
 * 先优评选接口。
 */
@Tag(name = "28-先优评选")
@RestController
@RequestMapping("/party/excellent")
@RequiredArgsConstructor
public class ExcellentSelectionController {

    private final ExcellentSelectionService selectionService;

    @Operation(summary = "分页查询评选活动")
    @SaCheckPermission("excellent:list")
    @GetMapping("/selection/page")
    public R<PageResult<Map<String, Object>>> selectionPage(ExcellentSelectionQuery query) {
        return R.ok(selectionService.selectionPage(query));
    }

    @Operation(summary = "评选详情（含候选人列表）")
    @SaCheckPermission("excellent:list")
    @GetMapping("/selection/{selectionId}")
    public R<Map<String, Object>> selectionDetail(@PathVariable Long selectionId) {
        return R.ok(selectionService.selectionDetail(selectionId));
    }

    @Operation(summary = "新增评选活动")
    @SaCheckPermission("excellent:add")
    @PostMapping("/selection")
    public R<Long> addSelection(@RequestBody ExcellentSelection selection) {
        return R.ok("新增成功", selectionService.addSelection(selection));
    }

    @Operation(summary = "修改评选活动")
    @SaCheckPermission("excellent:edit")
    @PutMapping("/selection")
    public R<Void> updateSelection(@RequestBody ExcellentSelection selection) {
        selectionService.updateSelection(selection);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除评选活动")
    @SaCheckPermission("excellent:remove")
    @DeleteMapping("/selection/{selectionId}")
    public R<Void> removeSelection(@PathVariable Long selectionId) {
        selectionService.removeSelection(selectionId);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "候选人列表")
    @SaCheckPermission("excellent:list")
    @GetMapping("/candidate/list")
    public R<List<Map<String, Object>>> candidateList(@RequestParam Long selectionId) {
        return R.ok(selectionService.candidateList(selectionId));
    }

    @Operation(summary = "新增/修改单个候选人")
    @SaCheckPermission(value = {"excellent:add", "excellent:edit"}, mode = SaMode.OR)
    @PostMapping("/candidate")
    public R<Long> saveCandidate(@RequestBody ExcellentCandidate candidate) {
        return R.ok("保存成功", selectionService.saveCandidate(candidate));
    }

    @Operation(summary = "批量保存候选人（全量替换）")
    @SaCheckPermission(value = {"excellent:add", "excellent:edit"}, mode = SaMode.OR)
    @PostMapping("/candidate/batch")
    public R<Void> saveCandidates(@RequestBody ExcellentCandidateBatchDTO batch) {
        selectionService.saveCandidates(batch.getSelectionId(), batch.getCandidates());
        return R.ok("保存成功", null);
    }

    @Operation(summary = "删除候选人")
    @SaCheckPermission("excellent:remove")
    @DeleteMapping("/candidate/{candidateId}")
    public R<Void> removeCandidate(@PathVariable Long candidateId) {
        selectionService.removeCandidate(candidateId);
        return R.ok("删除成功", null);
    }
}
