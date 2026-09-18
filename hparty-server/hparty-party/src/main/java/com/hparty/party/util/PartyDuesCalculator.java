package com.hparty.party.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 党费缴纳标准计算。
 *
 * <p>按月工资收入分档：</p>
 * <ul>
 *   <li>3000 元以下 —— 0.5%</li>
 *   <li>3000（含）— 5000 元 —— 1%</li>
 *   <li>5000（含）— 10000 元 —— 1.5%</li>
 *   <li>10000（含）以上 —— 2%</li>
 * </ul>
 *
 * <p>分档口径只在此处定义一次：新增、修改、批量生成账单统一调用本方法，
 * 避免同一套 if-else 散落在多处后出现口径不一致。</p>
 */
public final class PartyDuesCalculator {

    /** 第一档上限（不含） */
    private static final BigDecimal TIER_1 = new BigDecimal("3000");
    /** 第二档上限（不含） */
    private static final BigDecimal TIER_2 = new BigDecimal("5000");
    /** 第三档上限（不含） */
    private static final BigDecimal TIER_3 = new BigDecimal("10000");

    private static final BigDecimal RATE_UNDER_3000 = new BigDecimal("0.005");
    private static final BigDecimal RATE_3000_TO_5000 = new BigDecimal("0.01");
    private static final BigDecimal RATE_5000_TO_10000 = new BigDecimal("0.015");
    private static final BigDecimal RATE_ABOVE_10000 = new BigDecimal("0.02");

    private PartyDuesCalculator() {
    }

    /**
     * 按缴纳基数（月工资收入）计算每月应缴党费，保留 2 位小数（四舍五入）。
     *
     * @param base 缴纳基数，可为 null 或非正数，此时应缴金额为 0.00
     * @return 应缴金额，永不为 null
     */
    public static BigDecimal calcStandard(BigDecimal base) {
        if (base == null || base.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal rate;
        if (base.compareTo(TIER_1) < 0) {
            rate = RATE_UNDER_3000;
        } else if (base.compareTo(TIER_2) < 0) {
            rate = RATE_3000_TO_5000;
        } else if (base.compareTo(TIER_3) < 0) {
            rate = RATE_5000_TO_10000;
        } else {
            rate = RATE_ABOVE_10000;
        }
        return base.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
