package com.hparty.develop.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 发展党员年度计划查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DevPlanQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 计划年度 */
    private Integer planYear;

    /** 计划所属组织 */
    private Long orgId;

    /** 状态：0=草稿 1=已下达 2=执行中 3=已完成 */
    private Integer status;

    /** 关键字（计划说明模糊匹配） */
    private String keyword;
}
