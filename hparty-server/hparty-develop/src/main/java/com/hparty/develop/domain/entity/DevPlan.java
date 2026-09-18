package com.hparty.develop.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 发展党员年度计划。
 *
 * <p>{@code dev_plan} 的 5 个审计列齐全，因此继承 {@code BaseEntity}。
 * {@code org_id + plan_year} 的业务唯一约束建在生成列 {@code org_year_alive} 上
 * （见 {@code docs/03-数据库设计.md} 2.2），逻辑删除后不影响重建同年计划。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dev_plan")
public class DevPlan extends BaseEntity {

    /** 计划ID */
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long planId;

    /** 计划所属组织 */
    private Long orgId;

    /** 计划年度 */
    private Integer planYear;

    /** 计划发展党员数 */
    private Integer planCount;

    /** 入党积极分子培养目标数 */
    private Integer activistTarget;

    /** 状态：0=草稿 1=已下达 2=执行中 3=已完成 */
    private Integer status;

    /** 下达组织（上级党委） */
    private Long issueOrgId;

    /** 下达日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;

    /** 计划说明 */
    private String description;
}
