package com.hparty.develop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.develop.domain.dto.DevApplicantDTO;
import com.hparty.develop.domain.dto.DevApplicantQuery;
import com.hparty.develop.domain.vo.DevApplicantCardVO;
import com.hparty.develop.domain.vo.DevHandleResultVO;
import com.hparty.develop.domain.vo.DevTimelineVO;
import com.hparty.develop.service.DevApplicantService;
import com.hparty.develop.service.DevFlowService;
import com.hparty.framework.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 发展党员管理接口（对应图5 卡片墙与 25 步时间轴详情）。
 */
@Tag(name = "10-发展党员")
@RestController
@RequestMapping("/develop/applicant")
@RequiredArgsConstructor
public class DevApplicantController {

    private final DevApplicantService applicantService;
    private final DevFlowService flowService;

    @Operation(summary = "发展党员卡片墙分页（支持按阶段筛选）")
    @SaCheckPermission("develop:applicant:list")
    @GetMapping("/page")
    public R<PageResult<DevApplicantCardVO>> page(DevApplicantQuery query) {
        return R.ok(applicantService.pageCards(query));
    }

    @Operation(summary = "25 步时间轴详情")
    @SaCheckPermission("develop:applicant:detail")
    @GetMapping("/{applicantId}/timeline")
    public R<DevTimelineVO> timeline(@PathVariable Long applicantId) {
        return R.ok(applicantService.timeline(applicantId));
    }

    @Operation(summary = "申请人本人提交当前步骤")
    @SaCheckPermission("develop:applicant:self-submit")
    @PostMapping("/{applicantId}/self-submit")
    public R<DevHandleResultVO> selfSubmit(@PathVariable Long applicantId) {
        return R.ok(flowService.selfSubmit(applicantId));
    }

    @Operation(summary = "各阶段人数统计")
    @SaCheckPermission("develop:stat:list")
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        return R.ok(applicantService.statistics());
    }

    @Operation(summary = "新增加发展对象")
    @SaCheckPermission("develop:applicant:add")
    @OperLog(title = "发展党员", businessType = OperLog.BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody DevApplicantDTO dto) {
        return R.ok("新增成功", applicantService.add(dto));
    }

    @Operation(summary = "修改发展对象")
    @SaCheckPermission("develop:applicant:edit")
    @OperLog(title = "发展党员", businessType = OperLog.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@Valid @RequestBody DevApplicantDTO dto) {
        applicantService.update(dto);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除发展对象")
    @SaCheckPermission("develop:applicant:remove")
    @OperLog(title = "发展党员", businessType = OperLog.BusinessType.DELETE)
    @DeleteMapping("/{applicantId}")
    public R<Void> remove(@PathVariable Long applicantId) {
        applicantService.remove(applicantId);
        return R.ok("删除成功", null);
    }
}
