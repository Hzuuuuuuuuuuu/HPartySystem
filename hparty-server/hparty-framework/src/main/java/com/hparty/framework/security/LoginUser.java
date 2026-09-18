package com.hparty.framework.security;

import com.hparty.common.constant.Constants;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 登录用户会话模型，存放于 Sa-Token Session。
 */
@Data
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String nickName;
    private String avatar;

    /** 关联的人员档案 ID，可能为空（如纯管理账号） */
    private Long personId;
    private String personName;

    /** 所属党组织 */
    private Long orgId;
    private String orgName;
    /** 组织类型：1=党委 2=党总支 3=党支部 4=党小组 */
    private Integer orgType;
    /** 组织物化路径，形如 /1/3/7/ */
    private String orgPath;

    /** 数据权限范围，取值为多个角色中最宽的那个 */
    private Integer dataScope;

    /** 是否超级管理员 */
    private boolean superAdmin;

    /** 角色标识集合 */
    private Set<String> roles = new HashSet<>();

    /** 权限标识集合 */
    private Set<String> perms = new HashSet<>();

    /** 是否拥有指定角色 */
    public boolean hasRole(String roleKey) {
        return roles != null && roles.contains(roleKey);
    }

    /** 是否拥有指定权限 */
    public boolean hasPerm(String perm) {
        return perms != null && perms.contains(perm);
    }

    /** 是否为支部书记/副书记/组织委员等支部班子成员 */
    public boolean isBranchLeader() {
        return hasRole("BRANCH_SECRETARY") || hasRole("BRANCH_DEPUTY") || hasRole("ORG_COMMITTEE");
    }

    /** 是否属于党委级组织（用于判定能否办理「上级党委审批/备案」类步骤） */
    public boolean isCommitteeLevel() {
        return orgType != null && orgType == 1;
    }

    public boolean isSuperAdmin() {
        return superAdmin || Constants.SUPER_ADMIN_USER_ID.equals(userId);
    }
}
