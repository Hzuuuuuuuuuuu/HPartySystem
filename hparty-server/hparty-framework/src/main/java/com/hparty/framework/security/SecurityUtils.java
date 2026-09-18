package com.hparty.framework.security;

import cn.dev33.satoken.stp.StpUtil;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.ResultCode;
import com.hparty.common.exception.BizException;

/**
 * 当前登录用户工具类。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 获取当前登录用户，未登录抛异常 */
    public static LoginUser getLoginUser() {
        LoginUser user = getLoginUserOrNull();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    /** 获取当前登录用户，未登录返回 null（用于定时任务、系统内部调用） */
    public static LoginUser getLoginUserOrNull() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            Object obj = StpUtil.getSession().get(Constants.SESSION_USER_ID);
            return obj instanceof LoginUser user ? user : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 当前用户 ID */
    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    /** 当前用户所属组织 ID */
    public static Long getOrgId() {
        return getLoginUser().getOrgId();
    }

    /** 当前用户关联的人员档案 ID，可能为 null */
    public static Long getPersonId() {
        LoginUser user = getLoginUser();
        return user.getPersonId();
    }

    /** 当前用户名 */
    public static String getUsername() {
        LoginUser user = getLoginUserOrNull();
        return user == null ? "system" : user.getUsername();
    }

    public static boolean isSuperAdmin() {
        LoginUser user = getLoginUserOrNull();
        return user != null && user.isSuperAdmin();
    }

    /** 校验当前用户所属组织是否与目标组织一致，用于防止越权操作 */
    public static void checkOrgPermission(Long targetOrgId) {
        LoginUser user = getLoginUser();
        if (user.isSuperAdmin()) {
            return;
        }
        if (targetOrgId == null || !targetOrgId.equals(user.getOrgId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他党组织的数据");
        }
    }
}
