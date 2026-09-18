package com.hparty.common.enums;

import lombok.Getter;

/**
 * 步骤办理结论，与 {@code dev_step_record.result} 对应。
 */
@Getter
public enum HandleResult {

    /** 通过，流转到下一步 */
    PASS(1, "通过"),

    /** 驳回，退回上一步重新办理 */
    REJECT(2, "驳回"),

    /** 不通过，流程终止 */
    FAIL(3, "不通过"),

    /** 延长预备期（仅 STEP_23） */
    EXTEND(4, "延长预备期"),

    /** 取消预备党员资格（仅 STEP_23） */
    DISQUALIFY(5, "取消预备党员资格");

    private final int code;
    private final String label;

    HandleResult(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(Integer code) {
        if (code == null) {
            return "待办";
        }
        for (HandleResult r : values()) {
            if (r.code == code) {
                return r.label;
            }
        }
        return "";
    }
}
