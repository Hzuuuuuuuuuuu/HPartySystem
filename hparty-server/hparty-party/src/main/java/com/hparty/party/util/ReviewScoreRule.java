package com.hparty.party.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 民主评议党员的计分口径（**全部权重与阈值集中在这里，便于按单位实际调整**）。
 *
 * <p>综合得分 = 自评 × {@link #W_SELF} + 互评 × {@link #W_PEER}
 * + 群众评议 × {@link #W_MASS} + 组织评定 × {@link #W_ORG}。
 * 四项权重之和必须为 1。</p>
 *
 * <p><b>缺项怎么算</b>：四个分项里只要有一项还没产生（例如尚未开展群众评议），
 * 就不能把缺的项按 0 计 —— 那会把一个只完成自评的人直接算成不及格。
 * 因此这里采用「**按已有项重新归一化**」：把缺项剔除后，剩余项的权重按比例放大到 1。
 * 若四项全缺，综合得分与等次都留空。</p>
 */
public final class ReviewScoreRule {

    /** 自评权重（20%） */
    public static final BigDecimal W_SELF = new BigDecimal("0.20");

    /** 互评权重（40%） */
    public static final BigDecimal W_PEER = new BigDecimal("0.40");

    /** 群众评议权重（20%） */
    public static final BigDecimal W_MASS = new BigDecimal("0.20");

    /** 组织评定权重（20%） */
    public static final BigDecimal W_ORG = new BigDecimal("0.20");

    /** 优秀等次下限（≥90） */
    public static final BigDecimal EXCELLENT_MIN = new BigDecimal("90");

    /** 合格等次下限（≥75） */
    public static final BigDecimal QUALIFIED_MIN = new BigDecimal("75");

    /** 基本合格等次下限（≥60） */
    public static final BigDecimal BASIC_MIN = new BigDecimal("60");

    /** 优秀比例上限：不超过党员总数的 30%（按人数向下取整，不足 1 人时至少留 1 个名额） */
    public static final BigDecimal EXCELLENT_RATIO = new BigDecimal("0.30");

    /** 得分保留一位小数 */
    private static final int SCALE = 1;

    private ReviewScoreRule() {
    }

    /**
     * 计算综合得分（含缺项归一化）。
     *
     * @param selfScore 自评得分，可为 null
     * @param peerScore 互评平均分，可为 null
     * @param massScore 群众评议得分，可为 null
     * @param orgScore  组织评定得分，可为 null
     * @return 综合得分（保留 1 位小数）；四项全为 null 时返回 null
     */
    public static BigDecimal totalScore(BigDecimal selfScore, BigDecimal peerScore,
                                        BigDecimal massScore, BigDecimal orgScore) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;

        if (selfScore != null) {
            weighted = weighted.add(selfScore.multiply(W_SELF));
            weightSum = weightSum.add(W_SELF);
        }
        if (peerScore != null) {
            weighted = weighted.add(peerScore.multiply(W_PEER));
            weightSum = weightSum.add(W_PEER);
        }
        if (massScore != null) {
            weighted = weighted.add(massScore.multiply(W_MASS));
            weightSum = weightSum.add(W_MASS);
        }
        if (orgScore != null) {
            weighted = weighted.add(orgScore.multiply(W_ORG));
            weightSum = weightSum.add(W_ORG);
        }

        if (weightSum.signum() == 0) {
            return null;
        }
        // 权重和不足 1 时按比例归一化（等价于除以权重和）
        return weighted.divide(weightSum, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 按党员总数推算优秀名额上限。
     *
     * @param memberCount 参加评议的党员总数
     * @return 优秀名额上限；0 人时返回 0
     */
    public static int excellentQuotaOf(int memberCount) {
        if (memberCount <= 0) {
            return 0;
        }
        int quota = BigDecimal.valueOf(memberCount).multiply(EXCELLENT_RATIO)
                .setScale(0, RoundingMode.DOWN).intValue();
        // 不足 1 人时仍给 1 个名额，否则 1-3 人的小支部永远评不出优秀
        return Math.max(quota, 1);
    }
}
