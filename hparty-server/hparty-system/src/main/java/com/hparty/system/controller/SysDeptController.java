package com.hparty.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.system.domain.dto.SysDeptQuery;
import com.hparty.system.domain.entity.SysDept;
import com.hparty.system.domain.vo.SysDeptTreeVO;
import com.hparty.system.service.SysDeptService;
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
 * 党组织管理接口。
 */
@Tag(name = "党组织管理", description = "党组织树的增删改查、组织架构图数据")
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    /**
     * 党组织分页列表。
     *
     * @param query 过滤条件
     * @return 分页结果
     */
    @Operation(summary = "党组织列表", description = "支持按组织名称模糊、组织类型、状态过滤，已应用数据权限")
    @SaCheckPermission("system:dept:list")
    @GetMapping("/list")
    public R<PageResult<SysDept>> list(SysDeptQuery query) {
        return R.ok(deptService.listDept(query));
    }

    /**
     * 组织架构树。
     *
     * @return 根节点集合
     */
    @Operation(summary = "组织架构树", description = "返回全量组织树，用于组织架构图与上级组织选择")
    @SaCheckPermission("system:dept:list")
    @GetMapping("/tree")
    public R<List<SysDeptTreeVO>> tree() {
        return R.ok(deptService.listDeptTree());
    }

    /**
     * 党组织详情。
     *
     * @param orgId 组织 ID
     * @return 组织实体
     */
    @Operation(summary = "党组织详情")
    @SaCheckPermission("system:dept:list")
    @GetMapping("/{orgId}")
    public R<SysDept> getInfo(@PathVariable("orgId") Long orgId) {
        return R.ok(deptService.getDept(orgId));
    }

    /**
     * 新增党组织。
     *
     * @param dept 组织信息
     * @return 新增的组织 ID
     */
    @Operation(summary = "新增党组织", description = "自动维护 orgPath 与 ancestors")
    @SaCheckPermission("system:dept:add")
    @PostMapping
    public R<Long> add(@RequestBody SysDept dept) {
        return R.ok(deptService.addDept(dept));
    }

    /**
     * 修改党组织。
     *
     * @param dept 组织信息
     * @return 操作结果
     */
    @Operation(summary = "修改党组织", description = "变更父节点时级联重写所有子孙的 orgPath 与 ancestors")
    @SaCheckPermission("system:dept:edit")
    @PutMapping
    public R<Void> edit(@RequestBody SysDept dept) {
        deptService.updateDept(dept);
        return R.ok();
    }

    /**
     * 删除党组织。
     *
     * @param orgId 组织 ID
     * @return 操作结果
     */
    @Operation(summary = "删除党组织", description = "存在下级组织或关联人员档案时不允许删除")
    @SaCheckPermission("system:dept:remove")
    @DeleteMapping("/{orgId}")
    public R<Void> remove(@PathVariable("orgId") Long orgId) {
        deptService.removeDept(orgId);
        return R.ok();
    }
}
