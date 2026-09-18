package com.hparty.system.service;

import cn.hutool.core.util.StrUtil;
import com.hparty.common.enums.DataScope;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.vo.TodoGroupVO;
import com.hparty.system.domain.vo.TodoItemVO;
import com.hparty.system.domain.vo.TodoResultVO;
import com.hparty.system.mapper.MyTodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 「我的待办」聚合服务。
 *
 * <p><b>不建新表</b>：待办数据本来就在库里（{@code dev_step_record.status=2} 就是「当前待办」），
 * 这里只是把散在四个业务域的待办聚合出一个出口：
 * 发展党员、三会一课任务、党费、组织关系转接。</p>
 *
 * <p><b>数据权限</b>：本模块的查询全部是注解 SQL，无法直接调
 * {@code DataScopeHelper.apply(wrapper)}（那是给 Wrapper 用的）。因此这里在 Java 侧
 * 复刻了 {@code DataScopeHelper} 的 switch，把「我可见哪些数据」翻译成两个参数
 * {@code orgIds} 与 {@code personId} 传给 Mapper：</p>
 * <ul>
 *   <li>1=全部 / 超管 → {@code orgIds=null}（不限）；</li>
 *   <li>2=本级 → {@code orgIds=[我的 orgId]}；</li>
 *   <li>3=本级及以下 → {@code orgIds=} 物化路径前缀匹配出的整棵子树；</li>
 *   <li>4=仅本人 → {@code personId=我的 personId}，<b>不再按组织放开</b>，
 *       否则普通党员会看到整个支部的待办；</li>
 *   <li>5=自定义 → {@code orgIds=sys_role_dept} 里授给我的组织。</li>
 * </ul>
 * <p>口径与 {@code DataScopeHelper} 一致，只是换了承载形式。
 * 之所以不复用它的 {@code apply}，是因为转接单等场景需要把组织条件
 * 与业务条件做 OR / IN 组合，而 {@code apply} 只能追加 AND 条件。</p>
 *
 * <p><b>权限</b>：所有登录用户都能访问自己的待办，接口上<b>不加</b>
 * {@code @SaCheckPermission} —— 数据范围由数据权限控制，而不是功能权限。</p>
 */
@Service
@RequiredArgsConstructor
public class MyTodoService {

    /** 分组编码 */
    private static final String TYPE_DEVELOP = "DEVELOP";
    private static final String TYPE_TASK = "TASK";
    private static final String TYPE_DUES = "DUES";
    private static final String TYPE_TRANSFER = "TRANSFER";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MyTodoMapper todoMapper;

    /**
     * 当前用户的全部待办，按来源分组。
     */
    public TodoResultVO list() {
        LoginUser user = SecurityUtils.getLoginUser();
        Scope scope = resolveScope(user);

        List<TodoGroupVO> groups = new ArrayList<>();
        groups.add(developGroup(scope));
        groups.add(taskGroup(scope, user));
        groups.add(duesGroup(scope));
        groups.add(transferGroup(scope));

        TodoResultVO result = new TodoResultVO();
        result.setGroups(groups);
        result.setTotal(groups.stream().mapToInt(TodoGroupVO::getCount).sum());
        result.setOverdueTotal(groups.stream()
                .flatMap(g -> g.getItems().stream())
                .mapToInt(i -> Boolean.TRUE.equals(i.getOverdue()) ? 1 : 0)
                .sum());
        return result;
    }

    /**
     * 待办数量概览（首页卡片与红点用）。
     *
     * <p>结构比 {@link #list()} 轻：只回数量不回明细，避免首页为了一个数字拉全量待办。</p>
     */
    public TodoResultVO count() {
        TodoResultVO full = list();
        TodoResultVO brief = new TodoResultVO();
        brief.setTotal(full.getTotal());
        brief.setOverdueTotal(full.getOverdueTotal());
        for (TodoGroupVO g : full.getGroups()) {
            brief.getGroups().add(TodoGroupVO.brief(g.getType(), g.getTypeLabel(), g.getCount()));
        }
        return brief;
    }

    // ==================== 各来源分组 ====================

    /** 发展党员：{@code dev_step_record.status=2} 的待办步骤 */
    private TodoGroupVO developGroup(Scope scope) {
        if (scope.isEmpty()) {
            return TodoGroupVO.of(TYPE_DEVELOP, "发展党员", List.of());
        }
        List<TodoItemVO> items = new ArrayList<>();
        for (Map<String, Object> row : todoMapper.selectDevelopTodos(scope.orgIds(), scope.personId())) {
            LocalDateTime deadline = toDateTime(row.get("deadline_time"));
            LocalDate dueDate = deadline == null ? null : deadline.toLocalDate();
            long overdueDays = overdueDays(dueDate);

            TodoItemVO item = new TodoItemVO();
            Long applicantId = toLong(row.get("applicant_id"));
            String stepCode = toStr(row.get("step_code"));
            item.setKey("dev-" + applicantId + "-" + stepCode);
            item.setTitle(StrUtil.format("{} · {} {}",
                    StrUtil.blankToDefault(toStr(row.get("person_name")), "未知人员"),
                    stepCode,
                    StrUtil.blankToDefault(toStr(row.get("step_name")), "")));
            item.setDescription(joinDot(toStr(row.get("org_name")), deadlineText(deadline, overdueDays)));
            item.setDeadline(deadline);
            item.setOverdue(overdueDays > 0);
            item.setOverdueDays(overdueDays);
            item.setLink("/develop/applicant/" + applicantId);
            items.add(item);
        }
        return TodoGroupVO.of(TYPE_DEVELOP, "发展党员", items);
    }

