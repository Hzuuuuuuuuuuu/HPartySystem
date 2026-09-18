package com.hparty.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图对象。
 */
@Data
@Schema(description = "角色")
public class SysRoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色 ID */
    @Schema(description = "角色 ID")
    private Long roleId;

    /** 角色名称 */
    @Schema(description = "角色名称")
    private String roleName;

    /** 角色权限字符串 */
    @Schema(description = "角色权限字符串")
    private String roleKey;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer roleSort;

    /** 数据范围：1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义 */
    @Schema(description = "数据范围：1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义")
    private Integer dataScope;

    /** 是否内置：0=否 1=是（内置角色不可删除） */
    @Schema(description = "是否内置：0=否 1=是")
    private Integer isBuiltin;

    /** 状态：0=停用 1=正常 */
    @Schema(description = "状态：0=停用 1=正常")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 已分配菜单 ID 集合，仅详情接口返回 */
    @Schema(description = "已分配菜单 ID 集合，仅详情接口返回")
    private List<Long> menuIds;

    /** 自定义数据范围下的组织 ID 集合，仅详情接口返回 */
    @Schema(description = "自定义数据范围下的组织 ID 集合，仅详情接口返回")
    private List<Long> deptIds;
}
