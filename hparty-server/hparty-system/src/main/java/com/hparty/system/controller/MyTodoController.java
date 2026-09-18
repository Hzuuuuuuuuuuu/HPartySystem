package com.hparty.system.controller;

import com.hparty.common.core.R;
import com.hparty.system.domain.vo.TodoResultVO;
import com.hparty.system.service.MyTodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「我的待办」接口。
 *
 * <p><b>不加 {@code @SaCheckPermission}</b>：待办是每个登录用户的个人视图，
 * 由数据权限保证「只看得到自己范围内的待办」，而不是由功能权限控制能否访问。
 * 权限标识与菜单授权只管菜单是否可见。</p>
 *
 * <p>聚合来源：发展党员 / 三会一课任务 / 党费 / 组织关系转接。不建新表。</p>
 */
@Tag(name = "25-我的待办")
@RestController
@RequestMapping("/my/todo")
@RequiredArgsConstructor
public class MyTodoController {

    private final MyTodoService todoService;

    @Operation(summary = "我的待办（按来源分组）")
    @GetMapping
    public R<TodoResultVO> list() {
        return R.ok(todoService.list());
    }

    @Operation(summary = "我的待办数量（首页卡片/红点）")
    @GetMapping("/count")
    public R<TodoResultVO> count() {
        return R.ok(todoService.count());
    }
}
