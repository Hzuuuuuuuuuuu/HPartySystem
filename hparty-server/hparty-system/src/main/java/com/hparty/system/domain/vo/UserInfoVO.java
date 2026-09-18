package com.hparty.system.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 登录用户信息，前端启动时拉取一次，用于渲染顶栏与按钮权限。
 */
@Data
public class UserInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String nickName;
    private String avatar;

    /** 关联的人员档案 ID */
    private Long personId;
    private String personName;

    /** 所属党组织 */
    private Long orgId;
    private String orgName;
    private Integer orgType;
    private String orgPath;

    /** 数据权限范围 */
    private Integer dataScope;

    /** 是否超级管理员 */
    private boolean superAdmin;

    private Set<String> roles;
    private Set<String> perms;
}
