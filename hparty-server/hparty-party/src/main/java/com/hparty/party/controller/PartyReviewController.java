package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.framework.annotation.OperLog;
import com.hparty.party.domain.dto.ReviewOrgEvalDTO;
import com.hparty.party.domain.dto.ReviewPeerEvalDTO;
import com.hparty.party.domain.dto.ReviewQuery;
import com.hparty.party.domain.dto.ReviewSelfEvalDTO;
import com.hparty.party.domain.entity.PartyReview;
import com.hparty.party.domain.entity.PartyReviewDetail;
import com.hparty.party.service.PartyReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 民主评议党员接口。
 *
 * <p>自评与互评接口**不加功能权限**：民主评议是全体党员都要参加的政治生活，
 * 每个登录用户都应能提交，身份与打分对象均由服务端从登录会话取，
 * 客户端无法指定「替谁打分」。组织评定与批次维护则需要相应权限。</p>
 */
@Tag(name = "26-民主评议党员")
@RestController
@RequestMapping("/party/review")
@RequiredArgsConstructor
public class PartyReviewController {

    private final PartyReviewService reviewService;

    @Operation(summary = "评议批次分页")
    @SaCheckPermission("review:list")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(ReviewQuery query) {
        return R.ok(reviewService.page(query));
    }

    @Operation(summary = "等次分布统计")
    @SaCheckPermission("review:list")
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(required = false) Long reviewId) {
        return R.ok(reviewService.statistics(reviewId));
    }

    @Operation(summary = "评议批次详情（含明细）")
    @SaCheckPermission("review:list")
    @GetMapping("/{reviewId}")
    public R<Map<String, Object>> detail(@PathVariable Long reviewId) {
        return R.ok(reviewService.detail(reviewId));
    }

    @Operation(summary = "创建评议批次")
    @SaCheckPermission("review:add")
    @OperLog(title = "民主评议党员", businessType = OperLog.BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody PartyReview review) {
        return R.ok("创建成功", reviewService.add(review));
    }

    @Operation(summary = "修改评议批次")
    @SaCheckPermission("review:edit")
    @OperLog(title = "民主评议党员", businessType = OperLog.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody PartyReview review) {
        reviewService.update(review);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除评议批次（仅草稿）")
    @SaCheckPermission("review:remove")
    @OperLog(title = "民主评议党员", businessType = OperLog.BusinessType.DELETE)
    @DeleteMapping("/{reviewId}")
    public R<Void> remove(@PathVariable Long reviewId) {
        reviewService.remove(reviewId);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "启动评议（生成明细）")
    @SaCheckPermission("review:edit")
    @OperLog(title = "民主评议党员", businessType = OperLog.BusinessType.UPDATE)
    @PostMapping("/{reviewId}/start")
    public R<Integer> start(@PathVariable Long reviewId) {
        int count = reviewService.start(reviewId);
        return R.ok("已启动，生成 " + count + " 条评议明细", count);
    }

    @Operation(summary = "评议明细列表")
    @SaCheckPermission("review:list")
    @GetMapping("/{reviewId}/details")
    public R<List<PartyReviewDetail>> details(@PathVariable Long reviewId) {
        return R.ok(reviewService.listDetails(reviewId));
    }

    @Operation(summary = "提交自评（本人）")
    @OperLog(title = "民主评议党员", businessType = OperLog.BusinessType.UPDATE)
    @PostMapping("/{reviewId}/self-eval")
    public R<Void> selfEval(@PathVariable Long reviewId,
                            @Valid @RequestBody ReviewSelfEvalDTO dto) {
        reviewService.submitSelfEval(reviewId, dto);
        return R.ok("自评已提交", null);
    }

    @Operation(summary = "提交互评（批量，不能给自己打分）")
    @OperLog(title = "民主评议党员", businessType = OperLog.BusinessType.UPDATE)
    @PostMapping("/{reviewId}/peer-eval")
    public R<Integer> peerEval(@PathVariable Long reviewId,
                               @Valid @RequestBody ReviewPeerEvalDTO dto) {
        int count = reviewService.submitPeerEval(reviewId, dto);
        return R.ok("互评已提交 " + count + " 条", count);
    }

    @Operation(summary = "组织评定（批量）")
    @SaCheckPermission("review:judge")
    @OperLog(title = "民主评议党员", businessType = OperLog.BusinessType.APPROVE)
    @PostMapping("/{reviewId}/org-eval")
    public R<Integer> orgEval(@PathVariable Long reviewId,
                              @Valid @RequestBody ReviewOrgEvalDTO dto) {
        int count = reviewService.submitOrgEval(reviewId, dto);
        return R.ok("组织评定已提交 " + count + " 条", count);
    }
}
