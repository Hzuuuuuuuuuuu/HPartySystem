package com.hparty.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.framework.annotation.OperLog;
import com.hparty.system.domain.dto.SysRoleDTO;
import com.hparty.system.domain.dto.SysRoleQuery;
import com.hparty.system.domain.vo.SysRoleVO;
import com.hparty.system.service.SysRoleService;
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

/**
 * 角色管理接口。
 */
@Tag(name = "角色管理", description = "角色的增删改查、菜单权限分配、数据范围维护")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    /**
     * 角色分页列表。
     *
     * @param query 过滤条件
     * @return 分页结果
     */
    @Operation(summary = "角色列表", description = "支持按角色名称/权限字符串模糊、状态过滤")
    @SaCheckPermission("system:role:list")
    @GetMapping("/list")
    public R<PageResult<SysRoleVO>> list(SysRoleQuery query) {
        return R.ok(roleService.listRole(query));
    }

    /**
     * 角色详情。
     *
     * @param roleId 角色 ID
     * @return 角色信息，含已分配菜单 ID 列表
     */
    @Operation(summary = "角色详情", description = "含已分配菜单 ID 与自定义数据范围的组织 ID")
    @SaCheckPermission("system:role:list")
    @GetMapping("/{roleId}")
    public R<SysRoleVO> getInfo(@PathVariable("roleId") Long roleId) {
        return R.ok(roleService.getRole(roleId));
    }

    /**
     * 新增角色。
     *
     * @param dto 角色信息
     * @return 新增的角色 ID
     */
    @Operation(summary = "新增角色", description = "可同时指定菜单权限与自定义数据范围")
    @SaCheckPermission("system:role:add")
    @PostMapping
    public R<Long> add(@RequestBody SysRoleDTO dto) {
        return R.ok(roleService.addRole(dto));
    }

    /**
     * 修改角色。
     *
     * @param dto 角色信息
     * @return 操作结果
     */
    @Operation(summary = "修改角色", description = "传入 menuIds / deptIds 时全量覆盖原有关联")
    @SaCheckPermission("system:role:edit")
    @PutMapping
    public R<Void> edit(@RequestBody SysRoleDTO dto) {
        roleService.updateRole(dto);
        return R.ok();
    }

    /**
     * 删除角色。
     *
     * @param roleId 角色 ID
     * @return 操作结果
     */
    @Operation(summary = "删除角色", description = "内置角色、已被用户引用的角色不允许删除")
    @SaCheckPermission("system:role:remove")
    @OperLog(title = "角色管理", businessType = OperLog.BusinessType.DELETE)
    @DeleteMapping("/{roleId}")
    public R<Void> remove(@PathVariable("roleId") Long roleId) {
        roleService.removeRole(roleId);
        return R.ok();
    }

    /**
     * 分配菜单权限。
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 集合，传空表示清空全部权限
     * @return 操作结果
     */
    @Operation(summary = "分配菜单权限", description = "全量覆盖该角色的菜单授权")
    @SaCheckPermission("system:role:edit")
    @OperLog(title = "角色管理", businessType = OperLog.BusinessType.GRANT)
    @PutMapping("/{roleId}/menus")
    public R<Void> assignMenus(@PathVariable("roleId") Long roleId,
                               @RequestBody List<Long> menuIds) {
        roleService.assignMenus(roleId, menuIds);
        return R.ok();
    }

    /**
     * 启用 / 停用角色。
     *
     * @param roleId 角色 ID
     * @param status 0=停用 1=正常
     * @return 操作结果
     */
    @Operation(summary = "启用/停用角色", description = "超级管理员角色不允许停用")
    @SaCheckPermission("system:role:edit")
    @OperLog(title = "角色管理", businessType = OperLog.BusinessType.UPDATE)
    @PutMapping("/{roleId}/status")
    public R<Void> changeStatus(@PathVariable("roleId") Long roleId,
                                @RequestParam("status") Integer status) {
        roleService.changeStatus(roleId, status);
        return R.ok();
    }
}