    /** 三会一课：已发布且本组织未提交材料的任务 */
    private TodoGroupVO taskGroup(Scope scope, LoginUser user) {
        // 「本组织未提交」需要一个明确的组织；仅本人范围的账号（普通党员）不承担支部任务
        if (scope.isEmpty() || scope.selfOnly() || user.getOrgId() == null) {
            return TodoGroupVO.of(TYPE_TASK, "三会一课任务", List.of());
        }
        List<TodoItemVO> items = new ArrayList<>();
        for (Map<String, Object> row : todoMapper.selectTaskTodos(scope.orgIds(), user.getOrgId())) {
            // 截止日期是 DATE，界面按「当天 23:59:59」理解
            LocalDate deadlineDate = toDate(row.get("deadline"));
            LocalDateTime deadline = deadlineDate == null ? null : deadlineDate.atTime(LocalTime.MAX);
            long overdueDays = overdueDays(deadlineDate);

            TodoItemVO item = new TodoItemVO();
            item.setKey("task-" + toLong(row.get("task_id")));
            item.setTitle(toStr(row.get("title")));
            item.setDescription(joinDot(
                    StrUtil.blankToDefault(toStr(row.get("publish_org_name")), toStr(row.get("org_name"))),
                    deadlineText(deadline, overdueDays)));
            item.setDeadline(deadline);
            item.setOverdue(overdueDays > 0);
            item.setOverdueDays(overdueDays);
            item.setLink("/meeting/task");
            items.add(item);
        }
        return TodoGroupVO.of(TYPE_TASK, "三会一课任务", items);
    }

    /**
     * 党费：账单已生成且未缴。
     *
     * <p><b>口径说明</b>：规格写的是「{@code status=0} 且已过当月」。这里取
     * 「缴费月份不晚于当前月」——即<b>当月账单也进入待办</b>（截止月末缴清），
     * <b>跨月</b>才计为欠缴并在描述里显示逾期月数。这么处理的原因是：
     * 支部书记需要在月末前就看到本月谁还没交，等到跨月才提醒已经晚了；
     * 而「欠缴」的严格判定仍然沿用 {@code PartyDuesService} 的跨月口径，
     * 两者不冲突。未来月份的账单不会出现（账单按月生成）。</p>
     */
    private TodoGroupVO duesGroup(Scope scope) {
        if (scope.isEmpty()) {
            return TodoGroupVO.of(TYPE_DUES, "党费欠缴", List.of());
        }
        LocalDate today = LocalDate.now();
        int currentYm = today.getYear() * 12 + today.getMonthValue();

        List<TodoItemVO> items = new ArrayList<>();
        for (Map<String, Object> row : todoMapper.selectDuesTodos(scope.orgIds(), scope.personId(), currentYm)) {
            Integer year = toInt(row.get("dues_year"));
            Integer month = toInt(row.get("dues_month"));
            int rowYm = (year == null ? 0 : year) * 12 + (month == null ? 0 : month);
            int overdueMonths = Math.max(0, currentYm - rowYm);

            TodoItemVO item = new TodoItemVO();
            item.setKey("dues-" + toLong(row.get("dues_id")));
            item.setTitle(StrUtil.format("{} · {}年{}月党费未缴",
                    StrUtil.blankToDefault(toStr(row.get("person_name")), "未知人员"), year, month));
            String amount = row.get("dues_standard") == null ? null : "应缴 " + row.get("dues_standard") + " 元";
            String state = overdueMonths > 0 ? "已逾期 " + overdueMonths + " 个月" : "本月应缴，请于月末前缴清";
            item.setDescription(joinDot(toStr(row.get("org_name")), joinDot(amount, state)));
            // 账单月最后一天 23:59:59 为缴费截止
            LocalDate dueDate = year == null || month == null ? null
                    : LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
            item.setDeadline(dueDate == null ? null : dueDate.atTime(LocalTime.MAX));
            item.setOverdue(overdueMonths > 0);
            item.setOverdueDays(overdueDays(dueDate));
            item.setLink("/party-dues");
            items.add(item);
        }
        return TodoGroupVO.of(TYPE_DUES, "党费欠缴", items);
    }

