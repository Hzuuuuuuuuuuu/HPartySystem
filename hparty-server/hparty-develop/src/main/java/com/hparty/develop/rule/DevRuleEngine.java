package com.hparty.develop.rule;

import com.hparty.common.exception.BizException;
import com.hparty.develop.domain.entity.DevStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 规则引擎：按 {@code dev_step.rule_key} 找到对应策略并执行。
 *
 * <p>所有 {@link DevStepRule} 实现由 Spring 自动注入并按 {@code ruleKey} 建索引。
 * 步骤与规则的绑定关系存在数据库里，因此调整某个步骤受哪些规则约束
 * 只需要改数据，不用改代码。</p>
 */
@Slf4j
@Component
public class DevRuleEngine {

    /** ruleKey → 规则实现 */
    private final Map<String, DevStepRule> ruleMap = new LinkedHashMap<>();

    public DevRuleEngine(List<DevStepRule> rules) {
        rules.stream()
                .sorted(Comparator.comparingInt(DevStepRule::order))
                .forEach(r -> ruleMap.put(r.ruleKey(), r));
        log.info("发展党员规则引擎已装载 {} 条规则: {}", ruleMap.size(), ruleMap.keySet());
    }

    /**
     * 执行当前步骤绑定的全部规则。
     *
     * @return 各规则的校验结果，按 {@link DevStepRule#order()} 升序
     */
    public List<RuleResult> validate(DevContext ctx) {
        DevStep step = ctx.getStep();
        String ruleKeys = step == null ? null : step.getRuleKey();
        if (ruleKeys == null || ruleKeys.isBlank()) {
            return List.of();
        }

        List<RuleResult> results = new ArrayList<>();
        for (String key : ruleKeys.split(",")) {
            String ruleKey = key.trim();
            if (ruleKey.isEmpty()) {
                continue;
            }
            DevStepRule rule = ruleMap.get(ruleKey);
            if (rule == null) {
                // 配置了不存在的规则不阻断流程，但要留下痕迹便于排查
                log.warn("步骤 {} 配置了未实现的规则 {}，已跳过", step.getStepCode(), ruleKey);
                continue;
            }
            try {
                RuleResult result = rule.validate(ctx);
                if (result != null) {
                    results.add(result);
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.error("规则 {} 执行异常，步骤 {}", ruleKey, step.getStepCode(), e);
                results.add(RuleResult.reject("规则校验异常：" + e.getMessage()));
            }
        }
        return results;
    }

    /**
     * 执行规则并在不通过时抛出异常。
     *
     * @throws BizException 任一规则返回 REJECT
     */
    public List<String> checkOrThrow(DevContext ctx) {
        List<RuleResult> results = validate(ctx);

        List<String> rejects = results.stream()
                .filter(RuleResult::isRejected)
                .map(RuleResult::message)
                .filter(Objects::nonNull)
                .toList();

        if (!rejects.isEmpty()) {
            throw BizException.ruleReject(String.join(" ", rejects));
        }

        return warnings(results);
    }

    /** 提取提醒信息（不阻断办理，但要提示经办人） */
    public List<String> warnings(List<RuleResult> results) {
        return results.stream()
                .filter(RuleResult::isWarn)
                .map(RuleResult::message)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 列出某步骤受哪些规则约束，供前端「本步骤规则说明」展示 */
    public List<Map<String, String>> describeRules(DevStep step) {
        String ruleKeys = step == null ? null : step.getRuleKey();
        if (ruleKeys == null || ruleKeys.isBlank()) {
            return List.of();
        }
        List<Map<String, String>> list = new ArrayList<>();
        for (String key : ruleKeys.split(",")) {
            DevStepRule rule = ruleMap.get(key.trim());
            if (rule != null) {
                list.add(Map.of("ruleKey", rule.ruleKey(), "description", rule.description()));
            }
        }
        return list;
    }
}
