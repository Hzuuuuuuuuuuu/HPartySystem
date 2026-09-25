package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.enums.DataScope;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.dto.DuesRecordQuery;
import com.hparty.party.domain.entity.PartyDuesRecord;
import com.hparty.party.domain.entity.PartyDuesUse;
import com.hparty.party.enums.DuesPayTypeEnum;
import com.hparty.party.enums.DuesStatusEnum;
import com.hparty.party.enums.DuesUseCategoryEnum;
import com.hparty.party.mapper.PartyDuesRecordMapper;
import com.hparty.party.mapper.PartyDuesUseMapper;
import com.hparty.party.mapper.PartyLookupMapper;
import com.hparty.party.util.PartyDuesCalculator;
import com.hparty.party.util.PartyNameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 党费收缴及使用服务。
 *
 * <p>应缴金额一律由 {@link PartyDuesCalculator} 依据缴纳基数推导，
 * 新增、修改、批量生成三条路径共用同一份分档口径。</p>
 */
@Service
@RequiredArgsConstructor
public class PartyDuesService {

    private final PartyDuesRecordMapper recordMapper;
    private final PartyDuesUseMapper useMapper;
    private final PartyLookupMapper lookupMapper;

    // ------------------------------------------------------------------
    // 缴纳记录
    // ------------------------------------------------------------------

