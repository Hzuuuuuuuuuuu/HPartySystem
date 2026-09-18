package com.hparty.framework.security;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.hparty.common.constant.Constants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限数据源：从会话中读取角色与权限。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        LoginUser user = getSessionUser();
        if (user == null) {
            return Collections.emptyList();
        }
        // 超级管理员拥有所有权限（Sa-Token 通配符）
        if (user.isSuperAdmin()) {
            return List.of("*");
        }
        return new ArrayList<>(user.getPerms());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        LoginUser user = getSessionUser();
        if (user == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(user.getRoles());
    }

    private LoginUser getSessionUser() {
        try {
            Object obj = StpUtil.getSession().get(Constants.SESSION_USER_ID);
            return obj instanceof LoginUser user ? user : null;
        } catch (Exception e) {
            return null;
        }
    }
}
