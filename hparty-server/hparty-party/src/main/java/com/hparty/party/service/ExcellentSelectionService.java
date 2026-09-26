package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.dto.ExcellentSelectionQuery;
import com.hparty.party.domain.entity.ExcellentCandidate;
import com.hparty.party.domain.entity.ExcellentSelection;
import com.hparty.party.enums.ExcellentResultEnum;
import com.hparty.party.enums.ExcellentStatusEnum;
import com.hparty.party.enums.ExcellentTypeEnum;
import com.hparty.party.mapper.ExcellentCandidateMapper;
import com.hparty.party.mapper.ExcellentSelectionMapper;
import com.hparty.party.mapper.PartyLookupMapper;
import com.hparty.party.util.PartyNameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 先优评选服务（优秀共产党员 / 优秀党务工作者 / 先进基层党组织）。
 *
 * <p>评选活动（{@code excellent_selection}）与候选人（{@code excellent_candidate}）
 * 为一对多，候选人既可单条增改，也可按评选整体替换。</p>
 */
@Service
@RequiredArgsConstructor
public class ExcellentSelectionService {

    private final ExcellentSelectionMapper selectionMapper;
    private final ExcellentCandidateMapper candidateMapper;
    private final PartyLookupMapper lookupMapper;

    // ------------------------------------------------------------------
    // 评选活动
    // ------------------------------------------------------------------

