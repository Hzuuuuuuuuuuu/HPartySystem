package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党组织换届状态。
 * <p>与 {@code org_election.status} 取值一一对应。</p>
 */
@Getter
public enum ElectionStatusEnum {

    PREPARING(0, "筹备中"),
    RUNNING(1, "进行中"),
    FINISHED(2, "已完成"),
    TERMINATED(3, "已终止");

    private final Integer code;
    private final String label;

    ElectionStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ElectionStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ElectionStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        ElectionStatusEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
