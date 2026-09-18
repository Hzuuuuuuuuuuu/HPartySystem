package com.hparty.common.constant;

/**
 * 全局常量。
 */
public final class Constants {

    private Constants() {
    }

    /** 超级管理员角色标识 */
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    /** 超级管理员用户 ID */
    public static final Long SUPER_ADMIN_USER_ID = 1L;

    /** 顶级组织父 ID */
    public static final Long ROOT_ORG_ID = 0L;

    /** 未删除 */
    public static final int NOT_DELETED = 0;

    /** 已删除 */
    public static final int DELETED = 1;

    /** 正常状态 */
    public static final int STATUS_NORMAL = 1;

    /** 停用状态 */
    public static final int STATUS_DISABLED = 0;

    /** 是 */
    public static final int YES = 1;

    /** 否 */
    public static final int NO = 0;

    // ==================== 缓存 Key ====================

    /** 字典缓存前缀 */
    public static final String CACHE_DICT = "hparty:dict:";

    /** 验证码缓存前缀 */
    public static final String CACHE_CAPTCHA = "hparty:captcha:";

    /** 登录失败次数前缀 */
    public static final String CACHE_LOGIN_FAIL = "hparty:login:fail:";

    /** 验证码有效期（分钟） */
    public static final long CAPTCHA_EXPIRE_MINUTES = 2L;

    /** 登录失败锁定阈值 */
    public static final int LOGIN_FAIL_LIMIT = 5;

    // ==================== 请求头 ====================

    public static final String HEADER_TOKEN = "Authorization";

    public static final String TOKEN_PREFIX = "Bearer ";

    // ==================== Token 会话字段 ====================

    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_USERNAME = "username";
    public static final String SESSION_PERSON_ID = "personId";
    public static final String SESSION_ORG_ID = "orgId";
    public static final String SESSION_ORG_PATH = "orgPath";
    public static final String SESSION_ROLES = "roles";
    public static final String SESSION_PERMS = "perms";
    public static final String SESSION_DATA_SCOPE = "dataScope";
    public static final String SESSION_IS_SUPER = "isSuper";
}
