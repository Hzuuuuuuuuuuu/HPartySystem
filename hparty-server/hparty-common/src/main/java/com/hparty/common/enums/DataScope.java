package com.hparty.common.enums;

import lombok.Getter;

/**
 * 数据权限范围，与 {@code sys_role.data_scope} 对应。
 */
@Getter
public enum DataScope {

    ALL(1, "全部数据"),
    CURRENT(2, "本级数据"),
    CURRENT_AND_CHILD(3, "本级及以下数据"),
    SELF(4, "仅本人数据"),
    CUSTOM(5, "自定义数据");

    private final int code;
    private final String label;

    DataScope(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DataScope of(Integer code) {
        if (code == null) {
            return SELF;
        }
        for (DataScope s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return SELF;
    }
}
