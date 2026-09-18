package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.dto.ReviewOrgEvalDTO;
import com.hparty.party.domain.dto.ReviewPeerEvalDTO;
import com.hparty.party.domain.dto.ReviewQuery;
import com.hparty.party.domain.dto.ReviewSelfEvalDTO;
import com.hparty.party.domain.entity.PartyReview;
import com.hparty.party.domain.entity.PartyReviewDetail;
import com.hparty.party.enums.ReviewGradeEnum;
import com.hparty.party.enums.ReviewStatusEnum;
import com.hparty.party.mapper.PartyReviewDetailMapper;
import com.hparty.party.mapper.PartyReviewLookupMapper;
import com.hparty.party.mapper.PartyReviewMapper;
import com.hparty.party.util.ReviewScoreRule;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 民主评议党员服务。
 *
 * <p><b>流程</b>：创建批次（草稿）→ 启动（按组织下党员生成明细）→ 党员自评 → 党员互评
 * → 组织评定 → 公示 → 完成。批次状态随明细完成情况**自动向前跃迁**，
 * 不需要人工点「下一步」（见 {@link #syncStatus}）。</p>
 *
 * <p><b>计分</b>：权重与等次阈值集中在 {@link ReviewScoreRule}，
 * 综合得分 = 自评 20% + 互评 40% + 群众评议 20% + 组织评定 20%（缺项按已有项归一化）。</p>
 *
 * <p><b>两条硬规则</b>：互评**不能给自己打分**；优秀等次人数不得超过党员总数的 30%，
 * 超额直接拒绝并给出具体人数。</p>
 *
 * <p><b>已知取舍</b>：{@code party_review_detail} 只存互评平均分与参与人数，
 * 没有逐条保存「谁给谁打了多少分」的位置（表结构由需求规格给定）。因此互评采用
 * **增量平均**：新平均 = (旧平均 × 旧人数 + 本次得分) / (旧人数 + 1)。
 * 副作用是同一名打分人重复提交会被重复计入 —— 与「每人只投一次」的真实规则相比是宽松的。</p>
 */
@Service
@RequiredArgsConstructor
public class PartyReviewService {

    private final PartyReviewMapper reviewMapper;
    private final PartyReviewDetailMapper detailMapper;
    private final PartyReviewLookupMapper lookupMapper;

    // ------------------------------------------------------------------
    // 批次
    // ------------------------------------------------------------------

    /** 分页查询评议批次。 */
    public PageResult<Map<String, Object>> page(ReviewQuery query) {
        LambdaQueryWrapper<PartyReview> wrapper = new LambdaQueryWrapper<>();
        // 本表无 person_id 列，personColumn 传 null
        DataScopeHelper.apply(wrapper, "org_id", null);
        if (query.getOrgId() != null) {
            wrapper.eq(PartyReview::getOrgId, query.getOrgId());
        }
        if (query.getReviewYear() != null) {
            wrapper.eq(PartyReview::getReviewYear, query.getReviewYear());
        }
        if (query.getStatus() != null) {
            wrapper.eq(PartyReview::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.like(PartyReview::getTitle, query.getKeyword().trim());
        }
        wrapper.orderByDesc(PartyReview::getReviewYear).orderByDesc(PartyReview::getReviewId);

        Page<PartyReview> page = reviewMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> orgNames = loadOrgNames(page.getRecords().stream()
                .map(PartyReview::getOrgId).toList());
        // 一次性把整页批次的明细捞出来分组，避免逐行再查一次
        Map<Long, Map<String, Object>> progresses = progressOfBatch(page.getRecords().stream()
                .map(PartyReview::getReviewId).toList());
        return PageResult.of(page, r -> toVO(r, orgNames,
                progresses.getOrDefault(r.getReviewId(), emptyProgress())));
    }

    /** 批次详情（含明细与三项完成率）。 */
    public Map<String, Object> detail(Long reviewId) {
        PartyReview review = get(reviewId);
        List<PartyReviewDetail> details = listDetails(reviewId);
        Map<String, Object> vo = toVO(review, loadOrgNames(List.of(review.getOrgId())), progressOf(review));
        vo.put("details", details);
        return vo;
    }

    /** 按主键取批次并做越权校验。 */
    public PartyReview get(Long reviewId) {
        if (reviewId == null) {
            throw new BizException("评议批次ID不能为空");
        }
        PartyReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException("评议批次不存在");
        }
        if (!DataScopeHelper.canAccessOrg(review.getOrgId())) {
            throw BizException.forbidden("无权查看其他党组织的评议批次");
        }
        return review;
    }

    /** 创建批次（草稿）。 */
    @Transactional(rollbackFor = Exception.class)
    public Long add(PartyReview review) {
        if (StrUtil.isBlank(review.getTitle())) {
            throw new BizException("请填写评议标题");
        }
        if (review.getReviewYear() == null) {
            throw new BizException("请填写评议年度");
        }
        // 组织 ID 不无条件信任客户端
        Long orgId = review.getOrgId() != null ? review.getOrgId() : SecurityUtils.getOrgId();
        if (orgId == null) {
            throw new BizException("当前账号未分配所属党组织，无法创建。");
        }
        if (!DataScopeHelper.canAccessOrg(orgId)) {
            throw BizException.forbidden("无权在指定党组织下创建评议批次");
        }

        Long exists = reviewMapper.selectCount(new LambdaQueryWrapper<PartyReview>()
                .eq(PartyReview::getOrgId, orgId)
                .eq(PartyReview::getReviewYear, review.getReviewYear()));
        if (exists != null && exists > 0) {
            throw new BizException(review.getReviewYear() + " 年度该党组织的民主评议批次已存在");
        }

        review.setReviewId(null);
        review.setOrgId(orgId);
        review.setStatus(ReviewStatusEnum.DRAFT.getCode());
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底（并发下「先查后插」会漏）
            throw new BizException(review.getReviewYear() + " 年度该党组织的民主评议批次已存在");
        }
        return review.getReviewId();
    }

    /** 修改批次。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(PartyReview review) {
        if (review.getReviewId() == null) {
            throw new BizException("评议批次ID不能为空");
        }
        PartyReview exists = get(review.getReviewId());
        // 组织与年度是唯一键的一部分，不允许改
        review.setOrgId(exists.getOrgId());
        review.setReviewYear(exists.getReviewYear());

        if (review.getStatus() != null && !review.getStatus().equals(exists.getStatus())) {
            // 状态只允许人工推进到「已公示 / 已完成」，其余由流程自动跃迁
            boolean manual = Objects.equals(review.getStatus(), ReviewStatusEnum.PUBLICITY.getCode())
                    || Objects.equals(review.getStatus(), ReviewStatusEnum.FINISHED.getCode());
            if (!manual) {
                throw new BizException("状态只能推进为「已公示」或「已完成」");
            }
            if (review.getStatus() < exists.getStatus()) {
                throw new BizException("评议状态不能回退");
            }
        }
        reviewMapper.updateById(review);
    }

    /** 删除批次（仅草稿）。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long reviewId) {
        PartyReview review = get(reviewId);
        if (!Objects.equals(review.getStatus(), ReviewStatusEnum.DRAFT.getCode())) {
            throw new BizException("只有草稿状态的评议批次才能删除");
        }
        reviewMapper.deleteById(reviewId);
        detailMapper.delete(new LambdaQueryWrapper<PartyReviewDetail>()
                .eq(PartyReviewDetail::getReviewId, reviewId));
    }

    // ------------------------------------------------------------------
    // 启动：生成明细
    // ------------------------------------------------------------------

    /**
     * 启动评议：按组织（含整棵子树）下的党员生成明细行，状态置为「自评中」。
     */
    @Transactional(rollbackFor = Exception.class)
    public int start(Long reviewId) {
        PartyReview review = get(reviewId);
        if (!Objects.equals(review.getStatus(), ReviewStatusEnum.DRAFT.getCode())) {
            throw new BizException("只有草稿状态的评议批次才能启动");
        }

        List<Map<String, Object>> members = lookupMapper.selectMembersOfOrgTree(review.getOrgId());
        if (members.isEmpty()) {
            throw new BizException("该组织下没有党员（预备党员/正式党员），无法启动评议");
        }

        // 全量替换：先清空旧明细，再按当前党员名册重建
        detailMapper.delete(new LambdaQueryWrapper<PartyReviewDetail>()
                .eq(PartyReviewDetail::getReviewId, reviewId));

        for (Map<String, Object> member : members) {
            PartyReviewDetail detail = new PartyReviewDetail();
            detail.setReviewId(reviewId);
            detail.setPersonId(asLong(member.get("person_id")));
            detail.setPersonName(asString(member.get("name")));
            detail.setOrgId(asLong(member.get("org_id")));
            detail.setPeerCount(0);
            detailMapper.insert(detail);
        }

        PartyReview update = new PartyReview();
        update.setReviewId(reviewId);
        update.setStatus(ReviewStatusEnum.SELF.getCode());
        // 优秀名额未指定时按 30% 自动推算，供组织评定时校验
        if (review.getExcellentQuota() == null) {
            update.setExcellentQuota(ReviewScoreRule.excellentQuotaOf(members.size()));
        }
        reviewMapper.updateById(update);
        return members.size();
    }

    /** 明细列表。 */
    public List<PartyReviewDetail> listDetails(Long reviewId) {
        get(reviewId);
        return detailMapper.selectList(new LambdaQueryWrapper<PartyReviewDetail>()
                .eq(PartyReviewDetail::getReviewId, reviewId)
                .orderByAsc(PartyReviewDetail::getOrgId)
                .orderByAsc(PartyReviewDetail::getDetailId));
    }

    // ------------------------------------------------------------------
    // 自评 / 互评 / 组织评定
    // ------------------------------------------------------------------

    /**
     * 提交自评。**只能给本人打分** —— 打分对象从登录会话取，请求体里没有该字段。
     *
     * <p>自评在「公示」之前的任何阶段都允许补交：状态是由**完成率**自动跃迁的，
     * 若互评先于自评做满，批次会直接跳到「组织评定中」，
     * 此时把自评一刀切禁掉会让迟到的人永远补不上。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitSelfEval(Long reviewId, ReviewSelfEvalDTO dto) {
        PartyReview review = get(reviewId);
        requireStageOpen(review, "当前评议已公示，无法再提交自评");

        Long personId = SecurityUtils.getLoginUser().getPersonId();
        if (personId == null) {
            throw new BizException("当前账号未关联人员档案，无法参与民主评议");
        }
        PartyReviewDetail detail = findDetail(reviewId, personId);
        if (detail == null) {
            throw new BizException("您不在本次民主评议名单中");
        }

        detail.setSelfScore(dto.getSelfScore());
        detail.setSelfComment(dto.getSelfComment());
        detail.setSelfTime(LocalDateTime.now());
        detail.setTotalScore(ReviewScoreRule.totalScore(detail.getSelfScore(), detail.getPeerScore(),
                detail.getMassScore(), detail.getOrgScore()));
        detailMapper.updateById(detail);

        syncStatus(review);
    }

    /**
     * 提交互评（批量）。**不能给自己打分**：请求里出现本人 person_id 时整批拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public int submitPeerEval(Long reviewId, ReviewPeerEvalDTO dto) {
        PartyReview review = get(reviewId);
        if (review.getStatus() < ReviewStatusEnum.SELF.getCode()) {
            throw new BizException("评议尚未启动，无法提交互评");
        }
        requireStageOpen(review, "当前评议已公示，无法再提交互评");
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BizException("请填写互评打分");
        }

        Long raterPersonId = SecurityUtils.getLoginUser().getPersonId();
        if (raterPersonId == null) {
            throw new BizException("当前账号未关联人员档案，无法参与互评");
        }

        Set<Long> submitted = new HashSet<>();
        int count = 0;
        for (ReviewPeerEvalDTO.Item item : dto.getItems()) {
            if (Objects.equals(item.getPersonId(), raterPersonId)) {
                throw new BizException("不能给自己打分");
            }
            if (!submitted.add(item.getPersonId())) {
                throw new BizException("同一名被评议人在一次提交中只能出现一次");
            }
            PartyReviewDetail detail = findDetail(reviewId, item.getPersonId());
            if (detail == null) {
                throw new BizException("被评议人不在本次评议名单中");
            }

            // 增量平均：新平均 = (旧平均 × 旧人数 + 本次得分) / (旧人数 + 1)
            BigDecimal oldScore = detail.getPeerScore() == null ? BigDecimal.ZERO : detail.getPeerScore();
            int oldCount = detail.getPeerCount() == null ? 0 : detail.getPeerCount();
            BigDecimal newCount = BigDecimal.valueOf(oldCount + 1L);
            BigDecimal newAvg = oldScore.multiply(BigDecimal.valueOf(oldCount))
                    .add(item.getScore())
                    .divide(newCount, 1, RoundingMode.HALF_UP);

            detail.setPeerScore(newAvg);
            detail.setPeerCount(oldCount + 1);
            detail.setTotalScore(ReviewScoreRule.totalScore(detail.getSelfScore(), newAvg,
                    detail.getMassScore(), detail.getOrgScore()));
            detailMapper.updateById(detail);
            count++;
        }

        syncStatus(review);
        return count;
    }

    /**
     * 组织评定（批量）：录入组织评定得分与群众评议得分，定等次。
     *
     * <p>优秀等次超额时报错并给出实际人数与名额上限。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int submitOrgEval(Long reviewId, ReviewOrgEvalDTO dto) {
        PartyReview review = get(reviewId);
        if (review.getStatus() < ReviewStatusEnum.SELF.getCode()) {
            throw new BizException("评议尚未启动，无法组织评定");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BizException("请填写组织评定");
        }

        List<PartyReviewDetail> all = listDetails(reviewId);
        Map<Long, PartyReviewDetail> byId = new LinkedHashMap<>();
        for (PartyReviewDetail d : all) {
            byId.put(d.getDetailId(), d);
        }

        // 先校验并把「本次评定之后」的优秀人数算出来，再决定是否放行，避免改到一半才失败
        Map<Long, ReviewOrgEvalDTO.Item> items = new LinkedHashMap<>();
        for (ReviewOrgEvalDTO.Item item : dto.getItems()) {
            if (!byId.containsKey(item.getDetailId())) {
                throw new BizException("评议明细不存在或不属于该批次");
            }
            if (items.put(item.getDetailId(), item) != null) {
                throw new BizException("同一条明细在一次提交中只能出现一次");
            }
        }

        int excellent = 0;
        for (PartyReviewDetail d : all) {
            ReviewOrgEvalDTO.Item item = items.get(d.getDetailId());
            Integer grade = item == null ? d.getGrade() : resolveGrade(item, d);
            if (Objects.equals(grade, ReviewGradeEnum.EXCELLENT.getCode())) {
                excellent++;
            }
        }

        int quota = effectiveQuota(review, all.size());
        if (excellent > quota) {
            throw new BizException("优秀等次 " + excellent + " 人，超出名额上限 " + quota
                    + " 人（优秀比例不超过党员总数的 30%），请调整后再提交");
        }

        int count = 0;
        for (ReviewOrgEvalDTO.Item item : dto.getItems()) {
            PartyReviewDetail detail = byId.get(item.getDetailId());
            if (item.getMassScore() != null) {
                detail.setMassScore(item.getMassScore());
            }
            if (item.getOrgScore() != null) {
                detail.setOrgScore(item.getOrgScore());
            }
            detail.setTotalScore(ReviewScoreRule.totalScore(detail.getSelfScore(), detail.getPeerScore(),
                    detail.getMassScore(), detail.getOrgScore()));
            detail.setGrade(resolveGrade(item, detail));
            if (StrUtil.isNotBlank(item.getOrgComment())) {
                detail.setOrgComment(item.getOrgComment());
            }
            if (StrUtil.isNotBlank(item.getDispose())) {
                detail.setDispose(item.getDispose());
            }
            if (detail.getGrade() == null) {
                throw new BizException("请填写组织评定得分后再提交");
            }
            detailMapper.updateById(detail);
            count++;
        }

        syncStatus(review);
        return count;
    }

    /** 等次：组织显式指定优先，否则按综合得分阈值判定。 */
    private Integer resolveGrade(ReviewOrgEvalDTO.Item item, PartyReviewDetail detail) {
        if (item.getGrade() != null) {
            return item.getGrade();
        }
        BigDecimal total = ReviewScoreRule.totalScore(
                detail.getSelfScore(),
                detail.getPeerScore(),
                item.getMassScore() != null ? item.getMassScore() : detail.getMassScore(),
                item.getOrgScore() != null ? item.getOrgScore() : detail.getOrgScore());
        ReviewGradeEnum grade = ReviewGradeEnum.of(total);
        return grade == null ? null : grade.getCode();
    }

    /**
     * 优秀名额：批次上显式指定的优先，没指定就按党员总数的 30% 推算。
     *
     * <p>演示数据或历史数据里 {@code excellent_quota} 可能为空，
     * 若直接回 null，「超额提示」和页面上的名额展示都会失效。</p>
     */
    private int effectiveQuota(PartyReview review, int memberCount) {
        return review.getExcellentQuota() != null
                ? review.getExcellentQuota()
                : ReviewScoreRule.excellentQuotaOf(memberCount);
    }

    /**
     * 评议是否还处在「可以提交评价」的阶段（公示之前）。
     *
     * <p>阶段划分靠完成率自动跃迁，因此不能按「等于某个状态」来卡 ——
     * 互评做满会把批次直接推进到「组织评定中」，此时自评、互评都应当还能补交。</p>
     */
    private void requireStageOpen(PartyReview review, String message) {
        if (review.getStatus() == null
                || review.getStatus() < ReviewStatusEnum.SELF.getCode()
                || review.getStatus() >= ReviewStatusEnum.PUBLICITY.getCode()) {
            throw new BizException(message);
        }
    }

    private PartyReviewDetail findDetail(Long reviewId, Long personId) {
        if (personId == null) {
            return null;
        }
        return detailMapper.selectOne(new LambdaQueryWrapper<PartyReviewDetail>()
                .eq(PartyReviewDetail::getReviewId, reviewId)
                .eq(PartyReviewDetail::getPersonId, personId)
                .last("LIMIT 1"));
    }

    // ------------------------------------------------------------------
    // 状态自动跃迁
    // ------------------------------------------------------------------

    /**
     * 按明细完成情况把批次状态向前推进一步（只进不退）。
     *
     * <p>全部自评完 → 互评中；全部被互评过 → 组织评定中；全部定等次 → 已公示。
     * 「已公示 → 已完成」不自动，需支部在公示期满后手工置位。</p>
     */
    private void syncStatus(PartyReview review) {
        if (review.getStatus() == null || review.getStatus() < ReviewStatusEnum.SELF.getCode()
                || review.getStatus() >= ReviewStatusEnum.PUBLICITY.getCode()) {
            return;
        }
        Map<String, Object> progress = progressOf(review);
        long total = (long) progress.get("total");
        if (total == 0) {
            return;
        }
        int target;
        if ((long) progress.get("orgDone") == total) {
            target = ReviewStatusEnum.PUBLICITY.getCode();
        } else if ((long) progress.get("peerDone") == total) {
            target = ReviewStatusEnum.ORG.getCode();
        } else if ((long) progress.get("selfDone") == total) {
            target = ReviewStatusEnum.PEER.getCode();
        } else {
            return;
        }
        if (target > review.getStatus()) {
            PartyReview update = new PartyReview();
            update.setReviewId(review.getReviewId());
            update.setStatus(target);
            reviewMapper.updateById(update);
        }
    }

    /** 三项完成情况。 */
    private Map<String, Object> progressOf(PartyReview review) {
        List<PartyReviewDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<PartyReviewDetail>()
                        .eq(PartyReviewDetail::getReviewId, review.getReviewId()));
        return summarize(details);
    }

    /** 批量：评议批次 ID → 完成情况，一次查完避免列表页 N+1。 */
    private Map<Long, Map<String, Object>> progressOfBatch(List<Long> reviewIds) {
        List<Long> ids = reviewIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<PartyReviewDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<PartyReviewDetail>()
                        .in(PartyReviewDetail::getReviewId, ids));
        Map<Long, List<PartyReviewDetail>> grouped = new LinkedHashMap<>();
        for (PartyReviewDetail d : details) {
            grouped.computeIfAbsent(d.getReviewId(), k -> new ArrayList<>()).add(d);
        }
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Long id : ids) {
            result.put(id, summarize(grouped.getOrDefault(id, List.of())));
        }
        return result;
    }

    /** 明细集合 → 完成率与优秀人数。 */
    private Map<String, Object> summarize(List<PartyReviewDetail> details) {
        long total = details.size();
        long selfDone = details.stream().filter(d -> d.getSelfScore() != null).count();
        long peerDone = details.stream().filter(d -> d.getPeerCount() != null && d.getPeerCount() > 0).count();
        long orgDone = details.stream().filter(d -> d.getGrade() != null).count();
        long excellent = details.stream()
                .filter(d -> Objects.equals(d.getGrade(), ReviewGradeEnum.EXCELLENT.getCode())).count();

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("total", total);
        progress.put("selfDone", selfDone);
        progress.put("peerDone", peerDone);
        progress.put("orgDone", orgDone);
        progress.put("excellentUsed", excellent);
        progress.put("selfRate", rate(selfDone, total));
        progress.put("peerRate", rate(peerDone, total));
        progress.put("orgRate", rate(orgDone, total));
        return progress;
    }

    /** 空进度（批次还没生成明细时）。 */
    private Map<String, Object> emptyProgress() {
        return summarize(List.of());
    }

    private BigDecimal rate(long done, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(done).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 统计
    // ------------------------------------------------------------------

    /**
     * 等次分布统计。
     *
     * @param reviewId 指定批次；为 null 时取当前数据权限内最近的一个批次
     * @return 总数、各等次人数与占比、三项完成率
     */
    public Map<String, Object> statistics(Long reviewId) {
        PartyReview review;
        if (reviewId != null) {
            review = get(reviewId);
        } else {
            LambdaQueryWrapper<PartyReview> wrapper = new LambdaQueryWrapper<>();
            DataScopeHelper.apply(wrapper, "org_id", null);
            wrapper.orderByDesc(PartyReview::getReviewYear).orderByDesc(PartyReview::getReviewId)
                    .last("LIMIT 1");
            review = reviewMapper.selectOne(wrapper);
        }

        List<PartyReviewDetail> details = review == null ? List.of() : listDetails(review.getReviewId());
        Map<Integer, Long> grouped = new LinkedHashMap<>();
        for (PartyReviewDetail d : details) {
            if (d.getGrade() != null) {
                grouped.merge(d.getGrade(), 1L, Long::sum);
            }
        }

        long total = details.size();
        List<Map<String, Object>> byGrade = new ArrayList<>();
        for (ReviewGradeEnum g : ReviewGradeEnum.values()) {
            long count = grouped.getOrDefault(g.getCode(), 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("grade", g.getCode());
            item.put("gradeLabel", g.getLabel());
            item.put("count", count);
            item.put("ratio", rate(count, total));
            byGrade.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewId", review == null ? null : review.getReviewId());
        result.put("title", review == null ? null : review.getTitle());
        result.put("total", total);
        result.put("graded", details.stream().filter(d -> d.getGrade() != null).count());
        result.put("excellentQuota", review == null ? null : effectiveQuota(review, details.size()));
        result.put("byGrade", byGrade);
        if (review != null) {
            result.put("progress", progressOf(review));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private Map<Long, String> loadOrgNames(List<Long> orgIds) {
        List<Long> ids = orgIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new LinkedHashMap<>();
        for (Map<String, Object> row : lookupMapper.selectOrgNames(ids)) {
            map.put(asLong(row.get("org_id")), asString(row.get("org_name")));
        }
        return map;
    }

    private Map<String, Object> toVO(PartyReview r, Map<Long, String> orgNames, Map<String, Object> progress) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reviewId", r.getReviewId());
        item.put("title", r.getTitle());
        item.put("orgId", r.getOrgId());
        item.put("orgName", orgNames.get(r.getOrgId()));
        item.put("reviewYear", r.getReviewYear());
        item.put("startDate", r.getStartDate());
        item.put("endDate", r.getEndDate());
        item.put("status", r.getStatus());
        item.put("statusLabel", ReviewStatusEnum.labelOf(r.getStatus()));
        item.put("excellentQuota", effectiveQuota(r,
                ((Number) progress.getOrDefault("total", 0L)).intValue()));
        item.put("description", r.getDescription());
        item.put("fileId", r.getFileId());
        item.put("fileUrl", r.getFileUrl());
        item.put("progress", progress);
        return item;
    }

    private static Long asLong(Object value) {
        return value instanceof Number num ? num.longValue() : null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
