package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.party.domain.dto.MemberServiceQuery;
import com.hparty.party.domain.entity.MemberService;
import com.hparty.party.service.MemberServiceService;
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

import java.util.Map;

/**
 * 党员服务接口（困难帮扶、志愿服务、走访慰问等）。
 */
@Tag(name = "27-党员服务")
@RestController
@RequestMapping("/party/service")
@RequiredArgsConstructor
public class MemberServiceController {

    private final MemberServiceService memberService;

    @Operation(summary = "分页查询党员服务记录")
    @SaCheckPermission("service:list")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(MemberServiceQuery query) {
        return R.ok(memberService.page(query));
    }

    @Operation(summary = "党员服务统计")
    @SaCheckPermission("service:list")
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(required = false) Long orgId) {
        return R.ok(memberService.statistics(orgId));
    }

    @Operation(summary = "党员服务详情")
    @SaCheckPermission("service:list")
    @GetMapping("/{serviceId}")
    public R<Map<String, Object>> detail(@PathVariable Long serviceId) {
        return R.ok(memberService.detail(serviceId));
    }

    @Operation(summary = "新增党员服务")
    @SaCheckPermission("service:add")
    @PostMapping
    public R<Long> add(@RequestBody MemberService service) {
        return R.ok("新增成功", memberService.add(service));
    }

    @Operation(summary = "修改党员服务")
    @SaCheckPermission("service:edit")
    @PutMapping
    public R<Void> update(@RequestBody MemberService service) {
        memberService.update(service);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除党员服务")
    @SaCheckPermission("service:remove")
    @DeleteMapping("/{serviceId}")
    public R<Void> remove(@PathVariable Long serviceId) {
        memberService.remove(serviceId);
        return R.ok("删除成功", null);
    }
}
