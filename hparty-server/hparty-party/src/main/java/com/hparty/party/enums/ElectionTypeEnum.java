package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党组织换届类型。
 * <p>与 {@code org_election.election_type} 取值一一对应。</p>
 */
@Getter
public enum ElectionTypeEnum {

    REGULAR(1, "换届选举"),
    BY_ELECTION(2, "补选"),
    ADJUST(3, "委员调整");

    private final Integer code;
    private final String label;

    ElectionTypeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ElectionTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ElectionTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        ElectionTypeEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
