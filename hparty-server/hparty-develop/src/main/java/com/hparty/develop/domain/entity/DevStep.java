package com.hparty.develop.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发展步骤模板
 */
@Data
@TableName("dev_step")
public class DevStep implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 步骤ID */
    @TableId(value = "step_id", type = IdType.AUTO)
    private Long stepId;

    /** 步骤编码：STEP_01..STEP_25 */
    private String stepCode;

    /** 步骤名称 */
    private String stepName;

    /** 所属阶段编码 */
    private String stageCode;

    /** 全流程顺序：1..25 */
    private Integer stepOrder;

    /** 步骤类型：1=单次办理 2=周期性考察 */
    private Integer stepType;

    /** 办理角色，逗号分隔 */
    private String handleRoles;

    /** 办理组织类型：1=党委 2=党总支 3=党支部(支部委员会/支部大会) 4=党小组 */
    private Integer handleOrgType;

    /** 是否需要表决：0=否 1=是 */
    private Integer needVote;

    /** 办结期限（天），NULL=无限制 */
    private Integer deadlineDays;

    /** 距基准步骤须满天数（如培养教育满1年=365） */
    private Integer intervalDays;

    /** 间隔基准步骤编码 */
    private String intervalBaseStep;

    /** 周期性考察间隔天数（180=半年, 90=一季度） */
    private Integer periodicDays;

    /** 集中培训最少天数（3） */
    private Integer minTrainingDays;

    /** 集中培训最少学时（24） */
    private Integer minTrainingHours;

    /** 绑定的规则策略，逗号分隔 */
    private String ruleKey;

    /** 所需材料说明 */
    private String materialDesc;

    /** 步骤说明（来自流程图原文） */
    private String description;

    /** 是否分支节点：0=否 1=是（如STEP_23三出口） */
    private Integer isBranch;
}
