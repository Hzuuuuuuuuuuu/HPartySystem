package com.hparty.common.core;

import lombok.Getter;

/**
 * 统一状态码。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    FAIL(500, "操作失败"),

    /** 业务规则拦截，例如发展党员流程不满足前置条件 */
    BIZ_ERROR(600, "业务处理失败"),

    /** 流程规则校验未通过 */
    RULE_REJECT(601, "流程规则校验未通过");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
