package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党员服务类型。
 * <p>与 {@code member_service.service_type} 取值一一对应。</p>
 */
@Getter
public enum ServiceTypeEnum {

    DIFFICULTY_HELP(1, "困难帮扶"),
    VOLUNTEER(2, "志愿服务"),
    VISIT_CONSOLE(3, "走访慰问"),
    RIGHTS_PROTECTION(4, "权益维护"),
    EMPLOYMENT_HELP(5, "就业帮扶"),
    OTHER(6, "其它");

    private final Integer code;
    private final String label;

    ServiceTypeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ServiceTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ServiceTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        ServiceTypeEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
