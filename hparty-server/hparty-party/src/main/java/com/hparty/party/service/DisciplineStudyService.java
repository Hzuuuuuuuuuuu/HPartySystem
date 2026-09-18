package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.dto.DisciplineQuery;
import com.hparty.party.domain.entity.DisciplineParticipant;
import com.hparty.party.domain.entity.DisciplineStudy;
import com.hparty.party.enums.AttendStatusEnum;
import com.hparty.party.enums.DisciplineStatusEnum;
import com.hparty.party.enums.DisciplineStudyTypeEnum;
import com.hparty.party.mapper.DisciplineParticipantMapper;
import com.hparty.party.mapper.DisciplineStudyMapper;
import com.hparty.party.mapper.PartyLookupMapper;
import com.hparty.party.util.PartyNameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 党纪学习教育服务。
 *
 * <p>学习活动（{@code discipline_study}）与参与记录（{@code discipline_participant}）
 * 为一对多，参与名单全量替换保存，并回写参加人数与测试通过人数。</p>
 */
@Service
@RequiredArgsConstructor
public class DisciplineStudyService {

    private final DisciplineStudyMapper studyMapper;
    private final DisciplineParticipantMapper participantMapper;
    private final PartyLookupMapper lookupMapper;

    /** 分页查询党纪学习。 */
    public PageResult<Map<String, Object>> page(DisciplineQuery query) {
        LambdaQueryWrapper<DisciplineStudy> wrapper = new LambdaQueryWrapper<>();
        // 本表无 person_id 列，personColumn 传 null（详见 AmMeetingService 的说明）
        DataScopeHelper.apply(wrapper, "org_id", null);
        if (query.getOrgId() != null) {
            wrapper.eq(DisciplineStudy::getOrgId, query.getOrgId());
        }
        if (query.getStudyType() != null) {
            wrapper.eq(DisciplineStudy::getStudyType, query.getStudyType());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(DisciplineStudy::getTitle, kw)
                    .or().like(DisciplineStudy::getTeacher, kw));
        }
        wrapper.orderByDesc(DisciplineStudy::getStudyDate).orderByDesc(DisciplineStudy::getStudyId);

        Page<DisciplineStudy> page = studyMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> orgNames = loadOrgNames(page.getRecords().stream()
                .map(DisciplineStudy::getOrgId).toList());
        return PageResult.of(page, s -> toVO(s, orgNames));
    }

    /** 学习详情。 */
    public Map<String, Object> detail(Long studyId) {
        DisciplineStudy study = get(studyId);
        return toVO(study, loadOrgNames(Collections.singletonList(study.getOrgId())));
    }

    /** 按主键取学习记录并做越权校验。 */
    public DisciplineStudy get(Long studyId) {
        if (studyId == null) {
            throw new BizException("党纪学习ID不能为空");
        }
        DisciplineStudy study = studyMapper.selectById(studyId);
        if (study == null) {
            throw new BizException("党纪学习记录不存在");
        }
        if (!DataScopeHelper.canAccessOrg(study.getOrgId())) {
            throw BizException.forbidden("无权查看其他党组织的党纪学习记录");
        }
        return study;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(DisciplineStudy study) {
        if (StrUtil.isBlank(study.getTitle())) {
            throw new BizException("请填写学习主题");
        }
        study.setStudyId(null);
        if (study.getOrgId() == null) {
            study.setOrgId(SecurityUtils.getOrgId());
        }
        if (study.getStudyType() == null) {
            study.setStudyType(DisciplineStudyTypeEnum.REGULATION.getCode());
        }
        if (study.getStatus() == null) {
            study.setStatus(DisciplineStatusEnum.DRAFT.getCode());
        }
        if (study.getParticipantCount() == null) {
            study.setParticipantCount(0);
        }
        if (study.getPassCount() == null) {
            study.setPassCount(0);
        }
        studyMapper.insert(study);
        return study.getStudyId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(DisciplineStudy study) {
        if (study.getStudyId() == null) {
            throw new BizException("党纪学习ID不能为空");
        }
        DisciplineStudy exists = get(study.getStudyId());
        study.setOrgId(exists.getOrgId());
        studyMapper.updateById(study);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long studyId) {
        get(studyId);
        studyMapper.deleteById(studyId);
        participantMapper.delete(new LambdaQueryWrapper<DisciplineParticipant>()
                .eq(DisciplineParticipant::getStudyId, studyId));
    }

    /** 某次学习的参与名单。 */
    public List<DisciplineParticipant> listParticipants(Long studyId) {
        get(studyId);
        return participantMapper.selectList(new LambdaQueryWrapper<DisciplineParticipant>()
                .eq(DisciplineParticipant::getStudyId, studyId)
                .orderByAsc(DisciplineParticipant::getParticipantId));
    }

    /**
     * 保存参与名单（全量替换），并回写参加人数、测试通过人数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveParticipants(Long studyId, List<DisciplineParticipant> participants) {
        get(studyId);
        participantMapper.delete(new LambdaQueryWrapper<DisciplineParticipant>()
                .eq(DisciplineParticipant::getStudyId, studyId));

        int total = 0;
        int passed = 0;
        if (participants != null && !participants.isEmpty()) {
            for (DisciplineParticipant p : participants) {
                p.setParticipantId(null);
                p.setStudyId(studyId);
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
                total++;
                if (Objects.equals(p.getIsPassed(), 1)) {
                    passed++;
                }
            }
        }

        DisciplineStudy update = new DisciplineStudy();
        update.setStudyId(studyId);
        update.setParticipantCount(total);
        update.setPassCount(passed);
        studyMapper.updateById(update);
    }

    /** 批量补组织名，避免 N+1 */
    private Map<Long, String> loadOrgNames(List<Long> orgIds) {
        List<Long> ids = orgIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : PartyNameUtils.toOrgNameMap(lookupMapper.selectOrgNames(ids));
    }

    private Map<String, Object> toVO(DisciplineStudy s, Map<Long, String> orgNames) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("studyId", s.getStudyId());
        item.put("title", s.getTitle());
        item.put("studyType", s.getStudyType());
        item.put("studyTypeLabel", DisciplineStudyTypeEnum.labelOf(s.getStudyType()));
        item.put("orgId", s.getOrgId());
        item.put("orgName", orgNames.get(s.getOrgId()));
        item.put("studyDate", s.getStudyDate());
        item.put("place", s.getPlace());
        item.put("teacher", s.getTeacher());
        item.put("content", s.getContent());
        item.put("participantCount", s.getParticipantCount());
        item.put("passCount", s.getPassCount());
        item.put("status", s.getStatus());
        item.put("statusLabel", DisciplineStatusEnum.labelOf(s.getStatus()));
        item.put("fileId", s.getFileId());
        item.put("fileUrl", s.getFileUrl());
        item.put("remark", s.getRemark());
        return item;
    }
}
