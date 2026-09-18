package com.hparty.common.enums;

import lombok.Getter;

/**
 * 党组织类型。
 */
@Getter
public enum OrgType {

    PARTY_COMMITTEE(1, "党委"),
    GENERAL_BRANCH(2, "党总支"),
    BRANCH(3, "党支部"),
    PARTY_GROUP(4, "党小组");

    private final int code;
    private final String label;

    OrgType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(Integer code) {
        if (code == null) {
            return "";
        }
        for (OrgType t : values()) {
            if (t.code == code) {
                return t.label;
            }
        }
        return "";
    }
}
