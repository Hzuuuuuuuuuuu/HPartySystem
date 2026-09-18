package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.party.domain.dto.ElectionQuery;
import com.hparty.party.domain.entity.OrgElection;
import com.hparty.party.domain.entity.OrgElectionCandidate;
import com.hparty.party.service.OrgElectionService;
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
 * 党组织换届接口。
 * <p>列表与详情做数据权限裁剪，候选人名单整表保存。</p>
 */
@Tag(name = "23-党组织换届")
@RestController
@RequestMapping("/party/election")
@RequiredArgsConstructor
public class OrgElectionController {

    private final OrgElectionService electionService;

    @Operation(summary = "分页查询换届记录")
    @SaCheckPermission("election:list")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(ElectionQuery query) {
        return R.ok(electionService.page(query));
    }

    @Operation(summary = "换届详情（含候选人列表）")
    @SaCheckPermission("election:list")
    @GetMapping("/{electionId}")
    public R<Map<String, Object>> detail(@PathVariable Long electionId) {
        return R.ok(electionService.detail(electionId));
    }

    @Operation(summary = "新增换届")
    @SaCheckPermission("election:add")
    @PostMapping
    public R<Long> add(@RequestBody OrgElection election) {
        return R.ok("新增成功", electionService.add(election));
    }

    @Operation(summary = "修改换届")
    @SaCheckPermission("election:edit")
    @PutMapping
    public R<Void> update(@RequestBody OrgElection election) {
        electionService.update(election);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除换届")
    @SaCheckPermission("election:remove")
    @DeleteMapping("/{electionId}")
    public R<Void> remove(@PathVariable Long electionId) {
        electionService.remove(electionId);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "候选人列表")
    @SaCheckPermission("election:list")
    @GetMapping("/{electionId}/candidates")
    public R<List<OrgElectionCandidate>> candidates(@PathVariable Long electionId) {
        return R.ok(electionService.listCandidates(electionId));
    }

    @Operation(summary = "保存候选人（全量替换）")
    @SaCheckPermission(value = {"election:add", "election:edit"}, mode = SaMode.OR)
    @PostMapping("/{electionId}/candidates")
    public R<Void> saveCandidates(@PathVariable Long electionId,
                                  @RequestBody List<OrgElectionCandidate> candidates) {
        electionService.saveCandidates(electionId, candidates);
        return R.ok("保存成功", null);
    }
}
