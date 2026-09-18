package com.hparty.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.system.domain.dto.SysMenuDTO;
import com.hparty.system.domain.dto.SysMenuQuery;
import com.hparty.system.domain.entity.SysMenu;
import com.hparty.system.domain.vo.SysMenuTreeVO;
import com.hparty.system.service.SysMenuService;
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

/**
 * 菜单管理接口。
 */
@Tag(name = "菜单管理", description = "菜单/按钮权限的增删改查与菜单树")
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    /**
     * 菜单分页列表。
     *
     * @param query 过滤条件
     * @return 分页结果
     */
    @Operation(summary = "菜单列表", description = "支持按菜单名称/权限标识模糊、父菜单/类型/状态过滤")
    @SaCheckPermission("system:menu:list")
    @GetMapping("/list")
    public R<PageResult<SysMenu>> list(SysMenuQuery query) {
        return R.ok(menuService.listMenus(query));
    }

    /**
     * 菜单树。
     *
     * @return 根节点集合
     */
    @Operation(summary = "菜单树", description = "供角色分配权限时勾选")
    @SaCheckPermission("system:menu:list")
    @GetMapping("/tree")
    public R<List<SysMenuTreeVO>> tree() {
        return R.ok(menuService.listMenuTree());
    }

    /**
     * 菜单详情。
     *
     * @param menuId 菜单 ID
     * @return 菜单实体
     */
    @Operation(summary = "菜单详情")
    @SaCheckPermission("system:menu:list")
    @GetMapping("/{menuId}")
    public R<SysMenu> getInfo(@PathVariable("menuId") Long menuId) {
        return R.ok(menuService.getMenu(menuId));
    }

    /**
     * 新增菜单。
     *
     * @param dto 菜单信息
     * @return 新增的菜单 ID
     */
    @Operation(summary = "新增菜单", description = "菜单类型：M=目录 C=菜单 F=按钮")
    @SaCheckPermission("system:menu:add")
    @PostMapping
    public R<Long> add(@RequestBody SysMenuDTO dto) {
        return R.ok(menuService.addMenu(dto));
    }

    /**
     * 修改菜单。
     *
     * @param dto 菜单信息
     * @return 操作结果
     */
    @Operation(summary = "修改菜单", description = "上级菜单不能是自己或自己的下级")
    @SaCheckPermission("system:menu:edit")
    @PutMapping
    public R<Void> edit(@RequestBody SysMenuDTO dto) {
        menuService.updateMenu(dto);
        return R.ok();
    }

    /**
     * 删除菜单。
     *
     * @param menuId 菜单 ID
     * @return 操作结果
     */
    @Operation(summary = "删除菜单", description = "存在子菜单时不允许删除，同时清理角色授权关系")
    @SaCheckPermission("system:menu:remove")
    @DeleteMapping("/{menuId}")
    public R<Void> remove(@PathVariable("menuId") Long menuId) {
        menuService.removeMenu(menuId);
        return R.ok();
    }
}
