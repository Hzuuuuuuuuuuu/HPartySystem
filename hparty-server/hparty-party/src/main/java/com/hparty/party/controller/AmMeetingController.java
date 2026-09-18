package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.R;
import com.hparty.party.domain.entity.AmAttendee;
import com.hparty.party.domain.entity.AmMeeting;
import com.hparty.party.service.AmMeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 三会一课接口（对应图1）。
 */
@Tag(name = "20-三会一课")
@RestController
@RequestMapping("/party/meeting")
@RequiredArgsConstructor
public class AmMeetingController {

    private final AmMeetingService meetingService;

    @Operation(summary = "会议列表（可按类型过滤）")
    @SaCheckPermission("meeting:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String meetingType) {
        return R.ok(meetingService.list(meetingType));
    }

    @Operation(summary = "会议详情")
    @SaCheckPermission("meeting:list")
    @GetMapping("/{meetingId}")
    public R<AmMeeting> get(@PathVariable Long meetingId) {
        return R.ok(meetingService.get(meetingId));
    }

    @Operation(summary = "新增会议")
    @SaCheckPermission("meeting:add")
    @PostMapping
    public R<Long> add(@RequestBody AmMeeting meeting) {
        return R.ok("新增成功", meetingService.add(meeting));
    }

    @Operation(summary = "修改会议")
    @SaCheckPermission("meeting:edit")
    @PutMapping
    public R<Void> update(@RequestBody AmMeeting meeting) {
        meetingService.update(meeting);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除会议")
    @SaCheckPermission("meeting:remove")
    @DeleteMapping("/{meetingId}")
    public R<Void> remove(@PathVariable Long meetingId) {
        meetingService.remove(meetingId);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "参会人员签到情况")
    @SaCheckPermission("meeting:list")
    @GetMapping("/{meetingId}/attendees")
    public R<List<AmAttendee>> attendees(@PathVariable Long meetingId) {
        return R.ok(meetingService.listAttendees(meetingId));
    }

    @Operation(summary = "保存参会人员签到情况")
    @SaCheckPermission("meeting:edit")
    @PostMapping("/{meetingId}/attendees")
    public R<Void> saveAttendees(@PathVariable Long meetingId, @RequestBody List<AmAttendee> attendees) {
        meetingService.saveAttendees(meetingId, attendees);
        return R.ok("保存成功", null);
    }
}
