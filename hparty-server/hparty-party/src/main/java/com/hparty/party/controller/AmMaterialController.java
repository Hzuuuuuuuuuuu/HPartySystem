package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.R;
import com.hparty.common.enums.MaterialCategory;
import com.hparty.party.domain.entity.AmMaterial;
import com.hparty.party.service.AmMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织生活会材料接口（对应图2 的 11 个材料入口）。
 */
@Tag(name = "22-组织生活会材料")
@RestController
@RequestMapping("/party/material")
@RequiredArgsConstructor
public class AmMaterialController {

    private final AmMaterialService materialService;

    @Operation(summary = "材料分类清单（用于渲染九宫格入口）")
    @GetMapping("/categories")
    public R<List<Map<String, String>>> categories() {
        List<Map<String, String>> list = new ArrayList<>();
        for (MaterialCategory c : MaterialCategory.values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", c.getCode());
            item.put("label", c.getLabel());
            item.put("icon", c.getIcon());
            list.add(item);
        }
        return R.ok(list);
    }

    @Operation(summary = "材料列表（按分类过滤）")
    @SaCheckPermission("orglife:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String category,
                                             @RequestParam(required = false) Long meetingId) {
        return R.ok(materialService.list(category, meetingId));
    }

    @Operation(summary = "材料详情")
    @SaCheckPermission("orglife:list")
    @GetMapping("/{materialId}")
    public R<AmMaterial> get(@PathVariable Long materialId) {
        return R.ok(materialService.get(materialId));
    }

    @Operation(summary = "新增材料")
    @SaCheckPermission("orglife:add")
    @PostMapping
    public R<Long> add(@RequestBody AmMaterial material) {
        return R.ok("新增成功", materialService.add(material));
    }

    @Operation(summary = "修改材料")
    @SaCheckPermission("orglife:edit")
    @PutMapping
    public R<Void> update(@RequestBody AmMaterial material) {
        materialService.update(material);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除材料")
    @SaCheckPermission("orglife:remove")
    @DeleteMapping("/{materialId}")
    public R<Void> remove(@PathVariable Long materialId) {
        materialService.remove(materialId);
        return R.ok("删除成功", null);
    }
}
