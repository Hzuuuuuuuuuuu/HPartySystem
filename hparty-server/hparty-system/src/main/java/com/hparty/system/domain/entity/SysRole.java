package com.hparty.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hparty.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色ID */
    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;

    /** 角色名称 */
    private String roleName;

    /** 角色权限字符串 */
    private String roleKey;

    /** 显示顺序 */
    private Integer roleSort;

    /** 数据范围：1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义 */
    private Integer dataScope;

    /** 是否内置：0=否 1=是（内置角色不可删除） */
    private Integer isBuiltin;

    /** 状态：0=停用 1=正常 */
    private Integer status;

    /** 备注 */
    private String remark;
}
