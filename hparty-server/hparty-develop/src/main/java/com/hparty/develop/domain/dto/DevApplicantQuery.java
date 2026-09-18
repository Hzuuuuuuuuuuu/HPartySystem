package com.hparty.develop.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 发展党员列表查询条件（对应图5 卡片墙顶部的阶段筛选）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DevApplicantQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 姓名 / 手机号 模糊查询 */
    private String keyword;

    /** 所属党组织 */
    private Long orgId;

    /** 阶段编码：STAGE_1..STAGE_5，为空查全部 */
    private String currentStage;

    /** 当前步骤编码 */
    private String currentStep;

    /** 流程状态：1=进行中 2=已完成 3=已终止 4=已中止 */
    private Integer status;

    /** 人员状态（party_person.member_status） */
    private Integer memberStatus;
}
