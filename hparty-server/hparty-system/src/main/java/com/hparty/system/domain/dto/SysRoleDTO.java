package com.hparty.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 角色新增 / 修改入参。
 */
@Data
@Schema(description = "角色新增/修改入参")
public class SysRoleDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色 ID，新增时为空 */
    @Schema(description = "角色 ID，新增时为空")
    private Long roleId;

    /** 角色名称 */
    @Schema(description = "角色名称")
    private String roleName;

    /** 角色权限字符串，如 BRANCH_SECRETARY */
    @Schema(description = "角色权限字符串")
    private String roleKey;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer roleSort;

    /** 数据范围：1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义 */
    @Schema(description = "数据范围：1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义")
    private Integer dataScope;

    /** 状态：0=停用 1=正常 */
    @Schema(description = "状态：0=停用 1=正常")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 已分配菜单 ID 集合，全量覆盖 */
    @Schema(description = "已分配菜单 ID 集合，全量覆盖")
    private List<Long> menuIds;

    /** 自定义数据范围下的组织 ID 集合，仅 dataScope=5 时生效 */
    @Schema(description = "自定义数据范围下的组织 ID 集合，仅 dataScope=5 时生效")
    private List<Long> deptIds;
}
