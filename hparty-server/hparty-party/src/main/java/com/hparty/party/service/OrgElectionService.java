package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.dto.ElectionQuery;
import com.hparty.party.domain.entity.OrgElection;
import com.hparty.party.domain.entity.OrgElectionCandidate;
import com.hparty.party.enums.ElectionStatusEnum;
import com.hparty.party.enums.ElectionTypeEnum;
import com.hparty.party.mapper.OrgElectionCandidateMapper;
import com.hparty.party.mapper.OrgElectionMapper;
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
 * 党组织换届选举服务。
 *
 * <p>换届记录（{@code org_election}）与候选人（{@code org_election_candidate}）
 * 是一对多关系，候选人以「全量替换」的方式保存——支部调整候选人名单时
 * 前端整表提交，后端先清后插，避免逐条 diff 带来的状态不一致。</p>
 *
 * <p><strong>数据权限</strong>：列表查询走 {@link DataScopeHelper#apply}，
 * 详情 / 修改 / 删除走 {@link DataScopeHelper#canAccessOrg(Long)}。</p>
 */
@Service
@RequiredArgsConstructor
public class OrgElectionService {

    private final OrgElectionMapper electionMapper;
    private final OrgElectionCandidateMapper candidateMapper;
    private final PartyLookupMapper lookupMapper;

    /**
     * 分页查询换届记录。
     */
    public PageResult<Map<String, Object>> page(ElectionQuery query) {
        LambdaQueryWrapper<OrgElection> wrapper = new LambdaQueryWrapper<>();
        // 本表无 person_id 列，personColumn 传 null（详见 AmMeetingService 的说明）
        DataScopeHelper.apply(wrapper, "org_id", null);
        if (query.getOrgId() != null) {
            wrapper.eq(OrgElection::getOrgId, query.getOrgId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(OrgElection::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(OrgElection::getTitle, kw).or().like(OrgElection::getReason, kw));
        }
        wrapper.orderByDesc(OrgElection::getPlanDate).orderByDesc(OrgElection::getElectionId);

        Page<OrgElection> page = electionMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> orgNames = loadOrgNames(page.getRecords().stream()
                .map(OrgElection::getOrgId).toList());
        return PageResult.of(page, e -> toVO(e, orgNames));
    }

    /** 换届详情（含候选人列表）。 */
    public Map<String, Object> detail(Long electionId) {
        OrgElection election = get(electionId);
        Map<String, Object> vo = toVO(election,
                loadOrgNames(Collections.singletonList(election.getOrgId())));
        vo.put("candidates", candidateMapper.selectList(new LambdaQueryWrapper<OrgElectionCandidate>()
                .eq(OrgElectionCandidate::getElectionId, electionId)
                .orderByAsc(OrgElectionCandidate::getCandidateId)));
        return vo;
    }

    /** 按主键取换届记录并做越权校验。 */
    public OrgElection get(Long electionId) {
        if (electionId == null) {
            throw new BizException("换届ID不能为空");
        }
        OrgElection election = electionMapper.selectById(electionId);
        if (election == null) {
            throw new BizException("换届记录不存在");
        }
        if (!DataScopeHelper.canAccessOrg(election.getOrgId())) {
            throw BizException.forbidden("无权查看其他党组织的换届记录");
        }
        return election;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(OrgElection election) {
        if (StrUtil.isBlank(election.getTitle())) {
            throw new BizException("请填写换届名称");
        }
        election.setElectionId(null);
        if (election.getOrgId() == null) {
            election.setOrgId(SecurityUtils.getOrgId());
        }
        if (election.getElectionType() == null) {
            election.setElectionType(ElectionTypeEnum.REGULAR.getCode());
        }
        if (election.getStatus() == null) {
            election.setStatus(ElectionStatusEnum.PREPARING.getCode());
        }
        if (election.getShouldAttend() == null) {
            election.setShouldAttend(0);
        }
        if (election.getActualAttend() == null) {
            election.setActualAttend(0);
        }
        if (election.getQuorumRequired() == null) {
            election.setQuorumRequired(0);
        }
        if (election.getIsQuorumMet() == null) {
            election.setIsQuorumMet(0);
        }
        electionMapper.insert(election);
        return election.getElectionId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(OrgElection election) {
        if (election.getElectionId() == null) {
            throw new BizException("换届ID不能为空");
        }
        OrgElection exists = get(election.getElectionId());
        // 组织归属不允许通过修改接口变更
        election.setOrgId(exists.getOrgId());
        electionMapper.updateById(election);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long electionId) {
        get(electionId);
        electionMapper.deleteById(electionId);
        candidateMapper.delete(new LambdaQueryWrapper<OrgElectionCandidate>()
                .eq(OrgElectionCandidate::getElectionId, electionId));
    }

    /** 某次换届的候选人列表。 */
    public List<OrgElectionCandidate> listCandidates(Long electionId) {
        get(electionId);
        return candidateMapper.selectList(new LambdaQueryWrapper<OrgElectionCandidate>()
                .eq(OrgElectionCandidate::getElectionId, electionId)
                .orderByAsc(OrgElectionCandidate::getCandidateId));
    }

    /**
     * 保存候选人名单（全量替换）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveCandidates(Long electionId, List<OrgElectionCandidate> candidates) {
        get(electionId);
        candidateMapper.delete(new LambdaQueryWrapper<OrgElectionCandidate>()
                .eq(OrgElectionCandidate::getElectionId, electionId));
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (OrgElectionCandidate c : candidates) {
            if (c.getPersonId() == null) {
                throw new BizException("请选择候选人");
            }
            if (StrUtil.isBlank(c.getPositionCode()) || StrUtil.isBlank(c.getPositionName())) {
                throw new BizException("请选择候选职务");
            }
            c.setCandidateId(null);
            c.setElectionId(electionId);
            if (c.getIsIncumbent() == null) {
                c.setIsIncumbent(0);
            }
            if (c.getVotes() == null) {
                c.setVotes(0);
            }
            if (c.getIsElected() == null) {
                c.setIsElected(0);
            }
            if (StrUtil.isBlank(c.getPersonName()) && c.getPersonId() != null) {
                c.setPersonName(lookupMapper.selectPersonName(c.getPersonId()));
            }
            candidateMapper.insert(c);
        }
    }

    /** 批量补组织名，避免 N+1 */
    private Map<Long, String> loadOrgNames(List<Long> orgIds) {
        List<Long> ids = orgIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : PartyNameUtils.toOrgNameMap(lookupMapper.selectOrgNames(ids));
    }

    private Map<String, Object> toVO(OrgElection e, Map<Long, String> orgNames) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("electionId", e.getElectionId());
        item.put("orgId", e.getOrgId());
        item.put("orgName", orgNames.get(e.getOrgId()));
        item.put("electionType", e.getElectionType());
        item.put("electionTypeLabel", ElectionTypeEnum.labelOf(e.getElectionType()));
        item.put("termNo", e.getTermNo());
        item.put("title", e.getTitle());
        item.put("reason", e.getReason());
        item.put("planDate", e.getPlanDate());
        item.put("electionDate", e.getElectionDate());
        item.put("place", e.getPlace());
        item.put("hostId", e.getHostId());
        item.put("hostName", e.getHostName());
        item.put("recorderName", e.getRecorderName());
        item.put("shouldAttend", e.getShouldAttend());
        item.put("actualAttend", e.getActualAttend());
        item.put("quorumRequired", e.getQuorumRequired());
        item.put("isQuorumMet", e.getIsQuorumMet());
        item.put("status", e.getStatus());
        item.put("statusLabel", ElectionStatusEnum.labelOf(e.getStatus()));
        item.put("resultSummary", e.getResultSummary());
        item.put("approveOrgId", e.getApproveOrgId());
        item.put("approveDate", e.getApproveDate());
        item.put("fileId", e.getFileId());
        item.put("fileUrl", e.getFileUrl());
        item.put("remark", e.getRemark());
        return item;
    }
}