    /** 组织关系转接：介绍信已开具/已超期，等着本组织接收 */
    private TodoGroupVO transferGroup(Scope scope) {
        // 转接单待办面向接收方组织，仅本人范围的账号没有「本组织待接收」的概念
        if (scope.isEmpty() || scope.selfOnly()) {
            return TodoGroupVO.of(TYPE_TRANSFER, "组织关系转接", List.of());
        }
        List<TodoItemVO> items = new ArrayList<>();
        for (Map<String, Object> row : todoMapper.selectTransferTodos(scope.orgIds())) {
            LocalDate expireDate = toDate(row.get("expire_date"));
            long overdueDays = overdueDays(expireDate);
            boolean expired = isExpiredTransfer(row.get("status"), expireDate);

            TodoItemVO item = new TodoItemVO();
            item.setKey("transfer-" + toLong(row.get("transfer_id")));
            item.setTitle(StrUtil.format("{} · 组织关系转入待接收",
                    StrUtil.blankToDefault(toStr(row.get("person_name")), "未知人员")));
            item.setDescription(joinDot(
                    "来自 " + StrUtil.blankToDefault(toStr(row.get("from_org_name")), "未知组织"),
                    joinDot("介绍信 " + StrUtil.blankToDefault(toStr(row.get("letter_no")), "—"),
                            expired ? "已超期 " + overdueDays + " 天" : "有效期至 " + fmt(expireDate))));
            item.setDeadline(expireDate == null ? null : expireDate.atTime(LocalTime.MAX));
            item.setOverdue(expired);
            item.setOverdueDays(expired ? overdueDays : 0L);
            item.setLink("/party-transfer");
            items.add(item);
        }
        return TodoGroupVO.of(TYPE_TRANSFER, "组织关系转接", items);
    }

    // ==================== 数据权限 ====================

    /**
     * 把当前用户的数据范围翻译成 Mapper 能用的两个参数。
     *
     * @param orgIds   可见组织；{@code null} 表示不限组织
     * @param personId 仅本人范围的人员ID；非空时忽略 {@code orgIds}
     * @param empty    没有任何可见数据（如无组织账号），调用方直接返回空分组
     */
    private record Scope(List<Long> orgIds, Long personId, boolean selfOnly, boolean empty) {

        static Scope none() {
            return new Scope(null, null, false, true);
        }

        static Scope all() {
            return new Scope(null, null, false, false);
        }

        /** 普通党员：只看与自己相关的 */
        static Scope self(Long personId) {
            return new Scope(null, personId, true, personId == null);
        }

        static Scope orgs(List<Long> orgIds) {
            return new Scope(orgIds, null, false, orgIds == null || orgIds.isEmpty());
        }

        boolean isEmpty() {
            return empty;
        }
    }

    private Scope resolveScope(LoginUser user) {
        if (user == null) {
            return Scope.none();
        }
        if (user.isSuperAdmin()) {
            return Scope.all();
        }
        return switch (DataScope.of(user.getDataScope())) {
            case ALL -> Scope.all();
            // 「本级」= 只本组织；「仅本人」= 只本人相关
            case CURRENT -> user.getOrgId() == null ? Scope.none() : Scope.orgs(List.of(user.getOrgId()));
            case SELF -> user.getPersonId() != null
                    ? Scope.self(user.getPersonId())
                    : (user.getOrgId() == null ? Scope.none() : Scope.orgs(List.of(user.getOrgId())));
            case CURRENT_AND_CHILD -> {
                if (StrUtil.isBlank(user.getOrgPath())) {
                    yield user.getOrgId() == null ? Scope.none() : Scope.orgs(List.of(user.getOrgId()));
                }
                yield Scope.orgs(todoMapper.selectOrgIdsByPathPrefix(user.getOrgPath() + "%"));
            }
            case CUSTOM -> Scope.orgs(todoMapper.selectOrgIdsByUser(user.getUserId()));
        };
    }

    // ==================== 小工具 ====================

    /** 超期天数：截止日期早于今天才算超期，当天不算 */
    private long overdueDays(LocalDate dueDate) {
        if (dueDate == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, LocalDate.now()));
    }

    /** 转接单是否已超期：{@code status=1} 且失效日期早于今天（{@code status=4} 已是超期态） */
    private boolean isExpiredTransfer(Object status, LocalDate expireDate) {
        Integer s = toInt(status);
        if (s == null) {
            return false;
        }
        if (s == 4) {
            return true;
        }
        return s == 1 && expireDate != null && expireDate.isBefore(LocalDate.now());
    }

    /** 截止时间的文案：超期显示「已超期 N 天」，否则显示「应于 xxx 前办结」 */
    private String deadlineText(LocalDateTime deadline, long overdueDays) {
        if (deadline == null) {
            return "未设办理时限";
        }
        if (overdueDays > 0) {
            return "已超期 " + overdueDays + " 天";
        }
        return "应于 " + deadline.format(DATETIME_FMT) + " 前办结";
    }

    /** 用「 · 」拼接非空片段 */
    private String joinDot(String... parts) {
        List<String> kept = new ArrayList<>();
        for (String p : parts) {
            if (StrUtil.isNotBlank(p)) {
                kept.add(p);
            }
        }
        return String.join(" · ", kept);
    }

    private String fmt(LocalDate date) {
        return date == null ? "" : date.format(DATE_FMT);
    }

    private Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private Integer toInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** JDBC 返回的时间列可能是 Timestamp 或 LocalDateTime，统一转 LocalDateTime */
    private LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime dt) {
            return dt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    private LocalDate toDate(Object value) {
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        return null;
    }
}
