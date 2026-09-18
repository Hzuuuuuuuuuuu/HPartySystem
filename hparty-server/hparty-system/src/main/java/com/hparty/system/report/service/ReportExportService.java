package com.hparty.system.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hparty.common.enums.DataScope;
import com.hparty.common.enums.MeetingType;
import com.hparty.common.exception.BizException;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.dto.PartyPersonQuery;
import com.hparty.system.domain.vo.PartyPersonVO;
import com.hparty.system.report.dto.ReportDevelopQuery;
import com.hparty.system.report.dto.ReportDuesQuery;
import com.hparty.system.report.dto.ReportMeetingQuery;
import com.hparty.system.report.dto.ReportReviewQuery;
import com.hparty.system.report.entity.ReportDevApplicant;
import com.hparty.system.report.entity.ReportDuesRecord;
import com.hparty.system.report.entity.ReportMeeting;
import com.hparty.system.report.entity.ReportReviewDetail;
import com.hparty.system.report.mapper.ReportDevMapper;
import com.hparty.system.report.mapper.ReportDuesMapper;
import com.hparty.system.report.mapper.ReportLookupMapper;
import com.hparty.system.report.mapper.ReportMeetingMapper;
import com.hparty.system.report.mapper.ReportReviewMapper;
import com.hparty.system.report.util.ExcelExportUtils;
import com.hparty.system.report.vo.DevelopExcelVO;
import com.hparty.system.report.vo.DuesExcelVO;
import com.hparty.system.report.vo.MeetingExcelVO;
import com.hparty.system.report.vo.MemberExcelVO;
import com.hparty.system.report.vo.ReviewExcelVO;
import com.hparty.system.service.PartyPersonService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统计报表导出服务（党员名册 / 党费台账 / 发展党员进度 / 三会一课 / 民主评议结果）。
 *
 * <p><b>三条硬要求，逐一对应到实现</b>：</p>
 * <ol>
 *   <li><b>分批查询 + 流式写出</b>：每个导出都用 {@link ExcelExportUtils#writeStreaming}，
 *       每批 {@value ExcelExportUtils#BATCH_SIZE} 条，写完即弃，不把全表读进内存。</li>
 *   <li><b>数据权限生效</b>：每个导出都先 {@code DataScopeHelper.apply(wrapper, ...)}
 *       再叠加与列表页**同名同义**的筛选条件。党员名册直接复用
 *       {@code PartyPersonService.listMembers}，条件与列表页同一份代码。</li>
 *   <li><b>敏感字段按权限打码</b>：身份证号、手机号仅对「超级管理员 / 数据范围=全部」
 *       的用户原样导出，其余用户打码（见 {@link #maskIdCard} / {@link #maskPhone}）。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final PartyPersonService personService;
    private final ReportDuesMapper duesMapper;
    private final ReportDevMapper devMapper;
    private final ReportMeetingMapper meetingMapper;
    private final ReportReviewMapper reviewMapper;
    private final ReportLookupMapper lookupMapper;

    // ------------------------------------------------------------------
    // 一、党员名册
    // ------------------------------------------------------------------

    /**
     * 导出党员名册（{@code is_member = 1}）。
     *
     * <p>直接分页调用 {@code PartyPersonService.listMembers}，
     * 与列表页共用同一套 wrapper —— 筛选口径与数据权限不可能不一致。</p>
     */
    public void exportMember(HttpServletResponse response, PartyPersonQuery query) throws IOException {
        boolean mask = needMask();
        ExcelExportUtils.writeStreaming(response, "党员名册", "党员名册", MemberExcelVO.class,
                (pageNum, pageSize) -> {
                    query.setPageNum(pageNum);
                    query.setPageSize(pageSize);
                    return personService.listMembers(query).getRecords();
                },
                (records, offset) -> {
                    List<MemberExcelVO> rows = new ArrayList<>(records.size());
                    int seq = offset;
                    for (PartyPersonVO p : records) {
                        MemberExcelVO vo = new MemberExcelVO();
                        vo.setSeqNo(++seq);
                        vo.setName(p.getName());
                        vo.setSexLabel(p.getSexLabel());
                        vo.setIdCard(mask ? maskIdCard(p.getIdCard()) : p.getIdCard());
                        vo.setBirthDate(text(p.getBirthDate()));
                        vo.setAge(p.getAge());
                        vo.setNation(p.getNation());
                        vo.setPhone(mask ? maskPhone(p.getPhone()) : p.getPhone());
                        vo.setEducation(p.getEducation());
                        vo.setWorkUnit(p.getWorkUnit());
                        vo.setOrgName(p.getOrgName());
                        vo.setMemberStatusLabel(p.getMemberStatusLabel());
                        vo.setPoliticalStatus(p.getPoliticalStatus());
                        vo.setFullMemberDate(text(p.getFullMemberDate()));
                        vo.setPartyAge(p.getPartyAge());
                        rows.add(vo);
                    }
                    return rows;
                });
    }

    // ------------------------------------------------------------------
    // 二、党费收缴台账
    // ------------------------------------------------------------------

    /** 导出党费收缴台账（按年/月筛选）。 */
    public void exportDues(HttpServletResponse response, ReportDuesQuery query) throws IOException {
        QueryWrapper<ReportDuesRecord> wrapper = new QueryWrapper<>();
        // 本表有 org_id 与 person_id，与党费列表页一样走默认列
        DataScopeHelper.apply(wrapper);
        wrapper.eq(query.getOrgId() != null, "org_id", query.getOrgId());
        wrapper.eq(query.getDuesYear() != null, "dues_year", query.getDuesYear());
        wrapper.eq(query.getDuesMonth() != null, "dues_month", query.getDuesMonth());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());
        wrapper.eq(query.getIsOverdue() != null, "is_overdue", query.getIsOverdue());
        wrapper.like(hasText(query.getPersonName()), "person_name", trim(query.getPersonName()));
        wrapper.orderByDesc("dues_year").orderByDesc("dues_month").orderByAsc("person_id");

        ExcelExportUtils.writeStreaming(response, "党费收缴台账", "党费台账", DuesExcelVO.class,
                (pageNum, pageSize) -> duesMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize),
                        wrapper).getRecords(),
                (records, offset) -> {
                    Map<Long, String> orgNames = orgNames(records.stream().map(ReportDuesRecord::getOrgId).toList());
                    List<DuesExcelVO> rows = new ArrayList<>(records.size());
                    int seq = offset;
                    for (ReportDuesRecord r : records) {
                        DuesExcelVO vo = new DuesExcelVO();
                        vo.setSeqNo(++seq);
                        vo.setPersonName(r.getPersonName());
                        vo.setOrgName(orgNames.get(r.getOrgId()));
                        vo.setDuesYear(r.getDuesYear());
                        vo.setDuesMonth(r.getDuesMonth());
                        vo.setDuesBase(r.getDuesBase());
                        vo.setDuesStandard(r.getDuesStandard());
                        vo.setDuesPaid(r.getDuesPaid());
                        vo.setPayDate(text(r.getPayDate()));
                        vo.setPayTypeLabel(labelOf(PAY_TYPE_LABELS, r.getPayType()));
                        vo.setStatusLabel(labelOf(DUES_STATUS_LABELS, r.getStatus()));
                        vo.setOverdueLabel(Objects.equals(r.getIsOverdue(), 1) ? "是" : "否");
                        rows.add(vo);
                    }
                    return rows;
                });
    }

    // ------------------------------------------------------------------
    // 三、发展党员进度表
    // ------------------------------------------------------------------

    /** 导出发展党员进度表。 */
    public void exportDevelop(HttpServletResponse response, ReportDevelopQuery query) throws IOException {
        LambdaQueryWrapper<ReportDevApplicant> wrapper = new LambdaQueryWrapper<>();
        // 本表无 person_id 列（只有冗余的 personId 字段但投影里保留了），统一按 org 过滤
        DataScopeHelper.apply(wrapper, "org_id", null);
        wrapper.eq(query.getOrgId() != null, ReportDevApplicant::getOrgId, query.getOrgId());
        wrapper.eq(hasText(query.getCurrentStage()), ReportDevApplicant::getCurrentStage, query.getCurrentStage());
        wrapper.eq(hasText(query.getCurrentStep()), ReportDevApplicant::getCurrentStep, query.getCurrentStep());
        wrapper.eq(query.getStatus() != null, ReportDevApplicant::getStatus, query.getStatus());
        if (hasText(query.getKeyword())) {
            // 投影表里没有姓名列，先按姓名查出 person_id 再收敛（与列表页的关键字口径一致）
            List<Long> personIds = lookupMapper.selectPersonIdsByName(trim(query.getKeyword()));
            if (personIds.isEmpty()) {
                wrapper.eq(ReportDevApplicant::getPersonId, -1L);
            } else {
                wrapper.in(ReportDevApplicant::getPersonId, personIds);
            }
        }
        wrapper.orderByAsc(ReportDevApplicant::getOrgId).orderByAsc(ReportDevApplicant::getApplicantId);

        Map<String, String> stepNames = codeNameMap(lookupMapper.selectStepNames(), "step_code", "step_name");
        Map<String, String> stageNames = codeNameMap(lookupMapper.selectStageNames(), "stage_code", "stage_name");

        ExcelExportUtils.writeStreaming(response, "发展党员进度表", "发展党员进度", DevelopExcelVO.class,
                (pageNum, pageSize) -> devMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize),
                        wrapper).getRecords(),
                (records, offset) -> {
                    Map<Long, String> orgNames = orgNames(records.stream().map(ReportDevApplicant::getOrgId).toList());
                    Map<Long, String> personNames = personNames(records.stream()
                            .map(ReportDevApplicant::getPersonId).toList());
                    List<DevelopExcelVO> rows = new ArrayList<>(records.size());
                    int seq = offset;
                    for (ReportDevApplicant a : records) {
                        DevelopExcelVO vo = new DevelopExcelVO();
                        vo.setSeqNo(++seq);
                        vo.setPersonName(personNames.get(a.getPersonId()));
                        vo.setOrgName(orgNames.get(a.getOrgId()));
                        vo.setStageName(a.getCurrentStage() == null ? null
                                : a.getCurrentStage() + " " + stageNames.getOrDefault(a.getCurrentStage(), ""));
                        vo.setStepName(a.getCurrentStep() == null ? null
                                : a.getCurrentStep() + " " + stepNames.getOrDefault(a.getCurrentStep(), ""));
                        vo.setStatusLabel(labelOf(DEV_STATUS_LABELS, a.getStatus()));
                        vo.setProgress(a.getProgress());
                        vo.setApplyDate(text(a.getApplyDate()));
                        vo.setActivistDate(text(a.getActivistDate()));
                        vo.setCandidateDate(text(a.getCandidateDate()));
                        vo.setProbationaryDate(text(a.getProbationaryDate()));
                        vo.setFullMemberDate(text(a.getFullMemberDate()));
                        vo.setProbationEndDate(text(a.getProbationEndDate()));
                        rows.add(vo);
                    }
                    return rows;
                });
    }

    // ------------------------------------------------------------------
    // 四、三会一课开展情况
    // ------------------------------------------------------------------

    /** 导出三会一课开展情况。 */
    public void exportMeeting(HttpServletResponse response, ReportMeetingQuery query) throws IOException {
        LambdaQueryWrapper<ReportMeeting> wrapper = new LambdaQueryWrapper<>();
        DataScopeHelper.apply(wrapper, "org_id", null);
        wrapper.eq(query.getOrgId() != null, ReportMeeting::getOrgId, query.getOrgId());
        wrapper.eq(hasText(query.getMeetingType()), ReportMeeting::getMeetingType, query.getMeetingType());
        wrapper.eq(query.getStatus() != null, ReportMeeting::getStatus, query.getStatus());
        wrapper.like(hasText(query.getKeyword()), ReportMeeting::getTitle, trim(query.getKeyword()));
        wrapper.ge(query.getStartDate() != null, ReportMeeting::getMeetingDate, query.getStartDate());
        wrapper.le(query.getEndDate() != null, ReportMeeting::getMeetingDate, query.getEndDate());
        wrapper.orderByDesc(ReportMeeting::getMeetingDate).orderByDesc(ReportMeeting::getMeetingId);

        ExcelExportUtils.writeStreaming(response, "三会一课开展情况", "三会一课", MeetingExcelVO.class,
                (pageNum, pageSize) -> meetingMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize),
                        wrapper).getRecords(),
                (records, offset) -> {
                    Map<Long, String> orgNames = orgNames(records.stream().map(ReportMeeting::getOrgId).toList());
                    List<MeetingExcelVO> rows = new ArrayList<>(records.size());
                    int seq = offset;
                    for (ReportMeeting m : records) {
                        MeetingExcelVO vo = new MeetingExcelVO();
                        vo.setSeqNo(++seq);
                        vo.setMeetingTypeLabel(MeetingType.labelOf(m.getMeetingType()));
                        vo.setTitle(m.getTitle());
                        vo.setOrgName(orgNames.get(m.getOrgId()));
                        vo.setMeetingDate(text(m.getMeetingDate()));
                        vo.setStartTime(m.getStartTime());
                        vo.setEndTime(m.getEndTime());
                        vo.setPlace(m.getPlace());
                        vo.setHostName(m.getHostName());
                        vo.setRecorderName(m.getRecorderName());
                        vo.setShouldAttend(m.getShouldAttend());
                        vo.setActualAttend(m.getActualAttend());
                        vo.setAttendRate(attendRate(m.getActualAttend(), m.getShouldAttend()));
                        vo.setStatusLabel(labelOf(MEETING_STATUS_LABELS, m.getStatus()));
                        rows.add(vo);
                    }
                    return rows;
                });
    }

    // ------------------------------------------------------------------
    // 五、民主评议结果
    // ------------------------------------------------------------------

    /** 导出民主评议结果（按批次/年度/等次筛选）。 */
    public void exportReview(HttpServletResponse response, ReportReviewQuery query) throws IOException {
        LambdaQueryWrapper<ReportReviewDetail> wrapper = new LambdaQueryWrapper<>();
        DataScopeHelper.apply(wrapper, "org_id", null);
        wrapper.eq(query.getReviewId() != null, ReportReviewDetail::getReviewId, query.getReviewId());
        wrapper.eq(query.getOrgId() != null, ReportReviewDetail::getOrgId, query.getOrgId());
        wrapper.eq(query.getGrade() != null, ReportReviewDetail::getGrade, query.getGrade());
        if (query.getReviewYear() != null) {
            List<Long> ids = lookupMapper.selectReviewIdsByYear(query.getReviewYear());
            if (ids.isEmpty()) {
                wrapper.eq(ReportReviewDetail::getReviewId, -1L);
            } else {
                wrapper.in(ReportReviewDetail::getReviewId, ids);
            }
        }
        wrapper.orderByAsc(ReportReviewDetail::getReviewId).orderByAsc(ReportReviewDetail::getDetailId);

        ExcelExportUtils.writeStreaming(response, "民主评议党员结果", "评议结果", ReviewExcelVO.class,
                (pageNum, pageSize) -> reviewMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize),
                        wrapper).getRecords(),
                (records, offset) -> {
                    Map<Long, String> orgNames = orgNames(records.stream().map(ReportReviewDetail::getOrgId).toList());
                    Map<Long, Map<String, Object>> reviews = reviewInfo(records.stream()
                            .map(ReportReviewDetail::getReviewId).toList());
                    List<ReviewExcelVO> rows = new ArrayList<>(records.size());
                    int seq = offset;
                    for (ReportReviewDetail d : records) {
                        Map<String, Object> review = reviews.getOrDefault(d.getReviewId(), Map.of());
                        ReviewExcelVO vo = new ReviewExcelVO();
                        vo.setSeqNo(++seq);
                        vo.setReviewTitle(asString(review.get("title")));
                        Object year = review.get("review_year");
                        vo.setReviewYear(year instanceof Number num ? num.intValue() : null);
                        vo.setPersonName(d.getPersonName());
                        vo.setOrgName(orgNames.get(d.getOrgId()));
                        vo.setSelfScore(d.getSelfScore());
                        vo.setPeerScore(d.getPeerScore());
                        vo.setPeerCount(d.getPeerCount());
                        vo.setMassScore(d.getMassScore());
                        vo.setOrgScore(d.getOrgScore());
                        vo.setTotalScore(d.getTotalScore());
                        vo.setGradeLabel(labelOf(GRADE_LABELS, d.getGrade()));
                        vo.setOrgComment(d.getOrgComment());
                        vo.setDispose(d.getDispose());
                        rows.add(vo);
                    }
                    return rows;
                });
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 当前用户是否需要打码：只有超级管理员与数据范围=全部的用户能看到完整身份证/手机号 */
    private boolean needMask() {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null) {
            return true;
        }
        if (user.isSuperAdmin()) {
            return false;
        }
        return DataScope.of(user.getDataScope()) != DataScope.ALL;
    }

    /** 身份证打码：保留前 6 位与后 4 位 */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return idCard;
        }
        String value = idCard.trim();
        if (value.length() < 10) {
            return "****";
        }
        return value.substring(0, 6) + "********" + value.substring(value.length() - 4);
    }

    /** 手机号打码：保留前 3 位与后 4 位 */
    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String value = phone.trim();
        if (value.length() < 7) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private BigDecimal attendRate(Integer actual, Integer should) {
        if (should == null || should <= 0 || actual == null) {
            return null;
        }
        return BigDecimal.valueOf(actual).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(should), 1, RoundingMode.HALF_UP);
    }

    private Map<Long, String> orgNames(List<Long> orgIds) {
        List<Long> ids = distinct(orgIds);
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return toMap(lookupMapper.selectOrgNames(ids), "org_id", "org_name");
    }

    private Map<Long, String> personNames(List<Long> personIds) {
        List<Long> ids = distinct(personIds);
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return toMap(lookupMapper.selectPersonNames(ids), "person_id", "name");
    }

    /** 批量：评议批次 ID → 批次信息（标题/年度），一次查完避免 N+1 */
    private Map<Long, Map<String, Object>> reviewInfo(List<Long> reviewIds) {
        List<Long> ids = distinct(reviewIds);
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> row : lookupMapper.selectReviewTitles(ids)) {
            Object key = row.get("review_id");
            if (key instanceof Number num) {
                map.put(num.longValue(), row);
            }
        }
        return map;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 空值安全的字典回显。
     *
     * <p>不能直接写 {@code MAP.getOrDefault(code, "—")}：本类的字典是用
     * {@code Map.of(...)} 建的**不可变 Map**，而不可变 Map 的
     * {@code get/getOrDefault} 遇到 null 键会抛 NPE（不是返回默认值）——
     * 等次、缴纳方式这类列在数据未填时就是 null，会直接把导出打成 500。</p>
     */
    private String labelOf(Map<Integer, String> dict, Integer code) {
        return code == null ? "—" : dict.getOrDefault(code, "—");
    }

    private List<Long> distinct(List<Long> values) {
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private Map<Long, String> toMap(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyField);
            Object value = row.get(valueField);
            if (key instanceof Number num) {
                map.put(num.longValue(), value == null ? null : String.valueOf(value));
            }
        }
        return map;
    }

    private Map<String, String> codeNameMap(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyField);
            Object value = row.get(valueField);
            if (key != null) {
                map.put(String.valueOf(key), value == null ? "" : String.valueOf(value));
            }
        }
        return map;
    }

    private String text(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static final Map<Integer, String> PAY_TYPE_LABELS = Map.of(
            1, "现金", 2, "银行代扣", 3, "微信", 4, "支付宝", 5, "其它");

    private static final Map<Integer, String> DUES_STATUS_LABELS = Map.of(
            0, "未缴", 1, "已缴", 2, "免缴", 3, "补缴");

    private static final Map<Integer, String> MEETING_STATUS_LABELS = Map.of(
            0, "草稿", 1, "待召开", 2, "进行中", 3, "已结束", 4, "已归档");

    private static final Map<Integer, String> DEV_STATUS_LABELS = Map.of(
            1, "进行中", 2, "已完成", 3, "已终止", 4, "已中止");

    private static final Map<Integer, String> GRADE_LABELS = Map.of(
            1, "优秀", 2, "合格", 3, "基本合格", 4, "不合格");
}