    /** 分页查询党费缴纳记录。 */
    public PageResult<Map<String, Object>> recordPage(DuesRecordQuery query) {
        LambdaQueryWrapper<PartyDuesRecord> wrapper = new LambdaQueryWrapper<>();
        DataScopeHelper.apply(wrapper);
        if (query.getOrgId() != null) {
            wrapper.eq(PartyDuesRecord::getOrgId, query.getOrgId());
        }
        if (query.getDuesYear() != null) {
            wrapper.eq(PartyDuesRecord::getDuesYear, query.getDuesYear());
        }
        if (query.getDuesMonth() != null) {
            wrapper.eq(PartyDuesRecord::getDuesMonth, query.getDuesMonth());
        }
        if (query.getStatus() != null) {
            wrapper.eq(PartyDuesRecord::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getPersonName())) {
            wrapper.like(PartyDuesRecord::getPersonName, query.getPersonName().trim());
        }
        wrapper.orderByDesc(PartyDuesRecord::getDuesYear)
                .orderByDesc(PartyDuesRecord::getDuesMonth)
                .orderByAsc(PartyDuesRecord::getPersonId);

        Page<PartyDuesRecord> page = recordMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> orgNames = loadOrgNames(page.getRecords().stream()
                .map(PartyDuesRecord::getOrgId).toList());
        return PageResult.of(page, r -> toRecordVO(r, orgNames));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addRecord(PartyDuesRecord record) {
        if (record.getPersonId() == null) {
            throw new BizException("请选择党员");
        }
        if (record.getDuesYear() == null || record.getDuesMonth() == null) {
            throw new BizException("请填写党费所属年月");
        }
        checkMonth(record.getDuesMonth());
        record.setDuesId(null);
        if (record.getOrgId() == null) {
            record.setOrgId(SecurityUtils.getOrgId());
        }
        // 无归属组织的账号提前拦下：party_dues_record.org_id 是 NOT NULL（MySQL 1364）。
        BizException.throwIf(record.getOrgId() == null, "当前账号未分配所属党组织，无法创建。");
        if (StrUtil.isBlank(record.getPersonName())) {
            record.setPersonName(lookupMapper.selectPersonName(record.getPersonId()));
        }
        // 一人一月一条，先查重避免触发唯一键冲突
        Long duplicated = recordMapper.selectCount(new LambdaQueryWrapper<PartyDuesRecord>()
                .eq(PartyDuesRecord::getPersonId, record.getPersonId())
                .eq(PartyDuesRecord::getDuesYear, record.getDuesYear())
                .eq(PartyDuesRecord::getDuesMonth, record.getDuesMonth()));
        if (duplicated != null && duplicated > 0) {
            throw new BizException("该党员当月党费记录已存在");
        }
        applyStandard(record, record.getDuesBase());
        applyPaidDefaults(record);
        recordMapper.insert(record);
        return record.getDuesId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRecord(PartyDuesRecord record) {
        if (record.getDuesId() == null) {
            throw new BizException("党费ID不能为空");
        }
        PartyDuesRecord exists = getRecord(record.getDuesId());
        record.setOrgId(exists.getOrgId());
        // 基数没传时沿用库中基数，保证应缴金额与基数始终一致
        BigDecimal base = record.getDuesBase() != null ? record.getDuesBase() : exists.getDuesBase();
        applyStandard(record, base);
        applyPaidDefaults(record);
        recordMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeRecord(Long duesId) {
        getRecord(duesId);
        recordMapper.deleteById(duesId);
    }

    /** 按主键取缴纳记录并做越权校验。 */
    public PartyDuesRecord getRecord(Long duesId) {
        if (duesId == null) {
            throw new BizException("党费ID不能为空");
        }
        PartyDuesRecord record = recordMapper.selectById(duesId);
        if (record == null) {
            throw new BizException("党费记录不存在");
        }
        if (!DataScopeHelper.canAccessData(record.getOrgId(), record.getPersonId())) {
            throw BizException.forbidden("无权查看其他党组织或其他人员的党费记录");
        }
        return record;
    }

    /**
     * 为全体正式党员批量生成指定月份的党费账单。
     *
     * <p>已存在（同一党员同一年月）的记录跳过，可重复执行。</p>
     *
     * @return 本次新增的记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int generate(Integer year, Integer month) {
        if (year == null || month == null) {
            throw new BizException("请指定年份和月份");
        }
        checkMonth(month);

        // 唯一键是「人 + 年 + 月」，查重必须全库范围，不能按数据权限裁剪
        Set<Long> exists = new HashSet<>();
        List<PartyDuesRecord> generated = recordMapper.selectList(new LambdaQueryWrapper<PartyDuesRecord>()
                .select(PartyDuesRecord::getPersonId)
                .eq(PartyDuesRecord::getDuesYear, year)
                .eq(PartyDuesRecord::getDuesMonth, month));
        generated.stream().map(PartyDuesRecord::getPersonId)
                .filter(Objects::nonNull).forEach(exists::add);

        List<Long> orgIds = resolveAccessibleOrgIds();
        if (orgIds != null && orgIds.isEmpty()) {
            return 0;
        }
        List<Map<String, Object>> members = lookupMapper.selectFullMembersForDues(orgIds, resolveAccessiblePersonIds());

        int count = 0;
        for (Map<String, Object> member : members) {
            Long personId = PartyNameUtils.toLong(member.get("person_id"));
            if (personId == null || exists.contains(personId)) {
                continue;
            }
            BigDecimal base = PartyNameUtils.toDecimal(member.get("dues_base"));

            PartyDuesRecord record = new PartyDuesRecord();
            record.setPersonId(personId);
            record.setPersonName(member.get("name") == null ? null : String.valueOf(member.get("name")));
            record.setOrgId(PartyNameUtils.toLong(member.get("org_id")));
            record.setDuesYear(year);
            record.setDuesMonth(month);
            record.setDuesBase(base);
            record.setDuesStandard(PartyDuesCalculator.calcStandard(base));
            record.setStatus(DuesStatusEnum.UNPAID.getCode());
            record.setIsOverdue(0);
            recordMapper.insert(record);
            count++;
        }
        return count;
    }

    /**
     * 党费收缴统计：年度应缴 / 实缴合计、已缴未缴笔数、逐月明细。
     */
    public Map<String, Object> recordStatistics(Integer year, Long orgId) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;

        LambdaQueryWrapper<PartyDuesRecord> wrapper = new LambdaQueryWrapper<PartyDuesRecord>()
                .eq(PartyDuesRecord::getDuesYear, targetYear);
        DataScopeHelper.apply(wrapper);
        if (orgId != null) {
            wrapper.eq(PartyDuesRecord::getOrgId, orgId);
        }
        List<PartyDuesRecord> records = recordMapper.selectList(wrapper);

        List<Map<String, Object>> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            final int targetMonth = month;
            List<PartyDuesRecord> ofMonth = records.stream()
                    .filter(r -> Objects.equals(r.getDuesMonth(), targetMonth)).toList();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", month);
            item.put("shouldTotal", sum(ofMonth, PartyDuesRecord::getDuesStandard));
            item.put("paidTotal", sum(ofMonth, PartyDuesRecord::getDuesPaid));
            item.put("paidCount", countByStatus(ofMonth, DuesStatusEnum.PAID.getCode()));
            item.put("unpaidCount", countByStatus(ofMonth, DuesStatusEnum.UNPAID.getCode()));
            months.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", targetYear);
        result.put("shouldTotal", sum(records, PartyDuesRecord::getDuesStandard));
        result.put("paidTotal", sum(records, PartyDuesRecord::getDuesPaid));
        result.put("unpaidCount", countByStatus(records, DuesStatusEnum.UNPAID.getCode()));
        result.put("paidCount", countByStatus(records, DuesStatusEnum.PAID.getCode()));
        result.put("months", months);
        return result;
    }

    // ------------------------------------------------------------------
    // 使用记录
    // ------------------------------------------------------------------

    /** 党费使用记录列表。 */
    public List<Map<String, Object>> useList(Long orgId, Integer useYear) {
        LambdaQueryWrapper<PartyDuesUse> wrapper = new LambdaQueryWrapper<>();
        DataScopeHelper.apply(wrapper);
        if (orgId != null) {
            wrapper.eq(PartyDuesUse::getOrgId, orgId);
        }
        if (useYear != null) {
            wrapper.eq(PartyDuesUse::getUseYear, useYear);
        }
        wrapper.orderByDesc(PartyDuesUse::getUseDate).orderByDesc(PartyDuesUse::getUseId);

        List<PartyDuesUse> list = useMapper.selectList(wrapper);
        Map<Long, String> orgNames = loadOrgNames(list.stream().map(PartyDuesUse::getOrgId).toList());
        return list.stream().map(u -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("useId", u.getUseId());
            item.put("orgId", u.getOrgId());
            item.put("orgName", orgNames.get(u.getOrgId()));
            item.put("useYear", u.getUseYear());
            item.put("useMonth", u.getUseMonth());
            item.put("amount", u.getAmount());
            item.put("useCategory", u.getUseCategory());
            item.put("useCategoryLabel", DuesUseCategoryEnum.labelOf(u.getUseCategory()));
            item.put("purpose", u.getPurpose());
            item.put("useDate", u.getUseDate());
            item.put("approver", u.getApprover());
            item.put("fileId", u.getFileId());
            item.put("fileUrl", u.getFileUrl());
            item.put("remark", u.getRemark());
            return item;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addUse(PartyDuesUse use) {
        if (use.getAmount() == null || use.getAmount().signum() <= 0) {
            throw new BizException("请填写正确的使用金额");
        }
        if (StrUtil.isBlank(use.getPurpose())) {
            throw new BizException("请填写具体用途");
        }
        use.setUseId(null);
        if (use.getOrgId() == null) {
            use.setOrgId(SecurityUtils.getOrgId());
        }
        // 无归属组织的账号提前拦下：party_dues_use.org_id 是 NOT NULL（MySQL 1364）。
        BizException.throwIf(use.getOrgId() == null, "当前账号未分配所属党组织，无法创建。");
        if (use.getUseYear() == null) {
            use.setUseYear(LocalDate.now().getYear());
        }
        if (use.getUseCategory() == null) {
            use.setUseCategory(DuesUseCategoryEnum.EDUCATION.getCode());
        }
        if (StrUtil.isBlank(use.getApprover())) {
            use.setApprover(SecurityUtils.getUsername());
        }
        useMapper.insert(use);
        return use.getUseId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeUse(Long useId) {
        if (useId == null) {
            throw new BizException("使用ID不能为空");
        }
        PartyDuesUse use = useMapper.selectById(useId);
        if (use == null) {
            throw new BizException("党费使用记录不存在");
        }
        if (!DataScopeHelper.canAccessOrg(use.getOrgId())) {
            throw BizException.forbidden("无权操作其他党组织的党费使用记录");
        }
        useMapper.deleteById(useId);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 统一走 {@link PartyDuesCalculator} 推导应缴金额（基数为空时保留原值） */
    private void applyStandard(PartyDuesRecord record, BigDecimal base) {
        if (base != null) {
            record.setDuesStandard(PartyDuesCalculator.calcStandard(base));
        }
    }

    /** 已缴状态补齐实缴金额、缴纳日期，并清掉欠缴标记 */
    private void applyPaidDefaults(PartyDuesRecord record) {
        if (Objects.equals(record.getStatus(), DuesStatusEnum.PAID.getCode())) {
            if (record.getDuesPaid() == null) {
                record.setDuesPaid(record.getDuesStandard());
            }
            if (record.getPayDate() == null) {
                record.setPayDate(LocalDate.now());
            }
            record.setIsOverdue(0);
        }
        if (record.getIsOverdue() == null) {
            record.setIsOverdue(0);
        }
    }

    private void checkMonth(Integer month) {
        if (month < 1 || month > 12) {
            throw new BizException("月份必须在 1-12 之间");
        }
    }

    private int countByStatus(List<PartyDuesRecord> records, Integer status) {
        return (int) records.stream().filter(r -> Objects.equals(r.getStatus(), status)).count();
    }

    private BigDecimal sum(List<PartyDuesRecord> records, Function<PartyDuesRecord, BigDecimal> getter) {
        return records.stream().map(getter).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 当前用户数据权限内的组织 ID 列表。
     * <p>返回 null 表示不限组织（全部数据权限）。</p>
     */
    private List<Long> resolveAccessibleOrgIds() {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null || user.isSuperAdmin()) {
            return null;
        }
        return switch (DataScope.of(user.getDataScope())) {
            case ALL -> null;
            // 「仅本人」由人员条件收敛，这里不加组织限制
            case SELF -> null;
            case CURRENT -> user.getOrgId() == null ? List.of() : List.of(user.getOrgId());
            case CURRENT_AND_CHILD -> StrUtil.isBlank(user.getOrgPath())
                    ? (user.getOrgId() == null ? List.of() : List.of(user.getOrgId()))
                    : lookupMapper.selectOrgIdsByPathPrefix(user.getOrgPath() + "%");
            case CUSTOM -> lookupMapper.selectOrgIdsByUser(user.getUserId());
        };
    }

    /** 「仅本人」数据权限下收敛到本人档案 */
    private List<Long> resolveAccessiblePersonIds() {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null || user.isSuperAdmin()) {
            return null;
        }
        if (DataScope.of(user.getDataScope()) == DataScope.SELF && user.getPersonId() != null) {
            return List.of(user.getPersonId());
        }
        return null;
    }

    /** 批量补组织名，避免 N+1 */
    private Map<Long, String> loadOrgNames(List<Long> orgIds) {
        List<Long> ids = orgIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : PartyNameUtils.toOrgNameMap(lookupMapper.selectOrgNames(ids));
    }

    private Map<String, Object> toRecordVO(PartyDuesRecord r, Map<Long, String> orgNames) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("duesId", r.getDuesId());
        item.put("personId", r.getPersonId());
        item.put("personName", r.getPersonName());
        item.put("orgId", r.getOrgId());
        item.put("orgName", orgNames.get(r.getOrgId()));
        item.put("duesYear", r.getDuesYear());
        item.put("duesMonth", r.getDuesMonth());
        item.put("duesBase", r.getDuesBase());
        item.put("duesStandard", r.getDuesStandard());
        item.put("duesPaid", r.getDuesPaid());
        item.put("payDate", r.getPayDate());
        item.put("payType", r.getPayType());
        item.put("payTypeLabel", DuesPayTypeEnum.labelOf(r.getPayType()));
        item.put("status", r.getStatus());
        item.put("statusLabel", DuesStatusEnum.labelOf(r.getStatus()));
        item.put("isOverdue", computeOverdue(r));
        item.put("overdueMonths", overdueMonths(r));
        item.put("remark", r.getRemark());
        return item;
    }

    /**
     * 计算某条党费记录是否已构成欠缴。
     *
     * <p><b>为什么在读取时现算，而不是读 {@code is_overdue} 列</b>：
     * 该列需要定时任务刷新才会有值，若任务没跑或跑漏了，界面就会显示"未欠缴"，
     * 而党费欠缴是有法定后果的 —— 《党章》规定党员连续 6 个月不缴纳党费
     * 按自行脱党处理。这类判断绝不能依赖一个可能过期的缓存列。</p>
     *
     * <p>判定规则：**当月账单到次月仍未缴清即为欠缴**。
     * 已缴（status=1）与免缴（status=2）不计欠缴。</p>
     *
     * <p>{@link #refreshOverdueFlags()} 会把结果落到列上，供统计报表与
     * 带索引的批量筛查使用；两者结果一致。</p>
     */
    private boolean computeOverdue(PartyDuesRecord r) {
        if (r.getStatus() == null || r.getStatus() != DuesStatusEnum.UNPAID.getCode()) {
            return false;
        }
        return overdueMonths(r) > 0;
    }

    /**
     * 欠缴月数：当前年月与该账单年月的差值。未欠缴返回 0。
     * <p>用于「连续欠缴 6 个月」这类预警，以及界面上的「已欠 X 个月」提示。</p>
     */
    private int overdueMonths(PartyDuesRecord r) {
        if (r.getStatus() == null || r.getStatus() != DuesStatusEnum.UNPAID.getCode()
                || r.getDuesYear() == null || r.getDuesMonth() == null) {
            return 0;
        }
        LocalDate now = LocalDate.now();
        int diff = (now.getYear() * 12 + now.getMonthValue())
                - (r.getDuesYear() * 12 + r.getDuesMonth());
        return Math.max(0, diff);
    }

    /**
     * 把欠缴标记刷新到 {@code party_dues_record.is_overdue} 列。
     *
     * <p>供定时任务与统计报表调用。判定口径与 {@link #computeOverdue} 完全一致，
     * 区别只是结果落库，便于带索引地批量筛查欠缴人员。</p>
     *
     * @return 被标记为欠缴的记录数
     */
    /**
     * 刷新欠缴标记。
     *
     * <p><b>为什么全部改成「先 SELECT 出主键，再 UPDATE ... IN (ids)」的形态</b>：
     * MyBatis-Plus 的 {@code BlockAttackInnerInterceptor}（用于拦截误写的全表更新）
     * 对本项目造成了两类误判，都会抛 {@code Prohibition of table update operation}：</p>
     *
     * <ol>
     *   <li>UPDATE 的 WHERE 里出现 <b>OR</b> —— 而「账单月份早于当前月」在 SQL 里天然要写成
     *       {@code year < ? OR (year = ? AND month < ?)}</li>
     *   <li>UPDATE 的 WHERE 里出现 <b>{@code <>}</b>，且该表<b>带逻辑删除</b> ——
     *       它的内部判定 {@code fullMatch} 把 {@code 列 <> ?} 误认为「恒真条件」，
     *       与 MP 自动追加的 {@code del_flag = ?} 一相与，就判定为无有效 WHERE。
     *       （这解释了为什么同样写法在 {@code dev_step_record} 上没事 —— 那张表没有 del_flag）</li>
     * </ol>
     *
     * <p>拦截器本身有价值（能挡住真正的全表更新），因此不摘除，而是把判定挪到 Java 侧：
     * SELECT 里可以随便用 OR 和 {@code <>}（它只管 UPDATE/DELETE），UPDATE 只用 {@code IN}。
     * 顺带两个好处：判定逻辑用 Java 写更易读；且只更新确实需要变更的行。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int refreshOverdueFlags() {
        LocalDate now = LocalDate.now();
        int current = now.getYear() * 12 + now.getMonthValue();

        // 1) 已缴/免缴/补缴但还被标着欠缴的 → 清除标记
        List<Long> toClearIds = recordMapper.selectList(new LambdaQueryWrapper<PartyDuesRecord>()
                        .select(PartyDuesRecord::getDuesId)
                        .eq(PartyDuesRecord::getIsOverdue, 1)
                        .ne(PartyDuesRecord::getStatus, DuesStatusEnum.UNPAID.getCode()))
                .stream().map(PartyDuesRecord::getDuesId).toList();

        // 2) 未缴账单：按「账单月份是否早于当前月」判定欠缴
        List<PartyDuesRecord> unpaid = recordMapper.selectList(new LambdaQueryWrapper<PartyDuesRecord>()
                .select(PartyDuesRecord::getDuesId, PartyDuesRecord::getDuesYear,
                        PartyDuesRecord::getDuesMonth, PartyDuesRecord::getIsOverdue)
                .eq(PartyDuesRecord::getStatus, DuesStatusEnum.UNPAID.getCode()));

        List<Long> toMarkIds = new ArrayList<>();
        List<Long> toUnmarkIds = new ArrayList<>(toClearIds);
        for (PartyDuesRecord r : unpaid) {
            boolean overdue = r.getDuesYear() != null && r.getDuesMonth() != null
                    && (r.getDuesYear() * 12 + r.getDuesMonth()) < current;
            boolean flagged = r.getIsOverdue() != null && r.getIsOverdue() == 1;

            if (overdue && !flagged) {
                toMarkIds.add(r.getDuesId());
            } else if (!overdue && flagged) {
                // 未缴但还没到期（含当月）—— 之前误标过就撤掉
                toUnmarkIds.add(r.getDuesId());
            }
        }

        if (!toUnmarkIds.isEmpty()) {
            recordMapper.update(null, new LambdaUpdateWrapper<PartyDuesRecord>()
                    .set(PartyDuesRecord::getIsOverdue, 0)
                    .in(PartyDuesRecord::getDuesId, toUnmarkIds));
        }
        if (!toMarkIds.isEmpty()) {
            recordMapper.update(null, new LambdaUpdateWrapper<PartyDuesRecord>()
                    .set(PartyDuesRecord::getIsOverdue, 1)
                    .in(PartyDuesRecord::getDuesId, toMarkIds));
        }
        return toMarkIds.size();
    }

    /**
     * 欠缴预警：查出连续欠缴达到指定月数的人员。
     *
     * <p>《党章》规定党员连续 6 个月不缴纳党费按自行脱党处理，
     * 因此这个清单是支部必须定期核查的。</p>
     *
     * @param minMonths 最少连续欠缴月数，传 6 即筛查面临脱党风险的人员
     */
    public List<Map<String, Object>> listArrears(int minMonths) {
        LocalDate now = LocalDate.now();
        int cutoff = now.getYear() * 12 + now.getMonthValue() - minMonths;

        var wrapper = new LambdaQueryWrapper<PartyDuesRecord>()
                .eq(PartyDuesRecord::getStatus, DuesStatusEnum.UNPAID.getCode())
                .apply("(dues_year * 12 + dues_month) <= {0}", cutoff);
        DataScopeHelper.apply(wrapper);

        List<PartyDuesRecord> records = recordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return List.of();
        }

        Map<Long, String> orgNames = loadOrgNames(
                records.stream().map(PartyDuesRecord::getOrgId).toList());

        // 按人聚合，统计各人欠缴月数
        Map<Long, List<PartyDuesRecord>> byPerson = records.stream()
                .collect(Collectors.groupingBy(PartyDuesRecord::getPersonId));

        return byPerson.entrySet().stream().map(e -> {
            List<PartyDuesRecord> list = e.getValue();
            PartyDuesRecord first = list.get(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("personId", first.getPersonId());
            item.put("personName", first.getPersonName());
            item.put("orgId", first.getOrgId());
            item.put("orgName", orgNames.get(first.getOrgId()));
            item.put("overdueMonths", list.stream().mapToInt(this::overdueMonths).max().orElse(0));
            item.put("unpaidCount", list.size());
            item.put("unpaidAmount", list.stream()
                    .map(r -> r.getDuesStandard() == null ? BigDecimal.ZERO : r.getDuesStandard())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            item.put("earliestMonth", String.format("%d-%02d",
                    first.getDuesYear(), first.getDuesMonth()));
            return item;
        }).sorted((a, b) -> Integer.compare(
                (int) b.get("overdueMonths"), (int) a.get("overdueMonths"))).toList();
    }
}
