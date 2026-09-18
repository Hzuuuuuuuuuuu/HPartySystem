package com.hparty.system.domain.dto;

import com.hparty.common.core.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 角色查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色查询条件")
public class SysRoleQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称，模糊匹配 */
    @Schema(description = "角色名称，模糊匹配")
    private String roleName;

    /** 角色权限字符串，模糊匹配 */
    @Schema(description = "角色权限字符串，模糊匹配")
    private String roleKey;

    /** 状态：0=停用 1=正常 */
    @Schema(description = "状态：0=停用 1=正常")
    private Integer status;
}
