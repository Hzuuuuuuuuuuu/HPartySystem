package com.hparty.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.framework.annotation.OperLog;
import com.hparty.system.domain.dto.SysUserDTO;
import com.hparty.system.domain.dto.SysUserQuery;
import com.hparty.system.domain.vo.SysUserVO;
import com.hparty.system.service.SysUserService;
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

/**
 * 系统用户管理接口。
 */
@Tag(name = "用户管理", description = "账号的增删改查、角色分配、密码重置、启停")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    /**
     * 用户分页列表。
     *
     * @param query 过滤条件
     * @return 分页结果
     */
    @Operation(summary = "用户列表", description = "支持按账号/昵称/手机号模糊、组织/状态过滤，已应用数据权限")
    @SaCheckPermission("system:user:list")
    @GetMapping("/list")
    public R<PageResult<SysUserVO>> list(SysUserQuery query) {
        return R.ok(userService.listUser(query));
    }

    /**
     * 用户详情。
     *
     * @param userId 用户 ID
     * @return 用户信息，含角色 ID 列表
     */
    @Operation(summary = "用户详情", description = "含已分配角色 ID 与角色名称")
    @SaCheckPermission("system:user:list")
    @GetMapping("/{userId}")
    public R<SysUserVO> getInfo(@PathVariable("userId") Long userId) {
        return R.ok(userService.getUser(userId));
    }

    /**
     * 新增用户。
     *
     * @param dto 用户信息
     * @return 新增的用户 ID
     */
    @Operation(summary = "新增用户", description = "密码以 BCrypt 加密存储，同时写入角色关联")
    @SaCheckPermission("system:user:add")
    @OperLog(title = "用户管理", businessType = OperLog.BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody SysUserDTO dto) {
        return R.ok(userService.addUser(dto));
    }

    /**
     * 修改用户。
     *
     * @param dto 用户信息
     * @return 操作结果
     */
    @Operation(summary = "修改用户", description = "密码不在此接口修改，请使用重置密码")
    @SaCheckPermission("system:user:edit")
    @OperLog(title = "用户管理", businessType = OperLog.BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody SysUserDTO dto) {
        userService.updateUser(dto);
        return R.ok();
    }

    /**
     * 删除用户。
     *
     * @param userId 用户 ID
     * @return 操作结果
     */
    @Operation(summary = "删除用户", description = "超级管理员与当前登录账号不允许删除")
    @SaCheckPermission("system:user:remove")
    @OperLog(title = "用户管理", businessType = OperLog.BusinessType.DELETE)
    @DeleteMapping("/{userId}")
    public R<Void> remove(@PathVariable("userId") Long userId) {
        userService.removeUser(userId);
        return R.ok();
    }

    /**
     * 重置密码。
     *
     * @param userId 用户 ID
     * @param dto    请求体，仅使用其中的 password 字段
     * @return 操作结果
     */
    @Operation(summary = "重置密码", description = "服务端以 BCrypt 加密后入库，密码走请求体避免出现在 URL 中")
    @SaCheckPermission("system:user:resetPwd")
    @OperLog(title = "用户管理", businessType = OperLog.BusinessType.GRANT, saveRequestData = false)
    @PutMapping("/{userId}/password")
    public R<Void> resetPwd(@PathVariable("userId") Long userId,
                            @RequestBody SysUserDTO dto) {
        userService.resetPwd(userId, dto.getPassword());
        return R.ok();
    }

    /**
     * 启用 / 停用用户。
     *
     * @param userId 用户 ID
     * @param status 0=停用 1=正常
     * @return 操作结果
     */
    @Operation(summary = "启用/停用用户", description = "不允许停用超级管理员与当前登录账号")
    @SaCheckPermission("system:user:edit")
    @OperLog(title = "用户管理", businessType = OperLog.BusinessType.UPDATE)
    @PutMapping("/{userId}/status")
    public R<Void> changeStatus(@PathVariable("userId") Long userId,
                                @RequestParam("status") Integer status) {
        userService.changeStatus(userId, status);
        return R.ok();
    }
}
