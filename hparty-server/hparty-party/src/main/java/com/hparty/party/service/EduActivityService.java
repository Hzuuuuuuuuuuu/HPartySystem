package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.dto.EducationQuery;
import com.hparty.party.domain.entity.EduActivity;
import com.hparty.party.domain.entity.EduParticipant;
import com.hparty.party.enums.AttendStatusEnum;
import com.hparty.party.enums.EduActivityStatusEnum;
import com.hparty.party.enums.EduActivityTypeEnum;
import com.hparty.party.mapper.EduActivityMapper;
import com.hparty.party.mapper.EduParticipantMapper;
import com.hparty.party.mapper.PartyLookupMapper;
import com.hparty.party.util.PartyNameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 党员教育管理服务。
 *
 * <p>教育活动（{@code edu_activity}）与参与记录（{@code edu_participant}）为一对多，
 * 参与名单采用「全量替换」保存，并在保存后回写活动的实到人数。</p>
 */
@Service
@RequiredArgsConstructor
public class EduActivityService {

    private final EduActivityMapper activityMapper;
    private final EduParticipantMapper participantMapper;
    private final PartyLookupMapper lookupMapper;

    /** 分页查询教育活动。 */
    public PageResult<Map<String, Object>> page(EducationQuery query) {
        LambdaQueryWrapper<EduActivity> wrapper = new LambdaQueryWrapper<>();
        // 本表无 person_id 列，personColumn 传 null（详见 AmMeetingService 的说明）
        DataScopeHelper.apply(wrapper, "org_id", null);
        if (query.getOrgId() != null) {
            wrapper.eq(EduActivity::getOrgId, query.getOrgId());
        }
        if (query.getActivityType() != null) {
            wrapper.eq(EduActivity::getActivityType, query.getActivityType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(EduActivity::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(EduActivity::getTitle, kw)
                    .or().like(EduActivity::getTeacher, kw)
                    .or().like(EduActivity::getOrganizer, kw));
        }
        wrapper.orderByDesc(EduActivity::getStartDate).orderByDesc(EduActivity::getActivityId);

        Page<EduActivity> page = activityMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> orgNames = loadOrgNames(page.getRecords().stream()
                .map(EduActivity::getOrgId).toList());
        return PageResult.of(page, a -> toVO(a, orgNames));
    }

    /** 活动详情。 */
    public Map<String, Object> detail(Long activityId) {
        EduActivity activity = get(activityId);
        return toVO(activity, loadOrgNames(Collections.singletonList(activity.getOrgId())));
    }

    /** 按主键取活动并做越权校验。 */
    public EduActivity get(Long activityId) {
        if (activityId == null) {
            throw new BizException("教育活动ID不能为空");
        }
        EduActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException("教育活动不存在");
        }
        if (!DataScopeHelper.canAccessOrg(activity.getOrgId())) {
            throw BizException.forbidden("无权查看其他党组织的教育活动");
        }
        return activity;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(EduActivity activity) {
        if (StrUtil.isBlank(activity.getTitle())) {
            throw new BizException("请填写活动名称");
        }
        activity.setActivityId(null);
        if (activity.getOrgId() == null) {
            activity.setOrgId(SecurityUtils.getOrgId());
        }
        if (activity.getActivityType() == null) {
            activity.setActivityType(EduActivityTypeEnum.PARTY_LECTURE.getCode());
        }
        if (activity.getStatus() == null) {
            activity.setStatus(EduActivityStatusEnum.DRAFT.getCode());
        }
        if (activity.getShouldAttend() == null) {
            activity.setShouldAttend(0);
        }
        if (activity.getActualAttend() == null) {
            activity.setActualAttend(0);
        }
        activityMapper.insert(activity);
        return activity.getActivityId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(EduActivity activity) {
        if (activity.getActivityId() == null) {
            throw new BizException("教育活动ID不能为空");
        }
        EduActivity exists = get(activity.getActivityId());
        activity.setOrgId(exists.getOrgId());
        activityMapper.updateById(activity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long activityId) {
        get(activityId);
        activityMapper.deleteById(activityId);
        participantMapper.delete(new LambdaQueryWrapper<EduParticipant>()
                .eq(EduParticipant::getActivityId, activityId));
    }

    /** 某活动的参与名单。 */
    public List<EduParticipant> listParticipants(Long activityId) {
        get(activityId);
        return participantMapper.selectList(new LambdaQueryWrapper<EduParticipant>()
                .eq(EduParticipant::getActivityId, activityId)
                .orderByAsc(EduParticipant::getParticipantId));
    }

    /**
     * 保存参与名单（全量替换），并回写活动的实到人数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveParticipants(Long activityId, List<EduParticipant> participants) {
        get(activityId);
        participantMapper.delete(new LambdaQueryWrapper<EduParticipant>()
                .eq(EduParticipant::getActivityId, activityId));

        int attended = 0;
        if (participants != null && !participants.isEmpty()) {
            for (EduParticipant p : participants) {
                p.setParticipantId(null);
                p.setActivityId(activityId);
                if (p.getAttendStatus() == null) {
                    p.setAttendStatus(AttendStatusEnum.NOT_SIGNED.getCode());
                }
                if (p.getIsPassed() == null) {
                    p.setIsPassed(0);
                }
                if (StrUtil.isBlank(p.getPersonName()) && p.getPersonId() != null) {
                    p.setPersonName(lookupMapper.selectPersonName(p.getPersonId()));
                }
                participantMapper.insert(p);
                if (Objects.equals(p.getAttendStatus(), AttendStatusEnum.ATTENDED.getCode())) {
                    attended++;
                }
            }
        }

        EduActivity update = new EduActivity();
        update.setActivityId(activityId);
        update.setActualAttend(attended);
        activityMapper.updateById(update);
    }

    /**
     * 教育统计：活动总数、总学时、参与人次、按类型分布。
     */
    public Map<String, Object> statistics(Long orgId) {
        LambdaQueryWrapper<EduActivity> wrapper = new LambdaQueryWrapper<>();
        // 本表无 person_id 列，personColumn 传 null（详见 AmMeetingService 的说明）
        DataScopeHelper.apply(wrapper, "org_id", null);
        if (orgId != null) {
            wrapper.eq(EduActivity::getOrgId, orgId);
        }
        List<EduActivity> activities = activityMapper.selectList(wrapper);

        BigDecimal totalHours = activities.stream()
                .map(EduActivity::getStudyHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalParticipants = 0L;
        List<Long> activityIds = activities.stream().map(EduActivity::getActivityId).toList();
        if (!activityIds.isEmpty()) {
            Long counted = participantMapper.selectCount(new LambdaQueryWrapper<EduParticipant>()
                    .in(EduParticipant::getActivityId, activityIds));
            totalParticipants = counted == null ? 0L : counted;
        }

        Map<Integer, Long> grouped = new LinkedHashMap<>();
        for (EduActivity a : activities) {
            if (a.getActivityType() != null) {
                grouped.merge(a.getActivityType(), 1L, Long::sum);
            }
        }
        List<Map<String, Object>> byType = new ArrayList<>();
        for (EduActivityTypeEnum type : EduActivityTypeEnum.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type.getCode());
            item.put("typeLabel", type.getLabel());
            item.put("count", grouped.getOrDefault(type.getCode(), 0L));
            byType.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalActivities", (long) activities.size());
        result.put("totalHours", totalHours);
        result.put("totalParticipants", totalParticipants);
        result.put("byType", byType);
        return result;
    }

    /** 批量补组织名，避免 N+1 */
    private Map<Long, String> loadOrgNames(List<Long> orgIds) {
        List<Long> ids = orgIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : PartyNameUtils.toOrgNameMap(lookupMapper.selectOrgNames(ids));
    }

    private Map<String, Object> toVO(EduActivity a, Map<Long, String> orgNames) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("activityId", a.getActivityId());
        item.put("title", a.getTitle());
        item.put("activityType", a.getActivityType());
        item.put("activityTypeLabel", EduActivityTypeEnum.labelOf(a.getActivityType()));
        item.put("orgId", a.getOrgId());
        item.put("orgName", orgNames.get(a.getOrgId()));
        item.put("organizer", a.getOrganizer());
        item.put("startDate", a.getStartDate());
        item.put("endDate", a.getEndDate());
        item.put("studyHours", a.getStudyHours());
        item.put("place", a.getPlace());
        item.put("teacher", a.getTeacher());
        item.put("content", a.getContent());
        item.put("shouldAttend", a.getShouldAttend());
        item.put("actualAttend", a.getActualAttend());
        item.put("status", a.getStatus());
        item.put("statusLabel", EduActivityStatusEnum.labelOf(a.getStatus()));
        item.put("fileId", a.getFileId());
        item.put("fileUrl", a.getFileUrl());
        item.put("remark", a.getRemark());
        return item;
    }
}
