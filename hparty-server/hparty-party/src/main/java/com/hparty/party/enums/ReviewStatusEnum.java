package com.hparty.party.enums;

import lombok.Getter;

/**
 * 民主评议党员批次状态。
 *
 * <p>状态是**单向推进**的：草稿 → 自评中 → 互评中 → 组织评定中 → 已公示 → 已完成。
 * 前四个阶段由 Service 依据「明细的完成情况」自动跃迁，不需要人工点「下一步」；
 * 「已公示 → 已完成」由支部在公示期满后手工置位（走批次修改接口）。</p>
 */
@Getter
public enum ReviewStatusEnum {

    /** 草稿：可自由增删改，尚未生成明细 */
    DRAFT(0, "草稿"),
    /** 自评中：明细已生成，党员本人提交自评 */
    SELF(1, "自评中"),
    /** 互评中：党员之间互相打分 */
    PEER(2, "互评中"),
    /** 组织评定中：支部委员会给分并定等次 */
    ORG(3, "组织评定中"),
    /** 已公示：全部明细已定等次，进入公示期 */
    PUBLICITY(4, "已公示"),
    /** 已完成：公示期满，结果生效 */
    FINISHED(5, "已完成");

    private final Integer code;
    private final String label;

    ReviewStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReviewStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e.label;
            }
        }
        return String.valueOf(code);
    }
}