    /** 分页查询评选活动。 */
    public PageResult<Map<String, Object>> selectionPage(ExcellentSelectionQuery query) {
        LambdaQueryWrapper<ExcellentSelection> wrapper = new LambdaQueryWrapper<>();
        // 本表无 person_id 列，personColumn 传 null（详见 AmMeetingService 的说明）
        DataScopeHelper.apply(wrapper, "org_id", null);
        if (query.getOrgId() != null) {
            wrapper.eq(ExcellentSelection::getOrgId, query.getOrgId());
        }
        if (query.getSelectionType() != null) {
            wrapper.eq(ExcellentSelection::getSelectionType, query.getSelectionType());
        }
        if (query.getSelectionYear() != null) {
            wrapper.eq(ExcellentSelection::getSelectionYear, query.getSelectionYear());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ExcellentSelection::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(ExcellentSelection::getSelectionYear)
                .orderByDesc(ExcellentSelection::getSelectionId);

        Page<ExcellentSelection> page = selectionMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> orgNames = loadOrgNames(page.getRecords().stream()
                .map(ExcellentSelection::getOrgId).toList());
        return PageResult.of(page, s -> toVO(s, orgNames));
    }

    /** 评选详情（含候选人列表）。 */
    public Map<String, Object> selectionDetail(Long selectionId) {
        ExcellentSelection selection = getSelection(selectionId);
        Map<String, Object> vo = toVO(selection,
                loadOrgNames(Collections.singletonList(selection.getOrgId())));
        vo.put("candidates", candidateList(selectionId));
        return vo;
    }

    /** 按主键取评选活动并做越权校验。 */
    public ExcellentSelection getSelection(Long selectionId) {
        if (selectionId == null) {
            throw new BizException("评选ID不能为空");
        }
        ExcellentSelection selection = selectionMapper.selectById(selectionId);
        if (selection == null) {
            throw new BizException("评选活动不存在");
        }
        if (!DataScopeHelper.canAccessOrg(selection.getOrgId())) {
            throw BizException.forbidden("无权查看其他党组织的评选活动");
        }
        return selection;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addSelection(ExcellentSelection selection) {
        if (StrUtil.isBlank(selection.getTitle())) {
            throw new BizException("请填写评选活动名称");
        }
        selection.setSelectionId(null);
        if (selection.getOrgId() == null) {
            selection.setOrgId(SecurityUtils.getOrgId());
        }
        // 无归属组织的账号提前拦下：excellent_selection.org_id 是 NOT NULL（MySQL 1364）。
        BizException.throwIf(selection.getOrgId() == null, "当前账号未分配所属党组织，无法创建。");
        if (selection.getSelectionType() == null) {
            selection.setSelectionType(ExcellentTypeEnum.EXCELLENT_MEMBER.getCode());
        }
        if (selection.getSelectionYear() == null) {
            selection.setSelectionYear(LocalDate.now().getYear());
        }
        if (selection.getStatus() == null) {
            selection.setStatus(ExcellentStatusEnum.DRAFT.getCode());
        }
        selectionMapper.insert(selection);
        return selection.getSelectionId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSelection(ExcellentSelection selection) {
        if (selection.getSelectionId() == null) {
            throw new BizException("评选ID不能为空");
        }
        ExcellentSelection exists = getSelection(selection.getSelectionId());
        selection.setOrgId(exists.getOrgId());
        selectionMapper.updateById(selection);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeSelection(Long selectionId) {
        getSelection(selectionId);
        selectionMapper.deleteById(selectionId);
        candidateMapper.delete(new LambdaQueryWrapper<ExcellentCandidate>()
                .eq(ExcellentCandidate::getSelectionId, selectionId));
    }

    // ------------------------------------------------------------------
    // 候选人
    // ------------------------------------------------------------------

    /** 某评选下的候选人列表。 */
    public List<Map<String, Object>> candidateList(Long selectionId) {
        getSelection(selectionId);
        List<ExcellentCandidate> candidates = candidateMapper.selectList(
                new LambdaQueryWrapper<ExcellentCandidate>()
                        .eq(ExcellentCandidate::getSelectionId, selectionId)
                        .orderByAsc(ExcellentCandidate::getRankNo)
                        .orderByAsc(ExcellentCandidate::getCandidateId));
        return candidates.stream().map(this::toCandidateVO).toList();
    }

    /**
     * 新增 / 修改单个候选人。
     *
     * @return 候选人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveCandidate(ExcellentCandidate candidate) {
        if (candidate.getSelectionId() == null) {
            throw new BizException("请指定评选活动");
        }
        ExcellentSelection selection = getSelection(candidate.getSelectionId());

        if (candidate.getCandidateId() == null) {
            fillCandidateDefaults(candidate, selection);
            candidateMapper.insert(candidate);
            return candidate.getCandidateId();
        }

        ExcellentCandidate exists = candidateMapper.selectById(candidate.getCandidateId());
        if (exists == null) {
            throw new BizException("候选人不存在");
        }
        candidate.setSelectionId(exists.getSelectionId());
        candidateMapper.updateById(candidate);
        return candidate.getCandidateId();
    }

    /**
     * 批量保存某评选的候选人（全量替换）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveCandidates(Long selectionId, List<ExcellentCandidate> candidates) {
        ExcellentSelection selection = getSelection(selectionId);
        candidateMapper.delete(new LambdaQueryWrapper<ExcellentCandidate>()
                .eq(ExcellentCandidate::getSelectionId, selectionId));
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (ExcellentCandidate c : candidates) {
            c.setCandidateId(null);
            c.setSelectionId(selectionId);
            fillCandidateDefaults(c, selection);
            candidateMapper.insert(c);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeCandidate(Long candidateId) {
        if (candidateId == null) {
            throw new BizException("候选人ID不能为空");
        }
        ExcellentCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BizException("候选人不存在");
        }
        // 候选人自身没有可靠的组织归属（个人类评选可能为空），按所属评选活动校验
        getSelection(candidate.getSelectionId());
        candidateMapper.deleteById(candidateId);
    }

    private void fillCandidateDefaults(ExcellentCandidate candidate, ExcellentSelection selection) {
        if (candidate.getVotes() == null) {
            candidate.setVotes(0);
        }
        if (candidate.getResult() == null) {
            candidate.setResult(ExcellentResultEnum.PENDING.getCode());
        }
        if (candidate.getRecommendOrgId() == null) {
            candidate.setRecommendOrgId(SecurityUtils.getLoginUser().getOrgId());
        }
        if (StrUtil.isBlank(candidate.getPersonName()) && candidate.getPersonId() != null) {
            candidate.setPersonName(lookupMapper.selectPersonName(candidate.getPersonId()));
        }
        if (StrUtil.isBlank(candidate.getOrgName()) && candidate.getOrgId() != null) {
            candidate.setOrgName(lookupMapper.selectOrgName(candidate.getOrgId()));
        }
        if (StrUtil.isBlank(candidate.getPersonName()) && StrUtil.isBlank(candidate.getOrgName())
                && !Objects.equals(selection.getSelectionType(), ExcellentTypeEnum.ADVANCED_ORG.getCode())) {
            // 组织类评选可以只填候选组织，个人类评选必须选到人
            throw new BizException("请选择候选人");
        }
    }

    /** 批量补组织名，避免 N+1 */
    private Map<Long, String> loadOrgNames(List<Long> orgIds) {
        List<Long> ids = orgIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : PartyNameUtils.toOrgNameMap(lookupMapper.selectOrgNames(ids));
    }

    private Map<String, Object> toCandidateVO(ExcellentCandidate c) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("candidateId", c.getCandidateId());
        item.put("selectionId", c.getSelectionId());
        item.put("personId", c.getPersonId());
        item.put("personName", c.getPersonName());
        item.put("orgId", c.getOrgId());
        item.put("orgName", StrUtil.isNotBlank(c.getOrgName()) ? c.getOrgName()
                : (c.getOrgId() == null ? null : lookupMapper.selectOrgName(c.getOrgId())));
        item.put("recommendOrgId", c.getRecommendOrgId());
        item.put("deeds", c.getDeeds());
        item.put("votes", c.getVotes());
        item.put("rankNo", c.getRankNo());
        item.put("result", c.getResult());
        item.put("resultLabel", ExcellentResultEnum.labelOf(c.getResult()));
        item.put("remark", c.getRemark());
        return item;
    }

    private Map<String, Object> toVO(ExcellentSelection s, Map<Long, String> orgNames) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("selectionId", s.getSelectionId());
        item.put("title", s.getTitle());
        item.put("selectionType", s.getSelectionType());
        item.put("selectionTypeLabel", ExcellentTypeEnum.labelOf(s.getSelectionType()));
        item.put("orgId", s.getOrgId());
        item.put("orgName", orgNames.get(s.getOrgId()));
        item.put("selectionYear", s.getSelectionYear());
        item.put("startDate", s.getStartDate());
        item.put("endDate", s.getEndDate());
        item.put("quota", s.getQuota());
        item.put("status", s.getStatus());
        item.put("statusLabel", ExcellentStatusEnum.labelOf(s.getStatus()));
        item.put("description", s.getDescription());
        item.put("fileId", s.getFileId());
        item.put("fileUrl", s.getFileUrl());
        item.put("remark", s.getRemark());
        return item;
    }
}
