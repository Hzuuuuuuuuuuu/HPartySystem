package com.hparty.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.system.domain.dto.SysDictDataQuery;
import com.hparty.system.domain.dto.SysDictTypeQuery;
import com.hparty.system.domain.entity.SysDictData;
import com.hparty.system.domain.entity.SysDictType;
import com.hparty.system.domain.vo.SysDictDataVO;
import com.hparty.system.service.SysDictDataService;
import com.hparty.system.service.SysDictTypeService;
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
 * 字典管理接口：字典类型 + 字典数据。
 * <p>权限说明：初始化脚本中只预置了 {@code system:dict:list}，因此写操作采用 OR 语义 ——
 * 已扩展按钮权限的环境按 {@code system:dict:add/edit/remove} 校验，未扩展的环境退化为 {@code system:dict:list}，
 * 避免接口因权限未初始化而完全不可用。</p>
 */
@Tag(name = "字典管理")
@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictTypeService dictTypeService;

    private final SysDictDataService dictDataService;

    // ==================== 字典类型 ====================

    /**
     * 分页查询字典类型。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询字典类型")
    @SaCheckPermission("system:dict:list")
    @GetMapping("/type/page")
    public R<PageResult<SysDictType>> pageType(SysDictTypeQuery query) {
        return R.ok(dictTypeService.page(query));
    }

    /**
     * 查询字典类型详情。
     *
     * @param dictId 字典主键
     * @return 字典类型
     */
    @Operation(summary = "查询字典类型详情")
    @SaCheckPermission("system:dict:list")
    @GetMapping("/type/{dictId}")
    public R<SysDictType> getType(@PathVariable Long dictId) {
        return R.ok(dictTypeService.getDetail(dictId));
    }

    /**
     * 新增字典类型。
     *
     * @param dictType 字典类型
     * @return 操作结果
     */
    @Operation(summary = "新增字典类型")
    @SaCheckPermission(value = {"system:dict:add", "system:dict:list"}, mode = SaMode.OR)
    @PostMapping("/type")
    public R<Void> addType(@RequestBody SysDictType dictType) {
        return R.toR(dictTypeService.add(dictType));
    }

    /**
     * 修改字典类型。
     *
     * @param dictType 字典类型
     * @return 操作结果
     */
    @Operation(summary = "修改字典类型")
    @SaCheckPermission(value = {"system:dict:edit", "system:dict:list"}, mode = SaMode.OR)
    @PutMapping("/type")
    public R<Void> updateType(@RequestBody SysDictType dictType) {
        return R.toR(dictTypeService.update(dictType));
    }

    /**
     * 删除字典类型（类型下存在字典数据时不允许删除）。
     *
     * @param dictId 字典主键
     * @return 操作结果
     */
    @Operation(summary = "删除字典类型")
    @SaCheckPermission(value = {"system:dict:remove", "system:dict:list"}, mode = SaMode.OR)
    @DeleteMapping("/type/{dictId}")
    public R<Void> removeType(@PathVariable Long dictId) {
        return R.toR(dictTypeService.remove(dictId));
    }

    // ==================== 字典数据 ====================

    /**
     * 按字典类型查询字典数据（走缓存）。
     *
     * @param dictType 字典类型
     * @param status   状态过滤：0=停用 1=正常，为空返回全部
     * @return 字典数据列表，按 dict_sort 升序
     */
    @Operation(summary = "按字典类型查询字典数据")
    @SaCheckPermission("system:dict:list")
    @GetMapping("/data/type/{dictType}")
    public R<List<SysDictDataVO>> listDataByType(@PathVariable String dictType,
                                                 @RequestParam(required = false) Integer status) {
        return R.ok(dictDataService.listByType(dictType, status));
    }

    /**
     * 分页查询字典数据（管理页面用，不走缓存）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询字典数据")
    @SaCheckPermission("system:dict:list")
    @GetMapping("/data/page")
    public R<PageResult<SysDictDataVO>> pageData(SysDictDataQuery query) {
        return R.ok(dictDataService.page(query));
    }

    /**
     * 查询字典数据详情。
     *
     * @param dictCode 字典编码
     * @return 字典数据
     */
    @Operation(summary = "查询字典数据详情")
    @SaCheckPermission("system:dict:list")
    @GetMapping("/data/{dictCode}")
    public R<SysDictDataVO> getData(@PathVariable Long dictCode) {
        return R.ok(dictDataService.getDetail(dictCode));
    }

    /**
     * 新增字典数据（新增后自动失效该类型缓存）。
     *
     * @param dictData 字典数据
     * @return 操作结果
     */
    @Operation(summary = "新增字典数据")
    @SaCheckPermission(value = {"system:dict:add", "system:dict:list"}, mode = SaMode.OR)
    @PostMapping("/data")
    public R<Void> addData(@RequestBody SysDictData dictData) {
        return R.toR(dictDataService.add(dictData));
    }

    /**
     * 修改字典数据（修改后自动失效新旧类型缓存）。
     *
     * @param dictData 字典数据
     * @return 操作结果
     */
    @Operation(summary = "修改字典数据")
    @SaCheckPermission(value = {"system:dict:edit", "system:dict:list"}, mode = SaMode.OR)
    @PutMapping("/data")
    public R<Void> updateData(@RequestBody SysDictData dictData) {
        return R.toR(dictDataService.update(dictData));
    }

    /**
     * 删除字典数据（删除后自动失效该类型缓存）。
     *
     * @param dictCode 字典编码
     * @return 操作结果
     */
    @Operation(summary = "删除字典数据")
    @SaCheckPermission(value = {"system:dict:remove", "system:dict:list"}, mode = SaMode.OR)
    @DeleteMapping("/data/{dictCode}")
    public R<Void> removeData(@PathVariable Long dictCode) {
        return R.toR(dictDataService.remove(dictCode));
    }

    /**
     * 清空全部字典缓存，用于字典表被外部批量变更后的兜底刷新。
     *
     * @return 操作结果
     */
    @Operation(summary = "刷新字典缓存")
    @SaCheckPermission(value = {"system:dict:remove", "system:dict:list"}, mode = SaMode.OR)
    @DeleteMapping("/cache")
    public R<Void> refreshCache() {
        dictDataService.clearCache();
        return R.ok();
    }
}
