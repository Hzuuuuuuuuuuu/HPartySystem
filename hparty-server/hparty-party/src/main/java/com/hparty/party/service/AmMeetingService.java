package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hparty.common.enums.MeetingType;
import com.hparty.common.exception.BizException;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.entity.AmAttendee;
import com.hparty.party.domain.entity.AmMeeting;
import com.hparty.party.mapper.AmAttendeeMapper;
import com.hparty.party.mapper.AmMeetingMapper;
import com.hparty.party.mapper.PartyLookupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 三会一课 / 主题党日 / 组织生活会 会议服务。
 *
 * <p>四类会议共用 {@code am_meeting} 表，用 {@code meeting_type} 区分 ——
 * 它们的字段结构完全一致（时间、地点、主持人、应到实到、内容、材料），
 * 分表只会带来重复代码。</p>
 */
@Service
@RequiredArgsConstructor
public class AmMeetingService {

    private final AmMeetingMapper meetingMapper;
    private final AmAttendeeMapper attendeeMapper;
    private final PartyLookupMapper lookupMapper;

    /**
     * 会议列表（可按类型过滤）。
     */
    public List<Map<String, Object>> list(String meetingType) {
        var wrapper = new LambdaQueryWrapper<AmMeeting>().orderByDesc(AmMeeting::getMeetingDate);
        // am_meeting 没有 person_id 列，personColumn 必须传 null：
        // 否则「仅本人」数据范围的用户会生成 WHERE person_id = ? 而报 Unknown column。
        // 传 null 后「仅本人」会回退为「按本组织过滤」—— 这正是会议数据的正确语义。
        DataScopeHelper.apply(wrapper, "org_id", null);
        if (StrUtil.isNotBlank(meetingType)) {
            wrapper.eq(AmMeeting::getMeetingType, meetingType);
        }

        List<AmMeeting> meetings = meetingMapper.selectList(wrapper);
        if (meetings.isEmpty()) {
            return List.of();
        }

        // 批量补组织名，避免 N+1
        List<Long> orgIds = meetings.stream()
                .map(AmMeeting::getOrgId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> orgNames = orgIds.isEmpty() ? Map.of()
                : lookupMapper.selectOrgNames(orgIds).stream().collect(Collectors.toMap(
                        m -> ((Number) m.get("org_id")).longValue(),
                        m -> String.valueOf(m.get("org_name")),
                        (a, b) -> a));

        return meetings.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("meetingId", m.getMeetingId());
            item.put("meetingType", m.getMeetingType());
            item.put("meetingTypeLabel", MeetingType.labelOf(m.getMeetingType()));
            item.put("title", m.getTitle());
            item.put("orgId", m.getOrgId());
            item.put("orgName", orgNames.get(m.getOrgId()));
            item.put("content", m.getContent());
            item.put("meetingDate", m.getMeetingDate());
            item.put("startTime", m.getStartTime());
            item.put("endTime", m.getEndTime());
            item.put("place", m.getPlace());
            item.put("hostName", m.getHostName());
            item.put("recorderName", m.getRecorderName());
            item.put("shouldAttend", m.getShouldAttend());
            item.put("actualAttend", m.getActualAttend());
            item.put("status", m.getStatus());
            return item;
        }).toList();
    }

    public AmMeeting get(Long meetingId) {
        AmMeeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BizException("会议不存在");
        }
        if (!DataScopeHelper.canAccessOrg(meeting.getOrgId())) {
            throw BizException.forbidden("无权查看其他党组织的会议");
        }
        return meeting;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(AmMeeting meeting) {
        if (StrUtil.isBlank(meeting.getMeetingType())) {
            throw new BizException("请选择会议类型");
        }
        if (StrUtil.isBlank(meeting.getTitle())) {
            throw new BizException("请填写会议标题");
        }
        meeting.setMeetingId(null);
        if (meeting.getOrgId() == null) {
            meeting.setOrgId(SecurityUtils.getOrgId());
        }
        if (meeting.getStatus() == null) {
            meeting.setStatus(0);
        }
        meetingMapper.insert(meeting);
        return meeting.getMeetingId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(AmMeeting meeting) {
        if (meeting.getMeetingId() == null) {
            throw new BizException("会议ID不能为空");
        }
        AmMeeting exists = get(meeting.getMeetingId());
        meeting.setOrgId(exists.getOrgId());
        meetingMapper.updateById(meeting);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long meetingId) {
        get(meetingId);
        meetingMapper.deleteById(meetingId);
        attendeeMapper.delete(new LambdaQueryWrapper<AmAttendee>()
                .eq(AmAttendee::getMeetingId, meetingId));
    }

    /**
     * 保存参会人员签到情况。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAttendees(Long meetingId, List<AmAttendee> attendees) {
        get(meetingId);
        attendeeMapper.delete(new LambdaQueryWrapper<AmAttendee>()
                .eq(AmAttendee::getMeetingId, meetingId));
        for (AmAttendee a : attendees) {
            a.setAttendeeId(null);
            a.setMeetingId(meetingId);
            attendeeMapper.insert(a);
        }

        // 回写实到人数
        long signed = attendees.stream()
                .filter(a -> a.getAttendStatus() != null && a.getAttendStatus() == 1)
                .count();
        AmMeeting update = new AmMeeting();
        update.setMeetingId(meetingId);
        update.setActualAttend((int) signed);
        meetingMapper.updateById(update);
    }

    public List<AmAttendee> listAttendees(Long meetingId) {
        return attendeeMapper.selectList(new LambdaQueryWrapper<AmAttendee>()
                .eq(AmAttendee::getMeetingId, meetingId));
    }
}
