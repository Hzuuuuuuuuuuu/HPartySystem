package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.framework.annotation.OperLog;
import com.hparty.party.domain.dto.TransferQuery;
import com.hparty.party.domain.entity.PartyTransfer;
import com.hparty.party.service.PartyTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
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
 * 组织关系转接接口。
 *
 * <p>列表与详情按「原组织 / 目标组织」双向裁剪可见性，写操作按主键做越权校验。
 * 权限标识与 {@code sys_menu} 中种下的按钮权限一一对应。</p>
 */
@Tag(name = "24-组织关系转接")
@RestController
@RequestMapping("/party/transfer")
@RequiredArgsConstructor
public class PartyTransferController {

    private final PartyTransferService transferService;

    @Operation(summary = "分页查询转接单")
    @SaCheckPermission("transfer:list")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(TransferQuery query) {
        return R.ok(transferService.page(query));
    }

    @Operation(summary = "超期未落地清单（口袋党员）")
    @SaCheckPermission("transfer:list")
    @GetMapping("/overdue")
    public R<List<Map<String, Object>>> overdue(TransferQuery query) {
        return R.ok(transferService.overdueList(query));
    }

    @Operation(summary = "转接详情（含流转时间线）")
    @SaCheckPermission("transfer:list")
    @GetMapping("/{transferId}")
    public R<Map<String, Object>> detail(@PathVariable Long transferId) {
        return R.ok(transferService.detail(transferId));
    }

    @Operation(summary = "发起转接")
    @SaCheckPermission("transfer:add")
    @OperLog(title = "组织关系转接", businessType = OperLog.BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody PartyTransfer transfer) {
        return R.ok("发起成功", transferService.add(transfer));
    }

    @Operation(summary = "修改转接单（仅待提交）")
    @SaCheckPermission("transfer:edit")
    @OperLog(title = "组织关系转接", businessType = OperLog.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody PartyTransfer transfer) {
        transferService.update(transfer);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "开具介绍信")
    @SaCheckPermission("transfer:handle")
    @OperLog(title = "组织关系转接", businessType = OperLog.BusinessType.APPROVE)
    @PostMapping("/{transferId}/issue")
    public R<Void> issue(@PathVariable Long transferId) {
        transferService.issue(transferId);
        return R.ok("介绍信已开具", null);
    }

    @Operation(summary = "接收（组织关系变更）")
    @SaCheckPermission("transfer:handle")
    @OperLog(title = "组织关系转接", businessType = OperLog.BusinessType.APPROVE)
    @PostMapping("/{transferId}/accept")
    public R<Void> accept(@PathVariable Long transferId) {
        return R.ok(transferService.accept(transferId), null);
    }

    @Operation(summary = "拒绝接收")
    @SaCheckPermission("transfer:handle")
    @OperLog(title = "组织关系转接", businessType = OperLog.BusinessType.APPROVE)
    @PostMapping("/{transferId}/reject")
    public R<Void> reject(@PathVariable Long transferId, @RequestBody RejectBody body) {
        transferService.reject(transferId, body == null ? null : body.getReason());
        return R.ok("已拒绝", null);
    }

    @Operation(summary = "撤销转接单")
    @SaCheckPermission("transfer:handle")
    @OperLog(title = "组织关系转接", businessType = OperLog.BusinessType.UPDATE)
    @PostMapping("/{transferId}/revoke")
    public R<Void> revoke(@PathVariable Long transferId) {
        transferService.revoke(transferId);
        return R.ok("已撤销", null);
    }

    @Operation(summary = "删除转接单（仅待提交）")
    @SaCheckPermission("transfer:remove")
    @OperLog(title = "组织关系转接", businessType = OperLog.BusinessType.DELETE)
    @DeleteMapping("/{transferId}")
    public R<Void> remove(@PathVariable Long transferId) {
        transferService.remove(transferId);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "刷新超期标记（幂等，供定时任务或手工触发）")
    @SaCheckPermission("transfer:handle")
    @PostMapping("/refresh-overdue")
    public R<Integer> refreshOverdue() {
        return R.ok("已刷新", transferService.refreshOverdueFlags());
    }

    /** 拒绝接收的请求体 */
    @Data
    public static class RejectBody {
        /** 拒绝原因 */
        private String reason;
    }
}
