package com.hparty.system.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.R;
import com.hparty.framework.annotation.OperLog;
import com.hparty.system.domain.dto.PartyPersonQuery;
import com.hparty.system.report.dto.ReportDevelopQuery;
import com.hparty.system.report.dto.ReportDuesQuery;
import com.hparty.system.report.dto.ReportMeetingQuery;
import com.hparty.system.report.dto.ReportReviewQuery;
import com.hparty.system.report.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 统计报表导出接口。
 *
 * <p><b>为什么同时挂 GET 与 POST</b>：需求规格给的是 POST（条件放在请求体里，适合复杂筛选），
 * 而前端统一封装的是 {@code http.download(url, params)}（GET + query string）。
 * 两条路径指向同一个方法，两种调用方式都能用，前端不必为了导出单独改请求库。</p>
 *
 * <p>四个接口都记 {@code EXPORT} 类型的操作日志 —— 导出会把成批的党员信息带出系统，
 * 是最需要留痕的操作之一。</p>
 */
@Tag(name = "27-统计报表导出")
@RestController
@RequestMapping("/report/export")
@RequiredArgsConstructor
public class ReportExportController {

    private final ReportExportService exportService;

    @Operation(summary = "导出党员名册")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @GetMapping("/member")
    public void memberByGet(PartyPersonQuery query, HttpServletResponse response) throws IOException {
        exportService.exportMember(response, query);
    }

    @Operation(summary = "导出党员名册（POST）")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @PostMapping("/member")
    public void memberByPost(@RequestBody PartyPersonQuery query, HttpServletResponse response) throws IOException {
        exportService.exportMember(response, query);
    }

    @Operation(summary = "导出党费收缴台账")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @GetMapping("/dues")
    public void duesByGet(ReportDuesQuery query, HttpServletResponse response) throws IOException {
        exportService.exportDues(response, query);
    }

    @Operation(summary = "导出党费收缴台账（POST）")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @PostMapping("/dues")
    public void duesByPost(@RequestBody ReportDuesQuery query, HttpServletResponse response) throws IOException {
        exportService.exportDues(response, query);
    }

    @Operation(summary = "导出发展党员进度表")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @GetMapping("/develop")
    public void developByGet(ReportDevelopQuery query, HttpServletResponse response) throws IOException {
        exportService.exportDevelop(response, query);
    }

    @Operation(summary = "导出发展党员进度表（POST）")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @PostMapping("/develop")
    public void developByPost(@RequestBody ReportDevelopQuery query, HttpServletResponse response) throws IOException {
        exportService.exportDevelop(response, query);
    }

    @Operation(summary = "导出三会一课开展情况")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @GetMapping("/meeting")
    public void meetingByGet(ReportMeetingQuery query, HttpServletResponse response) throws IOException {
        exportService.exportMeeting(response, query);
    }

    @Operation(summary = "导出三会一课开展情况（POST）")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @PostMapping("/meeting")
    public void meetingByPost(@RequestBody ReportMeetingQuery query, HttpServletResponse response) throws IOException {
        exportService.exportMeeting(response, query);
    }

    @Operation(summary = "导出民主评议党员结果")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @GetMapping("/review")
    public void reviewByGet(ReportReviewQuery query, HttpServletResponse response) throws IOException {
        exportService.exportReview(response, query);
    }

    @Operation(summary = "导出民主评议党员结果（POST）")
    @SaCheckPermission("report:export")
    @OperLog(title = "统计报表导出", businessType = OperLog.BusinessType.EXPORT)
    @PostMapping("/review")
    public void reviewByPost(@RequestBody ReportReviewQuery query, HttpServletResponse response) throws IOException {
        exportService.exportReview(response, query);
    }

    @Operation(summary = "导出能力自检（便于前端探测权限）")
    @SaCheckPermission("report:export")
    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("ok");
    }
}
