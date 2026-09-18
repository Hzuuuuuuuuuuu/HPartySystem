package com.hparty.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点，用于角色分配权限时勾选。
 */
@Data
@Schema(description = "菜单树节点")
public class SysMenuTreeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 菜单 ID */
    @Schema(description = "菜单 ID")
    private Long menuId;

    /** 父菜单 ID，0=顶级 */
    @Schema(description = "父菜单 ID，0=顶级")
    private Long parentId;

    /** 菜单名称 */
    @Schema(description = "菜单名称")
    private String menuName;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer orderNum;

    /** 路由地址 */
    @Schema(description = "路由地址")
    private String path;

    /** 组件路径 */
    @Schema(description = "组件路径")
    private String component;

    /** 路由参数 */
    @Schema(description = "路由参数")
    private String query;

    /** 是否外链：0=否 1=是 */
    @Schema(description = "是否外链：0=否 1=是")
    private Integer isFrame;

    /** 是否缓存：0=否 1=是 */
    @Schema(description = "是否缓存：0=否 1=是")
    private Integer isCache;

    /** 菜单类型：M=目录 C=菜单 F=按钮 */
    @Schema(description = "菜单类型：M=目录 C=菜单 F=按钮")
    private String menuType;

    /** 显示状态：0=隐藏 1=显示 */
    @Schema(description = "显示状态：0=隐藏 1=显示")
    private Integer visible;

    /** 菜单状态：0=停用 1=正常 */
    @Schema(description = "菜单状态：0=停用 1=正常")
    private Integer status;

    /** 权限标识 */
    @Schema(description = "权限标识")
    private String perms;

    /** 菜单图标 */
    @Schema(description = "菜单图标")
    private String icon;

    /** 子菜单 */
    @Schema(description = "子菜单")
    private List<SysMenuTreeVO> children = new ArrayList<>();
}
