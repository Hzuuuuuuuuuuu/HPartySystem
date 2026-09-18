package com.hparty.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.PageResult;
import com.hparty.common.enums.MemberStatus;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.system.domain.dto.PartyPersonDTO;
import com.hparty.system.domain.dto.PartyPersonQuery;
import com.hparty.system.domain.entity.PartyGroup;
import com.hparty.system.domain.entity.PartyMemberProfile;
import com.hparty.system.domain.entity.PartyPerson;
import com.hparty.system.domain.entity.PartyPosition;
import com.hparty.system.domain.entity.SysDept;
import com.hparty.system.domain.vo.PartyPersonDetailVO;
import com.hparty.system.domain.vo.PartyPersonStatVO;
import com.hparty.system.domain.vo.PartyPersonVO;
import com.hparty.system.mapper.PartyGroupMapper;
import com.hparty.system.mapper.PartyMemberProfileMapper;
import com.hparty.system.mapper.PartyPersonMapper;
import com.hparty.system.mapper.PartyPositionMapper;
import com.hparty.system.mapper.SysDeptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 人员档案服务。
 * <p>一张 {@code party_person} 表打通「群众 → 入党申请人 → 入党积极分子 → 发展对象 → 预备党员 → 正式党员」
 * 全生命周期，本服务负责档案的增删改查、按组织/党员维度检索以及统计。</p>
 * <p><strong>数据权限</strong>：所有列表/统计查询都会调用
 * {@link DataScopeHelper#apply(QueryWrapper)} 追加组织范围条件；按主键的详情、修改、删除
 * 则通过 {@link DataScopeHelper#canAccessOrg(Long)} 做越权校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyPersonService {

    /** 身份证号：15 位数字，或 18 位数字（末位可为 X） */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(^\\d{15}$)|(^\\d{17}[0-9Xx]$)");

    /** 手机号：11 位大陆手机号 */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 固定电话：区号-号码，横线可省略 */
    private static final Pattern LANDLINE_PATTERN = Pattern.compile("^0\\d{2,3}-?\\d{7,8}(-\\d{1,6})?$");

    private final PartyPersonMapper personMapper;

    private final PartyMemberProfileMapper memberProfileMapper;

    private final PartyPositionMapper positionMapper;

    private final PartyGroupMapper groupMapper;

    private final SysDeptMapper deptMapper;

    /**
     * 分页查询人员档案。
     * <p>支持姓名 / 电话 / 身份证号模糊匹配，组织、人员状态、政治面貌、党员标识等精确过滤，
     * 并按数据权限裁剪可见范围。</p>
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<PartyPersonVO> page(PartyPersonQuery query) {
        Page<PartyPerson> page = personMapper.selectPage(PageUtils.toPage(query), buildWrapper(query, false));
        return new PageResult<>(toVOList(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 分页查询党员名册（只查 {@code is_member = 1}）。
     *
     * @param query 查询条件，orgId / memberStatus 等过滤同样生效
     * @return 分页结果
     */
    public PageResult<PartyPersonVO> listMembers(PartyPersonQuery query) {
        Page<PartyPerson> page = personMapper.selectPage(PageUtils.toPage(query), buildWrapper(query, true));
        return new PageResult<>(toVOList(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 人员档案详情：基础档案 + 党员扩展信息 + 党内职务任职记录 + 所属党小组 / 党组织名称。
     *
     * @param personId 人员ID
     * @return 详情
     */
    public PartyPersonDetailVO getPerson(Long personId) {
        BizException.throwIf(personId == null, "人员ID不能为空");
        PartyPerson person = personMapper.selectById(personId);
        BizException.throwIf(person == null, "人员档案不存在");
        checkPersonAccess(person.getOrgId(), person.getPersonId());

        PartyPersonDetailVO vo = new PartyPersonDetailVO();
        BeanUtils.copyProperties(PartyPersonVO.of(person), vo);
        vo.setOrgName(loadOrgName(person.getOrgId()));
        vo.setGroupName(loadGroupName(person.getGroupId()));
        vo.setMemberProfile(loadMemberProfile(personId));
        vo.setPositions(loadPositions(personId));
        return vo;
    }

    /**
     * 按组织查询人员列表（不分页，用于花名册、导出等场景）。
     *
     * @param orgId 组织ID
     * @return 人员列表，按姓名升序
     */
    public List<PartyPersonVO> listByOrg(Long orgId) {
        BizException.throwIf(orgId == null, "组织ID不能为空");
        checkOrgAccess(orgId);

        QueryWrapper<PartyPerson> wrapper = new QueryWrapper<>();
        wrapper.eq("org_id", orgId).orderByAsc("name");
        return toVOList(personMapper.selectList(wrapper));
    }

    /**
     * 人员统计：总人数、各人员状态人数、正式党员数、预备党员数等。
     * <p>供前端「党组织基本情况」页面使用，统计范围受数据权限限制。</p>
     *
     * @return 统计结果
     */
    public PartyPersonStatVO getStatistics() {
        PartyPersonStatVO vo = new PartyPersonStatVO();

        // 一次分组查询即可拿到各人员状态人数，避免逐状态 count
        QueryWrapper<PartyPerson> groupWrapper = new QueryWrapper<>();
        groupWrapper.select("member_status AS member_status", "COUNT(*) AS cnt").groupBy("member_status");
        DataScopeHelper.apply(groupWrapper);

        Map<Integer, Long> counts = new HashMap<>();
        long total = 0L;
        for (Map<String, Object> row : personMapper.selectMaps(groupWrapper)) {
            int status = row.get("member_status") instanceof Number num ? num.intValue() : 0;
            long count = row.get("cnt") instanceof Number num ? num.longValue() : 0L;
            counts.merge(status, count, Long::sum);
            total += count;
        }

        List<PartyPersonStatVO.StatusCount> statusCounts = new ArrayList<>();
        for (MemberStatus status : MemberStatus.values()) {
            long count = counts.getOrDefault(status.getCode(), 0L);
            statusCounts.add(new PartyPersonStatVO.StatusCount(status.getCode(), status.getLabel(), count));
        }

        vo.setTotal(total);
        vo.setStatusCounts(statusCounts);
        vo.setMassCount(counts.getOrDefault(MemberStatus.MASS.getCode(), 0L));
        vo.setApplicantCount(counts.getOrDefault(MemberStatus.APPLICANT.getCode(), 0L));
        vo.setActivistCount(counts.getOrDefault(MemberStatus.ACTIVIST.getCode(), 0L));
        vo.setCandidateCount(counts.getOrDefault(MemberStatus.CANDIDATE.getCode(), 0L));
        vo.setProbationaryCount(counts.getOrDefault(MemberStatus.PROBATIONARY.getCode(), 0L));
        vo.setFullMemberCount(counts.getOrDefault(MemberStatus.FULL_MEMBER.getCode(), 0L));
        vo.setFlowingCount(counts.getOrDefault(MemberStatus.FLOWING.getCode(), 0L));

        // 党员总数单独按 is_member 统计，不与人员状态口径耦合
        QueryWrapper<PartyPerson> memberWrapper = new QueryWrapper<>();
        memberWrapper.eq("is_member", Constants.YES);
        DataScopeHelper.apply(memberWrapper);
        Long memberCount = personMapper.selectCount(memberWrapper);
        vo.setMemberCount(memberCount == null ? 0L : memberCount);

        // 党组织数：口径与上面一致，都按当前用户可见的组织范围统计
        QueryWrapper<SysDept> orgWrapper = new QueryWrapper<>();
        DataScopeHelper.apply(orgWrapper);
        Long orgCount = deptMapper.selectCount(orgWrapper);
        vo.setOrgCount(orgCount == null ? 0L : orgCount);
        return vo;
    }

    /**
     * 新增人员档案。
     * <p>校验：姓名与所属党组织必填；身份证号非空时全局唯一且格式合法；手机号格式合法；
     * 当前用户需有权限操作该组织。</p>
     *
     * @param dto 人员信息，可选携带党员扩展信息
     * @return 新增的人员ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addPerson(PartyPersonDTO dto) {
        validate(dto);
        checkOrgAccess(dto.getOrgId());
        checkIdCardUnique(dto.getIdCard(), null);

        PartyPerson person = new PartyPerson();
        BeanUtils.copyProperties(dto, person, "memberProfile");
        person.setPersonId(null);
        normalize(person, true);
        try {
            personMapper.insert(person);
        } catch (DuplicateKeyException e) {
            // 上面的 checkIdCardUnique 是「先查后插」，并发下两个请求可能同时通过检查，
            // 最终由 uk_person_id_card 唯一索引兜底：这里把数据库异常翻译成友好提示
            throw new BizException("身份证号已存在：" + person.getIdCard());
        }

        saveMemberProfile(person.getPersonId(), dto.getMemberProfile());
        log.info("新增人员档案：personId={} name={}", person.getPersonId(), person.getName());
        return person.getPersonId();
    }

    /**
     * 修改人员档案。
     * <p>校验同新增；改前会校验原档案所属组织的操作权限。</p>
     *
     * @param dto 人员信息，personId 必填
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePerson(PartyPersonDTO dto) {
        BizException.throwIf(dto == null || dto.getPersonId() == null, "人员ID不能为空");
        PartyPerson old = personMapper.selectById(dto.getPersonId());
        BizException.throwIf(old == null, "人员档案不存在");
        checkPersonAccess(old.getOrgId(), old.getPersonId());
        checkPersonAccess(dto.getOrgId(), dto.getPersonId());

        validate(dto);
        checkIdCardUnique(dto.getIdCard(), dto.getPersonId());

        PartyPerson person = new PartyPerson();
        BeanUtils.copyProperties(dto, person, "memberProfile");
        normalize(person, false);
        boolean success;
        try {
            success = personMapper.updateById(person) > 0;
        } catch (DuplicateKeyException e) {
            // 并发下把身份证号改成别人的号码，由唯一索引兜底
            throw new BizException("身份证号已存在：" + person.getIdCard());
        }

        if (success) {
            saveMemberProfile(person.getPersonId(), dto.getMemberProfile());
            log.info("修改人员档案：personId={}", person.getPersonId());
        }
        return success;
    }

    /**
     * 删除人员档案。
     * <p>校验：该人员若存在进行中的发展党员流程（{@code dev_applicant.status = 1}）则不允许删除；
     * 删除时一并清理党员扩展信息与党内职务记录。</p>
     *
     * @param personId 人员ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean removePerson(Long personId) {
        BizException.throwIf(personId == null, "人员ID不能为空");
        PartyPerson person = personMapper.selectById(personId);
        BizException.throwIf(person == null, "人员档案不存在");
        checkPersonAccess(person.getOrgId(), person.getPersonId());

        BizException.throwIf(personMapper.countRunningFlow(personId) > 0,
                "该人员存在进行中的发展党员流程，请先终止流程后再删除");

        memberProfileMapper.delete(new QueryWrapper<PartyMemberProfile>().eq("person_id", personId));
        positionMapper.delete(new QueryWrapper<PartyPosition>().eq("person_id", personId));

        boolean success = personMapper.deleteById(personId) > 0;
        if (success) {
            log.info("删除人员档案：personId={} name={}", personId, person.getName());
        }
        return success;
    }

    // ==================== 私有方法 ====================

    /**
     * 构造查询条件。
     *
     * @param query      查询条件
     * @param onlyMember true=只查党员（is_member = 1），此时忽略 query.isMember
     */
    private QueryWrapper<PartyPerson> buildWrapper(PartyPersonQuery query, boolean onlyMember) {
        QueryWrapper<PartyPerson> wrapper = new QueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getName()), "name", query.getName());
        wrapper.like(StringUtils.hasText(query.getPhone()), "phone", query.getPhone());
        wrapper.like(StringUtils.hasText(query.getIdCard()), "id_card", query.getIdCard());
        wrapper.eq(query.getOrgId() != null, "org_id", query.getOrgId());
        wrapper.eq(query.getGroupId() != null, "group_id", query.getGroupId());
        wrapper.eq(query.getMemberStatus() != null, "member_status", query.getMemberStatus());
        wrapper.eq(StringUtils.hasText(query.getPoliticalStatus()), "political_status", query.getPoliticalStatus());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());
        if (onlyMember) {
            wrapper.eq("is_member", Constants.YES);
        } else {
            wrapper.eq(query.getIsMember() != null, "is_member", query.getIsMember());
        }

        // 数据权限：只查询当前用户可见组织范围内的人员
        DataScopeHelper.apply(wrapper);

        if (StringUtils.hasText(query.getOrderByColumn())) {
            PageUtils.applyOrder(wrapper, query);
        } else {
            wrapper.orderByDesc("person_id");
        }
        return wrapper;
    }

    /** 实体列表转 VO 列表，并批量补全组织/党小组名称（避免 N+1） */
    private List<PartyPersonVO> toVOList(List<PartyPerson> persons) {
        if (persons == null || persons.isEmpty()) {
            return List.of();
        }
        List<Long> orgIds = new ArrayList<>(persons.size());
        List<Long> groupIds = new ArrayList<>(persons.size());
        for (PartyPerson person : persons) {
            orgIds.add(person.getOrgId());
            groupIds.add(person.getGroupId());
        }
        Map<Long, String> orgNames = loadOrgNames(orgIds);
        Map<Long, String> groupNames = loadGroupNames(groupIds);

        List<PartyPersonVO> list = new ArrayList<>(persons.size());
        for (PartyPerson person : persons) {
            PartyPersonVO vo = PartyPersonVO.of(person);
            vo.setOrgName(orgNames.get(person.getOrgId()));
            vo.setGroupName(groupNames.get(person.getGroupId()));
            list.add(vo);
        }
        return list;
    }

    /** 批量查询组织名称 */
    private Map<Long, String> loadOrgNames(Collection<Long> orgIds) {
        Set<Long> ids = distinctIds(orgIds);
        Map<Long, String> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<SysDept> depts = deptMapper.selectBatchIds(ids);
        if (depts == null) {
            return result;
        }
        for (SysDept dept : depts) {
            if (dept.getOrgId() != null) {
                result.putIfAbsent(dept.getOrgId(), dept.getOrgName() == null ? "" : dept.getOrgName());
            }
        }
        return result;
    }

    /** 批量查询党小组名称 */
    private Map<Long, String> loadGroupNames(Collection<Long> groupIds) {
        Set<Long> ids = distinctIds(groupIds);
        Map<Long, String> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<PartyGroup> groups = groupMapper.selectBatchIds(ids);
        if (groups == null) {
            return result;
        }
        for (PartyGroup group : groups) {
            if (group.getGroupId() != null) {
                result.putIfAbsent(group.getGroupId(), group.getGroupName() == null ? "" : group.getGroupName());
            }
        }
        return result;
    }

    /** 过滤空值并去重 */
    private Set<Long> distinctIds(Collection<Long> ids) {
        Set<Long> result = new HashSet<>();
        if (ids == null) {
            return result;
        }
        for (Long id : ids) {
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    /** 查询组织名称 */
    private String loadOrgName(Long orgId) {
        if (orgId == null) {
            return null;
        }
        SysDept dept = deptMapper.selectById(orgId);
        return dept == null ? null : dept.getOrgName();
    }

    /** 查询党小组名称 */
    private String loadGroupName(Long groupId) {
        if (groupId == null) {
            return null;
        }
        PartyGroup group = groupMapper.selectById(groupId);
        return group == null ? null : group.getGroupName();
    }

    /** 加载党员扩展信息（一人一条） */
    private PartyMemberProfile loadMemberProfile(Long personId) {
        QueryWrapper<PartyMemberProfile> wrapper = new QueryWrapper<>();
        wrapper.eq("person_id", personId);
        return memberProfileMapper.selectList(wrapper).stream().findFirst().orElse(null);
    }

    /** 加载党内职务任职记录，现任在前 */
    private List<PartyPosition> loadPositions(Long personId) {
        QueryWrapper<PartyPosition> wrapper = new QueryWrapper<>();
        wrapper.eq("person_id", personId);
        wrapper.orderByDesc("is_current").orderByDesc("start_date");
        return positionMapper.selectList(wrapper);
    }

    /** 新增或更新党员扩展信息 */
    private void saveMemberProfile(Long personId, PartyMemberProfile profile) {
        if (profile == null || personId == null) {
            return;
        }
        PartyMemberProfile exist = loadMemberProfile(personId);
        profile.setPersonId(personId);
        if (exist == null) {
            profile.setProfileId(null);
            memberProfileMapper.insert(profile);
        } else {
            profile.setProfileId(exist.getProfileId());
            memberProfileMapper.updateById(profile);
        }
    }

    /** 基础校验：必填项、身份证号格式、手机号格式 */
    private void validate(PartyPersonDTO dto) {
        BizException.throwIf(dto == null, "人员信息不能为空");
        BizException.throwIf(!StringUtils.hasText(dto.getName()), "姓名不能为空");
        BizException.throwIf(dto.getOrgId() == null, "所属党组织不能为空");

        if (StringUtils.hasText(dto.getIdCard()) && !ID_CARD_PATTERN.matcher(dto.getIdCard().trim()).matches()) {
            throw new BizException("身份证号格式不正确");
        }
        if (StringUtils.hasText(dto.getPhone())) {
            String phone = dto.getPhone().trim();
            if (!MOBILE_PATTERN.matcher(phone).matches() && !LANDLINE_PATTERN.matcher(phone).matches()) {
                throw new BizException("手机号格式不正确");
            }
        }
    }

    /** 身份证号唯一校验（非空时生效） */
    private void checkIdCardUnique(String idCard, Long excludePersonId) {
        if (!StringUtils.hasText(idCard)) {
            return;
        }
        QueryWrapper<PartyPerson> wrapper = new QueryWrapper<>();
        wrapper.eq("id_card", idCard.trim());
        wrapper.ne(excludePersonId != null, "person_id", excludePersonId);
        BizException.throwIf(personMapper.selectCount(wrapper) > 0, "身份证号已存在：" + idCard);
    }

    /** 越权校验：当前用户能否操作该组织的数据 */
    private void checkOrgAccess(Long orgId) {
        BizException.throwForbiddenIf(!DataScopeHelper.canAccessOrg(orgId), "无权操作其他党组织的数据");
    }

    /** 人员维度访问校验：SELF 只能访问本人，其他范围按组织规则判断。 */
    private void checkPersonAccess(Long orgId, Long personId) {
        BizException.throwForbiddenIf(!DataScopeHelper.canAccessData(orgId, personId), "无权操作其他人员的数据");
    }

    /**
     * 归一化冗余字段。
     * <p>新增时补齐默认值（人员状态=群众、状态=正常）；修改时只处理调用方显式传入的字段，
     * 避免局部更新把未提交的字段重置为默认值（MyBatis-Plus 只更新非 null 字段）。</p>
     *
     * @param person   待落库实体
     * @param isInsert true=新增
     */
    private void normalize(PartyPerson person, boolean isInsert) {
        if (isInsert) {
            if (person.getMemberStatus() == null) {
                person.setMemberStatus(MemberStatus.MASS.getCode());
            }
            if (person.getStatus() == null) {
                person.setStatus(Constants.STATUS_NORMAL);
            }
        }
        // 人员状态变化时同步党员标识
        if (person.getMemberStatus() != null) {
            person.setIsMember(MemberStatus.isPartyMember(person.getMemberStatus()) ? Constants.YES : Constants.NO);
        } else if (isInsert) {
            person.setIsMember(Constants.NO);
        }
        // 冗余年龄 / 党龄
        if (person.getBirthDate() != null) {
            person.setAge(yearsBetween(person.getBirthDate()));
        }
        if (person.getFullMemberDate() != null) {
            person.setPartyAge(yearsBetween(person.getFullMemberDate()));
        }
        if (StringUtils.hasText(person.getPhone())) {
            person.setPhone(person.getPhone().trim());
        }
        if (StringUtils.hasText(person.getIdCard())) {
            person.setIdCard(person.getIdCard().trim());
        }
    }

    /** 两个日期之间相差的整年数 */
    private Integer yearsBetween(LocalDate start) {
        if (start == null || start.isAfter(LocalDate.now())) {
            return 0;
        }
        return Period.between(start, LocalDate.now()).getYears();
    }
}
