package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.ResultCode;
import com.hparty.common.enums.DataScope;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.dto.TransferQuery;
import com.hparty.party.domain.entity.PartyTransfer;
import com.hparty.party.enums.TransferStatusEnum;
import com.hparty.party.enums.TransferTypeEnum;
import com.hparty.party.mapper.PartyLookupMapper;
import com.hparty.party.mapper.PartyTransferMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 组织关系转接服务。
 *
 * <p><b>业务主线</b>：发起（0=待提交）→ 开具介绍信（1=已开具，待接收）→ 接收方接收（2=已接收）。
 * 中途接收方可以拒绝（3，党员仍在原组织），发起方可以撤销（5），介绍信到期未落地则标记为
 * 超期（4）并进入「口袋党员」清单。</p>
 *
 * <p><b>两条容易写错的规则</b>：</p>
 * <ol>
 *   <li><b>开具介绍信不改组织关系</b>：只有「接收」才把 {@code party_person.org_id}
 *       改成目标组织。开具时就改的话，一旦被拒收，人就凭空从原组织消失了。</li>
 *   <li><b>接收时不重置发展党员流程</b>：流程图明确要求「工作、学习单位发生变动……
 *       接收单位应认真核对、做好接续培养，<b>培养教育时间可连续计算</b>」。
 *       因此只把 {@code dev_applicant.org_id} 迁到新组织，步骤、日期、材料一律保留。</li>
 * </ol>
 *
 * <p><b>数据权限</b>：转接单横跨两个组织，可见性判定与普通单组织表不同 ——
 * <b>原组织与目标组织的用户都应能看到</b>，所以列表用
 * {@code (from_org_id IN 可见组织 OR to_org_id IN 可见组织)}，
 * 而不是默认的 {@link DataScopeHelper#apply}（它只认单一 org 列）。
 * 「仅本人」范围（普通党员）退化为按 {@code person_id} 过滤，避免同支部党员互相看到转接单。
 * 按主键的详情与操作则要求<b>两个方向中至少一个</b>能通过
 * {@link DataScopeHelper#canAccessOrg}。</p>
 */
@Service
@RequiredArgsConstructor
public class PartyTransferService {

    /** 转接单号前缀 */
    private static final String TRANSFER_NO_PREFIX = "ZZ";
    /** 介绍信号前缀 */
    private static final String LETTER_NO_PREFIX = "JS";
    /** 默认介绍信有效期（天） */
    private static final int DEFAULT_VALID_DAYS = 90;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PartyTransferMapper transferMapper;
    private final PartyLookupMapper lookupMapper;

    // ==================== 查询 ====================

    /**
     * 分页查询转接单。
     * <p>可见范围：原组织与目标组织的用户都能看到（详见类注释）。</p>
     */
    public PageResult<Map<String, Object>> page(TransferQuery query) {
        LambdaQueryWrapper<PartyTransfer> wrapper = buildWrapper(query);
        wrapper.orderByDesc(PartyTransfer::getTransferId);
        Page<PartyTransfer> page = transferMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(page, this::toVO);
    }

    /**
     * 超期未落地清单（口袋党员）。
     *
     * <p>口径与界面一致：{@code status=1 且 expire_date < 今天} 的记录，
     * 加上已经被 {@link #refreshOverdueFlags()} 标记为 4 的记录。</p>
     */
    public List<Map<String, Object>> overdueList(TransferQuery query) {
        LocalDate today = LocalDate.now();
        // 状态由本接口自己决定，忽略调用方传来的 status，避免与下面的超期条件打架
        if (query != null) {
            query.setStatus(null);
        }
        LambdaQueryWrapper<PartyTransfer> wrapper = buildWrapper(query);
        wrapper.and(w -> w.eq(PartyTransfer::getStatus, TransferStatusEnum.ISSUED.getCode())
                        .lt(PartyTransfer::getExpireDate, today)
                        .or()
                        .eq(PartyTransfer::getStatus, TransferStatusEnum.EXPIRED.getCode()))
                .orderByAsc(PartyTransfer::getExpireDate);
        return transferMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    /**
     * 转接详情（含流转时间线）。
     *
     * @param transferId 转接ID
     * @throws BizException 单据不存在，或原组织/目标组织都不在当前用户数据权限内
     */
    public Map<String, Object> detail(Long transferId) {
        PartyTransfer transfer = get(transferId);
        Map<String, Object> vo = toVO(transfer);
        vo.put("timeline", buildTimeline(transfer));
        return vo;
    }

    /**
     * 按主键取转接单并做越权校验。
     *
     * <p><b>两个方向都要放行</b>：转出方要能看自己发起的单子，接收方要能看转给自己的单子。
     * 「仅本人」范围的账号额外要求 {@code person_id} 就是本人。</p>
     *
     * @param transferId 转接ID
     * @return 转接单实体
     * @throws BizException 单据不存在或无权访问
     */
    public PartyTransfer get(Long transferId) {
        if (transferId == null) {
            throw new BizException("转接ID不能为空");
        }
        PartyTransfer transfer = transferMapper.selectById(transferId);
        if (transfer == null) {
            throw new BizException("转接单不存在");
        }
        LoginUser user = SecurityUtils.getLoginUser();
        if (user.isSuperAdmin()) {
            return transfer;
        }
        // 仅本人范围：只认本人相关的单子，不能因为是同支部就放行
        DataScope scope = DataScope.of(user.getDataScope());
        if (scope == DataScope.SELF && user.getPersonId() != null) {
            if (!user.getPersonId().equals(transfer.getPersonId())) {
                throw new BizException(ResultCode.FORBIDDEN, "无权查看他人的组织关系转接单");
            }
            return transfer;
        }
        if (!DataScopeHelper.canAccessOrg(transfer.getFromOrgId())
                && !DataScopeHelper.canAccessOrg(transfer.getToOrgId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看其他党组织的组织关系转接单");
        }
        return transfer;
    }

    // ==================== 写入 ====================

    /**
     * 发起转接。
     *
     * <p>组织归属不信任客户端：原组织默认取人员档案中的现组织，目标组织必填，
     * 且当前用户必须对原组织有数据权限（否则等于替别的支部发起转出）。</p>
     *
     * @param transfer 转接单（transferType / personId / toOrgId 必填）
     * @return 新转接单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long add(PartyTransfer transfer) {
        if (transfer.getPersonId() == null) {
            throw new BizException("请选择转接人员");
        }
        Map<String, Object> person = transferMapper.selectPersonBrief(transfer.getPersonId());
        if (person == null) {
            throw new BizException("人员档案不存在");
        }
        Long personOrgId = toLong(person.get("org_id"));
        if (transfer.getFromOrgId() == null) {
            transfer.setFromOrgId(personOrgId);
        }
        if (transfer.getToOrgId() == null) {
            throw new BizException("请选择目标党组织");
        }
        if (Objects.equals(transfer.getFromOrgId(), transfer.getToOrgId())) {
            throw new BizException("目标党组织不能与原党组织相同");
        }
        if (!DataScopeHelper.canAccessOrg(transfer.getFromOrgId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权发起其他党组织的转出");
        }
        if (transfer.getTransferType() == null) {
            transfer.setTransferType(TransferTypeEnum.OUT.getCode());
        }
        if (transfer.getValidDays() == null || transfer.getValidDays() <= 0) {
            transfer.setValidDays(DEFAULT_VALID_DAYS);
        }

        transfer.setTransferId(null);
        transfer.setTransferNo(nextNo(TRANSFER_NO_PREFIX));
        transfer.setPersonName(toStr(person.get("name")));
        transfer.setFromOrgName(lookupMapper.selectOrgName(transfer.getFromOrgId()));
        transfer.setToOrgName(lookupMapper.selectOrgName(transfer.getToOrgId()));
        // 发起后处于「待提交」，尚未开具介绍信，组织关系不变更
        transfer.setStatus(TransferStatusEnum.PENDING.getCode());
        transfer.setLetterNo(null);
        transfer.setLetterDate(null);
        transfer.setExpireDate(null);
        transfer.setTransferDate(null);
        transfer.setRejectReason(null);
        fillHandler(transfer);
        transferMapper.insert(transfer);
        return transfer.getTransferId();
    }

    /**
     * 修改转接单。**仅「待提交」状态可改**，且原组织不允许通过修改接口变更
     * （否则等于把单子「过户」到别的支部）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(PartyTransfer transfer) {
        if (transfer.getTransferId() == null) {
            throw new BizException("转接ID不能为空");
        }
        PartyTransfer exists = get(transfer.getTransferId());
        if (!TransferStatusEnum.PENDING.getCode().equals(exists.getStatus())) {
            throw new BizException("只有「待提交」状态的转接单可以修改");
        }
        if (transfer.getToOrgId() != null && transfer.getToOrgId().equals(exists.getFromOrgId())) {
            throw new BizException("目标党组织不能与原党组织相同");
        }
        // 单号、原组织、状态等由流程控制，不接受客户端改写
        transfer.setTransferNo(exists.getTransferNo());
        transfer.setTransferType(exists.getTransferType());
        transfer.setPersonId(exists.getPersonId());
        transfer.setPersonName(exists.getPersonName());
        transfer.setFromOrgId(exists.getFromOrgId());
        transfer.setFromOrgName(exists.getFromOrgName());
        transfer.setStatus(exists.getStatus());
        transfer.setLetterNo(exists.getLetterNo());
        transfer.setLetterDate(exists.getLetterDate());
        transfer.setExpireDate(exists.getExpireDate());
        transfer.setTransferDate(exists.getTransferDate());
        if (transfer.getToOrgId() != null) {
            transfer.setToOrgName(lookupMapper.selectOrgName(transfer.getToOrgId()));
        }
        transferMapper.updateById(transfer);
    }

    /**
     * 删除转接单（仅「待提交」状态）。逻辑删除，单号可被后续单据复用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long transferId) {
        PartyTransfer transfer = get(transferId);
        if (!TransferStatusEnum.PENDING.getCode().equals(transfer.getStatus())) {
            throw new BizException("只有「待提交」状态的转接单可以删除");
        }
        transferMapper.deleteById(transferId);
    }

    /**
     * 开具介绍信（转出方操作）。
     *
     * <p>生成 {@code letter_no}、{@code letter_date} 取当天，按 {@code valid_days}
     * 推算 {@code expire_date}，状态置为 1。<b>此时不改动 {@code party_person.org_id}</b>。</p>
     *
     * @param transferId 转接ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long transferId) {
        PartyTransfer transfer = get(transferId);
        if (!TransferStatusEnum.PENDING.getCode().equals(transfer.getStatus())) {
            throw new BizException("只有「待提交」状态的转接单可以开具介绍信");
        }
        if (transfer.getFromOrgId() == null) {
            throw new BizException("转接单缺少原党组织，无法开具介绍信");
        }
        if (!DataScopeHelper.canAccessOrg(transfer.getFromOrgId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有原党组织可以开具介绍信");
        }
        int validDays = transfer.getValidDays() == null || transfer.getValidDays() <= 0
                ? DEFAULT_VALID_DAYS : transfer.getValidDays();
        LocalDate today = LocalDate.now();

        PartyTransfer update = new PartyTransfer();
        update.setTransferId(transferId);
        update.setLetterNo(nextNo(LETTER_NO_PREFIX));
        update.setLetterDate(today);
        update.setValidDays(validDays);
        update.setExpireDate(today.plusDays(validDays));
        update.setStatus(TransferStatusEnum.ISSUED.getCode());
        fillHandler(update);
        transferMapper.updateById(update);
    }

    /**
     * 接收（目标党组织操作）—— 组织关系真正变更的一步。
     *
     * <p>做三件事：把 {@code party_person.org_id} 改为目标组织；把该人「进行中」的
     * {@code dev_applicant.org_id} 一并迁过去（**不重置流程**）；把单据置为已接收并记录完成日期。</p>
     *
     * <p>{@code member_status} 与 apply/activist/candidate/probationary/full_member
     * 各项日期**保持不变** —— 转接不是重新入党。</p>
     *
     * @param transferId 转接ID
     * @return 给用户的提示语；若该人有进行中的发展党员流程，提示里会带上当前步骤
     */
    @Transactional(rollbackFor = Exception.class)
    public String accept(Long transferId) {
        PartyTransfer transfer = get(transferId);
        Integer status = transfer.getStatus();
        if (!TransferStatusEnum.ISSUED.getCode().equals(status)
                && !TransferStatusEnum.EXPIRED.getCode().equals(status)) {
            throw new BizException("只有「已开具」或「已超期」的转接单可以接收");
        }
        if (transfer.getToOrgId() == null) {
            throw new BizException("转接单未指定目标党组织，无法接收");
        }
        if (!DataScopeHelper.canAccessOrg(transfer.getToOrgId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有目标党组织可以接收该转接单");
        }

        // 1) 人员组织归属变更（member_status 与各里程碑日期不动）
        transferMapper.updatePersonOrg(transfer.getPersonId(), transfer.getToOrgId());

        // 2) 进行中的发展党员流程随人迁移，培养教育时间连续计算
        String extra = "";
        Map<String, Object> running = transferMapper.selectRunningApplicant(transfer.getPersonId());
        if (running != null) {
            Long applicantId = toLong(running.get("applicant_id"));
            if (applicantId != null) {
                transferMapper.updateApplicantOrg(applicantId, transfer.getToOrgId());
            }
            extra = StrUtil.format("；该同志有进行中的发展党员流程（当前 {}），培养教育时间按规定连续计算，"
                    + "流程已随组织关系一并迁入，未被重置", toStr(running.get("current_step")));
        }

        // 3) 单据状态
        PartyTransfer update = new PartyTransfer();
        update.setTransferId(transferId);
        update.setStatus(TransferStatusEnum.ACCEPTED.getCode());
        update.setTransferDate(LocalDate.now());
        update.setRejectReason(null);
        fillHandler(update);
        transferMapper.updateById(update);
        return "接收成功" + extra;
    }

    /**
     * 拒绝（目标党组织操作）。党员仍在原组织，不做任何人员数据变更。
     *
     * @param transferId 转接ID
     * @param reason     拒绝原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long transferId, String reason) {
        PartyTransfer transfer = get(transferId);
        if (!TransferStatusEnum.ISSUED.getCode().equals(transfer.getStatus())) {
            throw new BizException("只有「已开具」的转接单可以拒绝");
        }
        if (StrUtil.isBlank(reason)) {
            throw new BizException("请填写拒绝原因");
        }
        if (!DataScopeHelper.canAccessOrg(transfer.getToOrgId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有目标党组织可以拒绝该转接单");
        }
        PartyTransfer update = new PartyTransfer();
        update.setTransferId(transferId);
        update.setStatus(TransferStatusEnum.REJECTED.getCode());
        update.setRejectReason(reason);
        fillHandler(update);
        transferMapper.updateById(update);
    }

    /**
     * 撤销（转出方操作）。「待提交」与「已开具」都可撤销，撤销后组织关系不变。
     *
     * @param transferId 转接ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long transferId) {
        PartyTransfer transfer = get(transferId);
        Integer status = transfer.getStatus();
        if (!TransferStatusEnum.PENDING.getCode().equals(status)
                && !TransferStatusEnum.ISSUED.getCode().equals(status)
                && !TransferStatusEnum.EXPIRED.getCode().equals(status)) {
            throw new BizException("只有「待提交」「已开具」「已超期」的转接单可以撤销");
        }
        if (!DataScopeHelper.canAccessOrg(transfer.getFromOrgId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有原党组织可以撤销该转接单");
        }
        PartyTransfer update = new PartyTransfer();
        update.setTransferId(transferId);
        update.setStatus(TransferStatusEnum.REVOKED.getCode());
        fillHandler(update);
        transferMapper.updateById(update);
    }

    /**
     * 把已超期的转接单刷成 {@code status=4}。
     *
     * <p>本项目的既定做法是「读取时现算、定时任务负责落库」（见 {@code PartyDuesService}）：
     * 界面判定走 {@link #computeOverdue}，永远不会因为任务没跑而显示错的结论；
     * 本方法只是把同样的口径落库，供「口袋党员」清单做带索引的批量筛查。</p>
     *
     * <p>项目当前没有配置 {@code @Scheduled}，因此该方法由超期清单接口按需触发（幂等）。</p>
     *
     * @return 被标记为超期的条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int refreshOverdueFlags() {
        return transferMapper.markExpired(LocalDate.now());
    }

    // ==================== 私有方法 ====================

    /** 列表条件：数据权限 + 各筛选字段 */
    private LambdaQueryWrapper<PartyTransfer> buildWrapper(TransferQuery query) {
        LambdaQueryWrapper<PartyTransfer> wrapper = new LambdaQueryWrapper<>();
        applyVisibility(wrapper);
        if (query != null) {
            if (query.getTransferType() != null) {
                wrapper.eq(PartyTransfer::getTransferType, query.getTransferType());
            }
            if (query.getStatus() != null) {
                wrapper.eq(PartyTransfer::getStatus, query.getStatus());
            }
            if (StrUtil.isNotBlank(query.getPersonName())) {
                wrapper.like(PartyTransfer::getPersonName, query.getPersonName().trim());
            }
            if (StrUtil.isNotBlank(query.getTransferNo())) {
                String kw = query.getTransferNo().trim();
                wrapper.and(w -> w.like(PartyTransfer::getTransferNo, kw).or().like(PartyTransfer::getLetterNo, kw));
            }
            if (query.getFromOrgId() != null) {
                wrapper.eq(PartyTransfer::getFromOrgId, query.getFromOrgId());
            }
            if (query.getToOrgId() != null) {
                wrapper.eq(PartyTransfer::getToOrgId, query.getToOrgId());
            }
            if (query.getBeginDate() != null) {
                wrapper.ge(PartyTransfer::getLetterDate, query.getBeginDate());
            }
            if (query.getEndDate() != null) {
                wrapper.le(PartyTransfer::getLetterDate, query.getEndDate());
            }
        }
        return wrapper;
    }

    /**
     * 追加数据权限条件。
     *
     * <p>不能直接用 {@link DataScopeHelper#apply}：它只认单一 org 列，
     * 而转接单横跨原组织与目标组织，两边的人都应该看得到。这里取「我可见的组织 ID 列表」
     * 后拼 {@code (from_org_id IN ... OR to_org_id IN ...)}。</p>
     */
    private void applyVisibility(LambdaQueryWrapper<PartyTransfer> wrapper) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null || user.isSuperAdmin()) {
            return;
        }
        DataScope scope = DataScope.of(user.getDataScope());
        // 「仅本人」：普通党员只应看到自己相关的转接单，而不是整个支部的
        if (scope == DataScope.SELF && user.getPersonId() != null) {
            wrapper.eq(PartyTransfer::getPersonId, user.getPersonId());
            return;
        }
        List<Long> orgIds = visibleOrgIds(user, scope);
        if (orgIds == null) {
            return;
        }
        if (orgIds.isEmpty()) {
            // 没有任何可见组织（如超管之外的无组织账号）→ 查不到数据
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(w -> w.in(PartyTransfer::getFromOrgId, orgIds).or().in(PartyTransfer::getToOrgId, orgIds));
    }

    /**
     * 当前用户可见的组织 ID 列表。
     *
     * <p>与 {@link DataScopeHelper#apply} 的 switch 保持同一套口径，
     * 只是把「拼 SQL 条件」换成「返回 ID 列表」，以便用在 OR 组合里。</p>
     *
     * @return {@code null} 表示不限组织
     */
    private List<Long> visibleOrgIds(LoginUser user, DataScope scope) {
        return switch (scope) {
            case ALL -> null;
            case CURRENT, SELF -> user.getOrgId() == null ? List.of() : List.of(user.getOrgId());
            case CURRENT_AND_CHILD -> {
                if (StrUtil.isBlank(user.getOrgPath())) {
                    yield user.getOrgId() == null ? List.<Long>of() : List.of(user.getOrgId());
                }
                yield lookupMapper.selectOrgIdsByPathPrefix(user.getOrgPath() + "%");
            }
            case CUSTOM -> lookupMapper.selectOrgIdsByUser(user.getUserId());
        };
    }

    /** 流转时间线：发起 → 开具介绍信 → 接收/拒绝/撤销 */
    private List<Map<String, Object>> buildTimeline(PartyTransfer t) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(node("发起转接", TransferStatusEnum.PENDING.getLabel(), t.getCreateBy(),
                formatTime(t.getCreateTime()),
                TransferTypeEnum.labelOf(t.getTransferType()) + " · " + StrUtil.blankToDefault(t.getReason(), "未填写事由")));
        Integer status = t.getStatus();
        if (t.getLetterDate() != null) {
            // handler_* 只有一个字段，接收/拒绝时会覆盖成办理人。
            // 因此仅在单据仍处于「已开具/已超期」（尚未被接收方处理）时，
            // 才把 handlerName 当作开信人展示，避免出现「接的人成了开信的人」。
            boolean stillPending = TransferStatusEnum.ISSUED.getCode().equals(status)
                    || TransferStatusEnum.EXPIRED.getCode().equals(status);
            timeline.add(node("开具介绍信", TransferStatusEnum.ISSUED.getLabel(),
                    stillPending ? t.getHandlerName() : null, fmtDate(t.getLetterDate()),
                    "介绍信号 " + StrUtil.blankToDefault(t.getLetterNo(), "—")
                            + " · 有效期 " + t.getValidDays() + " 天 · 至 " + fmtDate(t.getExpireDate())));
        }
        if (TransferStatusEnum.ACCEPTED.getCode().equals(status)) {
            timeline.add(node("接收", TransferStatusEnum.ACCEPTED.getLabel(), t.getHandlerName(),
                    fmtDate(t.getTransferDate()),
                    "组织关系已转入 " + StrUtil.blankToDefault(t.getToOrgName(), "—")));
        } else if (TransferStatusEnum.REJECTED.getCode().equals(status)) {
            timeline.add(node("拒绝", TransferStatusEnum.REJECTED.getLabel(), t.getHandlerName(),
                    formatTime(t.getUpdateTime()),
                    StrUtil.blankToDefault(t.getRejectReason(), "未填写原因")));
        } else if (TransferStatusEnum.REVOKED.getCode().equals(status)) {
            timeline.add(node("撤销", TransferStatusEnum.REVOKED.getLabel(), t.getUpdateBy(),
                    formatTime(t.getUpdateTime()), "发起方撤销，组织关系未变更"));
        } else if (computeOverdue(t)) {
            timeline.add(node("超期", TransferStatusEnum.EXPIRED.getLabel(), null, fmtDate(t.getExpireDate()),
                    "介绍信已过有效期 " + overdueDays(t) + " 天，尚未落地，纳入口袋党员清单"));
        }
        return timeline;
    }

    private String fmtDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FMT);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(DATETIME_FMT);
    }

    private Map<String, Object> node(String title, String status, String operator, String time, String desc) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("title", title);
        node.put("status", status);
        node.put("operator", operator);
        node.put("time", time);
        node.put("description", desc);
        return node;
    }

    /**
     * 生成业务单号：{@code 前缀-年份-4位流水}。
     *
     * <p>转接单号（{@code ZZ-}）与介绍信号（{@code JS-}）各查各的列，互不干扰。
     * 以当年已有条数 +1 作为流水号。并发下可能与唯一索引冲突（概率极低，
     * 且只影响新单据的创建），由 {@code uk_transfer_no_alive} 兜底，
     * 不做额外的分布式锁 —— 与项目其余模块的取舍一致。</p>
     *
     * @param prefix 前缀，{@link #TRANSFER_NO_PREFIX} 或 {@link #LETTER_NO_PREFIX}
     */
    private String nextNo(String prefix) {
        int year = LocalDate.now().getYear();
        String yearPrefix = prefix + "-" + year + "-%";
        boolean letter = LETTER_NO_PREFIX.equals(prefix);
        int seq = (letter
                ? transferMapper.countByLetterNoPrefix(yearPrefix)
                : transferMapper.countByNoPrefix(yearPrefix)) + 1;
        return String.format("%s-%d-%04d", prefix, year, seq);
    }

    /** 记录经办人（人员档案 ID 与姓名，均可能为空） */
    private void fillHandler(PartyTransfer transfer) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null) {
            return;
        }
        if (transfer.getHandlerId() == null) {
            transfer.setHandlerId(user.getPersonId());
        }
        if (StrUtil.isBlank(transfer.getHandlerName())) {
            transfer.setHandlerName(StrUtil.blankToDefault(user.getPersonName(), user.getNickName()));
        }
    }

    /**
     * 是否已超期：已开具且失效日期早于今天。
     *
     * <p>与 {@code PartyDuesService.computeOverdue} 一样「读取时现算」，
     * 不依赖可能没有定时任务刷新的 {@code status=4} 标记列。</p>
     */
    private boolean computeOverdue(PartyTransfer t) {
        if (!TransferStatusEnum.ISSUED.getCode().equals(t.getStatus())
                && !TransferStatusEnum.EXPIRED.getCode().equals(t.getStatus())) {
            return false;
        }
        return t.getExpireDate() != null && t.getExpireDate().isBefore(LocalDate.now());
    }

    /** 超期天数 */
    private long overdueDays(PartyTransfer t) {
        if (t.getExpireDate() == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(t.getExpireDate(), LocalDate.now()));
    }

    private Map<String, Object> toVO(PartyTransfer t) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("transferId", t.getTransferId());
        item.put("transferNo", t.getTransferNo());
        item.put("transferType", t.getTransferType());
        item.put("transferTypeLabel", TransferTypeEnum.labelOf(t.getTransferType()));
        item.put("personId", t.getPersonId());
        item.put("personName", t.getPersonName());
        item.put("fromOrgId", t.getFromOrgId());
        item.put("fromOrgName", t.getFromOrgName());
        item.put("toOrgId", t.getToOrgId());
        item.put("toOrgName", t.getToOrgName());
        item.put("reason", t.getReason());
        item.put("letterNo", t.getLetterNo());
        item.put("letterDate", t.getLetterDate());
        item.put("validDays", t.getValidDays());
        item.put("expireDate", t.getExpireDate());
        item.put("status", t.getStatus());
        item.put("statusLabel", TransferStatusEnum.labelOf(t.getStatus()));
        item.put("overdue", computeOverdue(t));
        item.put("overdueDays", overdueDays(t));
        item.put("transferDate", t.getTransferDate());
        item.put("handlerId", t.getHandlerId());
        item.put("handlerName", t.getHandlerName());
        item.put("rejectReason", t.getRejectReason());
        item.put("fileId", t.getFileId());
        item.put("fileUrl", t.getFileUrl());
        item.put("remark", t.getRemark());
        item.put("createTime", t.getCreateTime());
        return item;
    }

    /** JDBC 返回的数值可能是 Integer / Long / BigDecimal，统一转 Long */
    private Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
