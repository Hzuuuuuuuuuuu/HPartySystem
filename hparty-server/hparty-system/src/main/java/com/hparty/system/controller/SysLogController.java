package com.hparty.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.system.domain.dto.SysLogQuery;
import com.hparty.system.domain.entity.SysLoginLog;
import com.hparty.system.domain.entity.SysOperLog;
import com.hparty.system.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 登录日志 / 操作日志接口。
 */
@Tag(name = "07-日志管理")
@RestController
@RequestMapping("/system/log")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService logService;

    // ==================== 登录日志 ====================

    @Operation(summary = "登录日志分页")
    @SaCheckPermission("system:loginlog:list")
    @GetMapping("/login/page")
    public R<PageResult<SysLoginLog>> pageLoginLogs(SysLogQuery query) {
        return R.ok(logService.pageLoginLogs(query));
    }

    @Operation(summary = "删除登录日志", description = "ids 为逗号分隔的多个 ID，如 1,2,3")
    @SaCheckPermission("system:loginlog:remove")
    // 用正则约束路径变量，避免与上面的 /login/clean 产生歧义
    // （虽然 Spring 的字面量优先规则当前能正确路由，但显式约束更稳妥）
    @DeleteMapping("/login/{ids:[0-9,]+}")
    public R<Void> removeLoginLogs(@PathVariable List<Long> ids) {
        logService.removeLoginLogs(ids);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "清空登录日志（仅超级管理员）")
    @SaCheckRole(Constants.SUPER_ADMIN_ROLE)
    @DeleteMapping("/login/clean")
    public R<Void> cleanLoginLogs() {
        logService.cleanLoginLogs();
        return R.ok("已清空", null);
    }

    // ==================== 操作日志 ====================

    @Operation(summary = "操作日志分页")
    @SaCheckPermission("system:operlog:list")
    @GetMapping("/oper/page")
    public R<PageResult<SysOperLog>> pageOperLogs(SysLogQuery query) {
        return R.ok(logService.pageOperLogs(query));
    }

    @Operation(summary = "操作日志详情")
    @SaCheckPermission("system:operlog:list")
    @GetMapping("/oper/{operId}")
    public R<SysOperLog> getOperLog(@PathVariable Long operId) {
        return R.ok(logService.getOperLog(operId));
    }

    @Operation(summary = "删除操作日志", description = "ids 为逗号分隔的多个 ID，如 1,2,3")
    @SaCheckPermission("system:operlog:remove")
    @DeleteMapping("/oper/{ids:[0-9,]+}")
    public R<Void> removeOperLogs(@PathVariable List<Long> ids) {
        logService.removeOperLogs(ids);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "清空操作日志（仅超级管理员）")
    @SaCheckRole(Constants.SUPER_ADMIN_ROLE)
    @DeleteMapping("/oper/clean")
    public R<Void> cleanOperLogs() {
        logService.cleanOperLogs();
        return R.ok("已清空", null);
    }
}
