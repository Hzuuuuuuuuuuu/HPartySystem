package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.R;
import com.hparty.party.domain.entity.AmTask;
import com.hparty.party.domain.entity.AmTaskSubmit;
import com.hparty.party.service.AmTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 活动任务通知接口（对应图1 底部的任务卡与「上传资料」）。
 */
@Tag(name = "21-活动任务通知")
@RestController
@RequestMapping("/party/task")
@RequiredArgsConstructor
public class AmTaskController {

    private final AmTaskService taskService;

    @Operation(summary = "任务列表")
    @SaCheckPermission("task:list")
    @GetMapping("/list")
    public R<List<AmTask>> list(@RequestParam(required = false) String taskType,
                                @RequestParam(required = false) Integer status) {
        return R.ok(taskService.list(taskType, status));
    }

    @Operation(summary = "任务详情")
    @SaCheckPermission("task:list")
    @GetMapping("/{taskId}")
    public R<AmTask> get(@PathVariable Long taskId) {
        return R.ok(taskService.get(taskId));
    }

    @Operation(summary = "发布任务")
    @SaCheckPermission("task:add")
    @PostMapping
    public R<Long> add(@RequestBody AmTask task) {
        return R.ok("发布成功", taskService.add(task));
    }

    @Operation(summary = "修改任务")
    @SaCheckPermission("task:edit")
    @PutMapping
    public R<Void> update(@RequestBody AmTask task) {
        taskService.update(task);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除任务")
    @SaCheckPermission("task:remove")
    @DeleteMapping("/{taskId}")
    public R<Void> remove(@PathVariable Long taskId) {
        taskService.remove(taskId);
        return R.ok("删除成功", null);
    }

    /**
     * 支部上传任务材料。
     * <p>前端 {@code TaskNoticeList.tsx} 用 multipart 提交（表单字段 {@code file}），
     * 因此这里必须接收 {@link MultipartFile}；只声明 {@code fileId}/{@code fileUrl}
     * 会导致文件被静默丢弃（接口仍返回成功，但落库恒为 NULL）。</p>
     *
     * @param taskId  任务ID
     * @param file    上传的文件（表单字段 file），可为空
     * @param fileId  已单独上传到 /file/upload 的文件ID，可为空
     * @param fileUrl 已单独上传的访问URL，可为空
     * @param remark  备注
     * @return 提交记录ID
     */
    @Operation(summary = "支部上传任务材料")
    @SaCheckPermission("task:submit")
    @PostMapping("/submit")
    public R<Long> submit(@RequestParam Long taskId,
                          @RequestParam(value = "file", required = false) MultipartFile file,
                          @RequestParam(required = false) Long fileId,
                          @RequestParam(required = false) String fileUrl,
                          @RequestParam(required = false) String remark) {
        return R.ok("上传成功", taskService.submit(taskId, file, fileId, fileUrl, remark));
    }

    @Operation(summary = "任务提交记录")
    @SaCheckPermission("task:list")
    @GetMapping("/{taskId}/submits")
    public R<List<AmTaskSubmit>> submits(@PathVariable Long taskId) {
        return R.ok(taskService.listSubmits(taskId));
    }
}
