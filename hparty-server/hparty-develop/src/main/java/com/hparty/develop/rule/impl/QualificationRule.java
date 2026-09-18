package com.hparty.develop.rule.impl;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.rule.DevContext;
import com.hparty.develop.rule.DevStepRule;
import com.hparty.develop.rule.RuleResult;
import com.hparty.system.domain.entity.PartyPerson;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * 资格条件规则。
 *
 * <p>按步骤编码分派到具体的资格校验。各步骤的资格要求差异较大，
 * 但都属于「办理人/当事人是否具备办理该步骤的资格」这一类，
 * 因此共用同一个 rule_key，在内部按步骤分派。</p>
 *
 * <p><b>注意</b>：培养联系人、入党介绍人的「正式党员身份」校验
 * 在 Service 层解析人员时完成（那里已经批量查库），本规则只校验
 * 数量与自荐这类无需查库的条件。</p>
 */
@Component
public class QualificationRule implements DevStepRule {

    /** 入党最低年龄 */
    private static final int MIN_AGE = 18;

    @Override
    public String ruleKey() {
        return "QUALIFICATION_RULE";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public String description() {
        return "校验当事人与办理人的资格条件";
    }

    @Override
    public RuleResult validate(DevContext ctx) {
        String stepCode = ctx.getStep().getStepCode();
        return switch (stepCode) {
            case "STEP_01" -> checkApplicantQualification(ctx);
            case "STEP_05" -> checkTrainers(ctx);
            case "STEP_09" -> checkIntroducers(ctx);
            case "STEP_13" -> checkPreReview(ctx);
            default -> RuleResult.pass();
        };
    }

    /**
     * STEP_01 递交入党申请书。
     * <p>条件：年满 18 周岁的中国公民；承认党的纲领和章程；
     * 愿意参加党的一个组织并在其中积极工作；愿意执行党的决议；按期交纳党费。</p>
     * <p>其中年龄可程序化校验，其余为政治条件，由党组织在后续步骤中考察。</p>
     */
    private RuleResult checkApplicantQualification(DevContext ctx) {
        PartyPerson person = ctx.getPerson();
        if (person == null) {
            return RuleResult.reject("未找到申请人的人员档案，无法办理。");
        }

        LocalDate birth = person.getBirthDate();
        LocalDate baseDate = ctx.getApplicant().getApplyDate() == null
                ? ctx.today() : ctx.getApplicant().getApplyDate();

        if (birth != null) {
            int age = Period.between(birth, baseDate).getYears();
            if (age < MIN_AGE) {
                return RuleResult.reject(String.format(
                        "申请人 %s 递交申请时年满 %d 周岁，未达到入党最低年龄要求（%d 周岁）。",
                        person.getName(), age, MIN_AGE));
            }
        }

        return RuleResult.pass();
    }

    /** STEP_05 指定培养联系人：1-2 名正式党员，不能是本人 */
    private RuleResult checkTrainers(DevContext ctx) {
        List<Long> trainerIds = ctx.getForm() == null ? null : ctx.getForm().getTrainerIds();
        if (trainerIds == null || trainerIds.isEmpty()) {
            return RuleResult.reject("请指定培养联系人，数量为 1-2 名正式党员。");
        }
        if (trainerIds.size() > 2) {
            return RuleResult.reject(String.format(
                    "培养联系人最多 2 名，当前指定了 %d 名。", trainerIds.size()));
        }
        return checkNotSelf(ctx, trainerIds, "培养联系人");
    }

    /** STEP_09 确定入党介绍人：2 名正式党员，不能是本人 */
    private RuleResult checkIntroducers(DevContext ctx) {
        List<Long> introducerIds = ctx.getForm() == null ? null : ctx.getForm().getIntroducerIds();
        if (introducerIds == null || introducerIds.isEmpty()) {
            return RuleResult.reject("请确定入党介绍人，数量为 2 名正式党员。");
        }
        if (introducerIds.size() != 2) {
            return RuleResult.reject(String.format(
                    "入党介绍人必须为 2 名正式党员，当前指定了 %d 名。", introducerIds.size()));
        }
        if (introducerIds.get(0).equals(introducerIds.get(1))) {
            return RuleResult.reject("两名入党介绍人不能为同一人。");
        }
        return checkNotSelf(ctx, introducerIds, "入党介绍人");
    }

    /**
     * STEP_13 上级党委预审。
     * <p>流程图注意项：发展对象未来 3 个月内将离开工作、学习单位的，
     * 一般不办理接收预备党员手续。</p>
     */
    private RuleResult checkPreReview(DevContext ctx) {
        if (ctx.getApplicant().getCandidateDate() == null) {
            return RuleResult.reject("尚未确定为发展对象，不能进行预审。");
        }
        return RuleResult.pass();
    }

    /** 办理人不能指定自己为培养联系人/入党介绍人 */
    private RuleResult checkNotSelf(DevContext ctx, List<Long> personIds, String roleName) {
        Long selfPersonId = ctx.getOperator() == null ? null : ctx.getOperator().getPersonId();
        if (selfPersonId != null && personIds.contains(selfPersonId)) {
            return RuleResult.reject(String.format("不能将本人指定为%s。", roleName));
        }
        Long applicantPersonId = ctx.getApplicant().getPersonId();
        if (applicantPersonId != null && personIds.contains(applicantPersonId)) {
            return RuleResult.reject(String.format("申请人本人不能担任自己的%s。", roleName));
        }
        return RuleResult.pass();
    }
}
