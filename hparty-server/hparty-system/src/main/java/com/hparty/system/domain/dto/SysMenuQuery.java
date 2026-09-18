package com.hparty.system.domain.dto;

import com.hparty.common.core.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 菜单查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "菜单查询条件")
public class SysMenuQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 菜单名称，模糊匹配 */
    @Schema(description = "菜单名称，模糊匹配")
    private String menuName;

    /** 权限标识，模糊匹配 */
    @Schema(description = "权限标识，模糊匹配")
    private String perms;

    /** 父菜单 ID */
    @Schema(description = "父菜单 ID")
    private Long parentId;

    /** 菜单类型：M=目录 C=菜单 F=按钮 */
    @Schema(description = "菜单类型：M=目录 C=菜单 F=按钮")
    private String menuType;

    /** 菜单状态：0=停用 1=正常 */
    @Schema(description = "菜单状态：0=停用 1=正常")
    private Integer status;
}
