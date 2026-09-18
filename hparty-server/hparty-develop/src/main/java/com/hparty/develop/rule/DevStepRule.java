package com.hparty.develop.rule;

/**
 * 发展党员步骤规则。
 *
 * <p>每个实现类通过 {@link #ruleKey()} 与 {@code dev_step.rule_key} 绑定，
 * 由 {@link DevRuleEngine} 在办理步骤时按序执行。</p>
 *
 * <p>新增规则只需：实现本接口 → 注册为 Spring Bean → 在 dev_step 表里填上 rule_key，
 * 无需改动流程主代码。</p>
 */
public interface DevStepRule {

    /** 规则标识，与 dev_step.rule_key 对应 */
    String ruleKey();

    /**
     * 校验。
     *
     * @param ctx 办理上下文
     * @return 校验结果，REJECT 会阻断本次办理
     */
    RuleResult validate(DevContext ctx);

    /**
     * 执行顺序，数值小的先执行。
     * <p>资格类规则应排在材料类规则之前，避免用户看到一堆无关报错。</p>
     */
    default int order() {
        return 100;
    }

    /** 规则说明，用于前端展示"本步骤受哪些规则约束" */
    default String description() {
        return "";
    }
}
