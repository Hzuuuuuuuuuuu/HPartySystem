package com.hparty.develop.rule;

/**
 * 规则校验结果。
 *
 * <p>三档设计的原因：党务流程里有些约束是**硬性**的（培养教育不满 1 年不能确定为发展对象），
 * 有些只是**提醒**（上级党委审批超过 3 个月）——超期了事情照样得办，
 * 但系统必须把超期情况暴露出来。若把提醒做成硬拦截，反而会逼着经办人绕过系统。</p>
 *
 * @param level   校验级别
 * @param message 提示信息，PASS 时为 null
 */
public record RuleResult(Level level, String message) {

    public enum Level {
        /** 通过，无任何问题 */
        PASS,
        /** 通过，但有需要提醒的问题（如已超期） */
        WARN,
        /** 不通过，阻断本次办理 */
        REJECT
    }

    public static RuleResult pass() {
        return new RuleResult(Level.PASS, null);
    }

    public static RuleResult warn(String message) {
        return new RuleResult(Level.WARN, message);
    }

    public static RuleResult reject(String message) {
        return new RuleResult(Level.REJECT, message);
    }

    /** 是否允许继续办理（PASS 与 WARN 都允许） */
    public boolean isAllowed() {
        return level != Level.REJECT;
    }

    public boolean isRejected() {
        return level == Level.REJECT;
    }

    public boolean isWarn() {
        return level == Level.WARN;
    }
}
