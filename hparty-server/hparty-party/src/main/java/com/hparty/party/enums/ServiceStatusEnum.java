package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党员服务办理状态。
 * <p>与 {@code member_service.status} 取值一一对应。</p>
 */
@Getter
public enum ServiceStatusEnum {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    FINISHED(2, "已完成"),
    CANCELED(3, "已取消");

    private final Integer code;
    private final String label;

    ServiceStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ServiceStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ServiceStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        ServiceStatusEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
