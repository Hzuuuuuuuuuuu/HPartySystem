package com.hparty.party.enums;

import com.hparty.party.util.ReviewScoreRule;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 民主评议党员等次。
 *
 * <p>等次由综合得分按 {@link ReviewScoreRule} 的阈值判定，
 * 组织评定时也可以人工指定（如综合得分够优秀但支部认为不宜评优）。</p>
 */
@Getter
public enum ReviewGradeEnum {

    /** 优秀：综合得分 ≥ 90 */
    EXCELLENT(1, "优秀"),
    /** 合格：75 ~ 89 */
    QUALIFIED(2, "合格"),
    /** 基本合格：60 ~ 74 */
    BASIC(3, "基本合格"),
    /** 不合格：< 60 */
    UNQUALIFIED(4, "不合格");

    private final Integer code;
    private final String label;

    ReviewGradeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReviewGradeEnum e : values()) {
            if (e.code.equals(code)) {
                return e.label;
            }
        }
        return String.valueOf(code);
    }

    /**
     * 按综合得分判定等次，阈值见 {@link ReviewScoreRule}。
     *
     * @param totalScore 综合得分，可为 null
     * @return 等次；得分为 null 时返回 null
     */
    public static ReviewGradeEnum of(BigDecimal totalScore) {
        if (totalScore == null) {
            return null;
        }
        if (totalScore.compareTo(ReviewScoreRule.EXCELLENT_MIN) >= 0) {
            return EXCELLENT;
        }
        if (totalScore.compareTo(ReviewScoreRule.QUALIFIED_MIN) >= 0) {
            return QUALIFIED;
        }
        if (totalScore.compareTo(ReviewScoreRule.BASIC_MIN) >= 0) {
            return BASIC;
        }
        return UNQUALIFIED;
    }
}
