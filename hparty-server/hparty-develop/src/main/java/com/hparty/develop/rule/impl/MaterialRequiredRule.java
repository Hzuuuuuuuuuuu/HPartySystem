package com.hparty.develop.rule.impl;

import com.hparty.develop.domain.entity.DevMaterial;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.rule.DevContext;
import com.hparty.develop.rule.DevStepRule;
import com.hparty.develop.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 材料齐全性规则。
 *
 * <p>检查当前步骤所需的档案材料是否已归档。返回 {@code WARN} 而非 {@code REJECT}
 * 是有意为之：材料补录在党务实践中很常见（纸质材料后补扫描件），
 * 硬性拦截会让历史材料的补录工作无法进行。系统只做提醒，
 * 由经办人自行判断是否先办理、后补材料。</p>
 */
@Component
public class MaterialRequiredRule implements DevStepRule {

    @Override
    public String ruleKey() {
        return "MATERIAL_REQUIRED_RULE";
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public String description() {
        return "提示当前步骤所需归档的材料";
    }

    @Override
    public RuleResult validate(DevContext ctx) {
        DevStep step = ctx.getStep();
        String materialDesc = step.getMaterialDesc();
        if (materialDesc == null || materialDesc.isBlank()) {
            return RuleResult.pass();
        }

        // 本次提交同时上传了材料
        List<Long> submitIds = ctx.getForm() == null ? null : ctx.getForm().getMaterialIds();
        if (submitIds != null && !submitIds.isEmpty()) {
            return RuleResult.pass();
        }

        // 该步骤下已有归档材料
        boolean hasMaterial = ctx.getMaterials().stream()
                .anyMatch(m -> step.getStepCode().equals(m.getStepCode()));
        if (hasMaterial) {
            return RuleResult.pass();
        }

        return RuleResult.warn(String.format(
                "本步骤需归档：%s。当前尚未上传，请办理后及时补充。", materialDesc));
    }
}
