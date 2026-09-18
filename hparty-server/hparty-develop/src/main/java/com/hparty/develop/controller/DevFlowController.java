package com.hparty.develop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.R;
import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.DevStage;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.domain.vo.DevHandleResultVO;
import com.hparty.develop.rule.DevRuleEngine;
import com.hparty.develop.service.DevFlowService;
import com.hparty.develop.service.DevStepService;
import com.hparty.framework.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 发展党员流程办理接口（5 阶段 25 步状态机）。
 */
@Tag(name = "11-发展党员流程")
@RestController
@RequestMapping("/develop/flow")
@RequiredArgsConstructor
public class DevFlowController {

    private final DevFlowService flowService;
    private final DevStepService stepService;
    private final DevRuleEngine ruleEngine;

    @Operation(summary = "办理当前步骤（通过/驳回/不通过）")
    @SaCheckPermission("develop:applicant:handle")
    @OperLog(title = "发展党员", businessType = OperLog.BusinessType.APPROVE)
    @PostMapping("/handle")
    public R<DevHandleResultVO> handle(@Valid @RequestBody DevHandleDTO dto) {
        return R.ok(flowService.handle(dto));
    }

    @Operation(summary = "提交前规则预检（只校验不落库）")
    @SaCheckPermission("develop:applicant:handle")
    @PostMapping("/preview")
    public R<List<String>> preview(@Valid @RequestBody DevHandleDTO dto) {
        return R.ok(flowService.preview(dto));
    }

    @Operation(summary = "查询全部阶段模板（5 个）")
    @SaCheckPermission("develop:step:list")
    @GetMapping("/stages")
    public R<List<DevStage>> stages() {
        return R.ok(stepService.listStages());
    }

    @Operation(summary = "查询全部步骤模板（25 个）")
    @SaCheckPermission("develop:step:list")
    @GetMapping("/steps")
    public R<List<DevStep>> steps() {
        return R.ok(stepService.listSteps());
    }

    @Operation(summary = "查询某步骤受哪些规则约束")
    @SaCheckPermission("develop:step:list")
    @GetMapping("/steps/{stepCode}/rules")
    public R<List<Map<String, String>>> stepRules(@PathVariable String stepCode) {
        return R.ok(ruleEngine.describeRules(stepService.getByCode(stepCode)));
    }
}
