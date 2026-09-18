package com.hparty.system.domain.dto;

import com.hparty.common.core.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 党组织查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "党组织查询条件")
public class SysDeptQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 组织名称，模糊匹配 */
    @Schema(description = "组织名称，模糊匹配")
    private String orgName;

    /** 组织类型：1=党委 2=党总支 3=党支部 4=党小组 */
    @Schema(description = "组织类型：1=党委 2=党总支 3=党支部 4=党小组")
    private Integer orgType;

    /** 状态：0=停用 1=正常 */
    @Schema(description = "状态：0=停用 1=正常")
    private Integer status;

    /** 父组织 ID，用于按层级下钻 */
    @Schema(description = "父组织 ID")
    private Long parentId;
}
