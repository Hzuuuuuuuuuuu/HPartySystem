package com.hparty.framework.datascope;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.hparty.common.enums.DataScope;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;

/**
 * 数据权限工具：把「当前用户能看到哪些组织的数据」翻译成 SQL 条件。
 * <p>组织表使用物化路径 {@code sys_dept.org_path}（形如 {@code /1/3/7/}），
 * 前缀匹配即可拿到整棵子树，无需递归查询。</p>
 *
 * <p>用法：在 Service 的列表/详情查询中调用
 * {@code DataScopeHelper.apply(wrapper)}。</p>
 *
 * <p><b>为什么参数类型是 {@link AbstractWrapper} 而不是 {@code QueryWrapper}</b>：
 * MyBatis-Plus 中 {@code QueryWrapper} 与 {@code LambdaQueryWrapper} 是兄弟类，
 * 都直接继承 {@code AbstractWrapper}，彼此没有继承关系。
 * 而本项目两种写法都在用，因此这里放宽到共同父类才能同时兼容。</p>
 *
 * <p>条件一律通过 {@code apply(sql, 占位参数)} 拼装，交由 MyBatis 预编译占位，
 * 不存在 SQL 注入风险。</p>
 */
public final class DataScopeHelper {

    /** 数据权限表默认的组织字段名 */
    public static final String DEFAULT_ORG_COLUMN = "org_id";

    /** 数据权限表默认的人员字段名 */
    public static final String DEFAULT_PERSON_COLUMN = "person_id";

    private DataScopeHelper() {
    }

    /**
     * 按默认字段名（org_id / person_id）追加数据权限条件。
     */
    public static void apply(AbstractWrapper<?, ?, ?> wrapper) {
        apply(wrapper, DEFAULT_ORG_COLUMN, DEFAULT_PERSON_COLUMN);
    }

    /**
     * 追加数据权限条件。
     *
     * @param wrapper      查询条件（QueryWrapper 或 LambdaQueryWrapper 均可）
     * @param orgColumn    组织字段名，可为 null（该表无组织字段时退化为按人员过滤）
     * @param personColumn 人员字段名，可为 null
     */
    public static void apply(AbstractWrapper<?, ?, ?> wrapper, String orgColumn, String personColumn) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null) {
            // 无登录上下文（定时任务等）不加限制，由调用方自行控制
            return;
        }
        if (user.isSuperAdmin()) {
            return;
        }

        DataScope scope = DataScope.of(user.getDataScope());
        switch (scope) {
            case ALL -> {
                // 不做限制
            }
            case CURRENT -> {
                if (orgColumn != null) {
                    wrapper.apply(orgColumn + " = {0}", user.getOrgId());
                }
            }
            case CURRENT_AND_CHILD -> {
                if (orgColumn != null) {
                    // 子树匹配：本级路径为 /1/3/，子孙路径必然以其为前缀
                    wrapper.apply(orgColumn + " IN (SELECT org_id FROM sys_dept WHERE del_flag = 0 AND org_path LIKE {0})",
                            user.getOrgPath() + "%");
                } else if (personColumn != null) {
                    wrapper.apply(personColumn + " IN (SELECT person_id FROM party_person WHERE del_flag = 0 AND org_id IN "
                            + "(SELECT org_id FROM sys_dept WHERE del_flag = 0 AND org_path LIKE {0}))",
                            user.getOrgPath() + "%");
                }
            }
            case SELF -> {
                if (personColumn != null && user.getPersonId() != null) {
                    wrapper.apply(personColumn + " = {0}", user.getPersonId());
                } else if (orgColumn != null) {
                    // 没有人员档案的账号（如纯管理账号）只能看到本组织
                    wrapper.apply(orgColumn + " = {0}", user.getOrgId());
                }
            }
            case CUSTOM -> {
                if (orgColumn != null) {
                    wrapper.apply(orgColumn + " IN (SELECT org_id FROM sys_role_dept WHERE role_id IN "
                            + "(SELECT role_id FROM sys_user_role WHERE user_id = {0}))", user.getUserId());
                }
            }
        }
    }

    /**
     * 判断当前用户是否有权访问指定组织的数据。
     * <p>用于详情、修改、删除等按主键操作的越权校验。</p>
     *
     * <p><b>「本级及以下」必须比对目标组织的路径</b>：物化路径是
     * {@code /1/3/7/} 这种形式，判断目标是否在子树内的方法是
     * 「目标路径以我的路径为前缀」。早期版本这里只检查了用户自己的
     * orgPath 非空就放行，等于任何组织都能被按 ID 访问（包括平级兄弟支部），
     * 是个越权漏洞。</p>
     */
    public static boolean canAccessOrg(Long targetOrgId) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null || user.isSuperAdmin()) {
            return true;
        }
        if (targetOrgId == null) {
            return false;
        }
        DataScope scope = DataScope.of(user.getDataScope());
        return switch (scope) {
            case ALL -> true;

            case CURRENT, SELF -> targetOrgId.equals(user.getOrgId());

            case CURRENT_AND_CHILD -> {
                if (targetOrgId.equals(user.getOrgId())) {
                    yield true;
                }
                String myPath = user.getOrgPath();
                if (myPath == null || myPath.isEmpty()) {
                    yield false;
                }
                String targetPath = OrgPathResolver.orgPathOf(targetOrgId);
                // 目标路径以我的路径为前缀 → 目标是我的子孙节点
                yield targetPath != null && targetPath.startsWith(myPath);
            }

            case CUSTOM -> OrgPathResolver.hasCustomOrgAccess(user.getUserId(), targetOrgId);
        };
    }

    /**
     * 判断当前用户是否有权访问指定“人员 + 组织”数据。
     * <p>SELF 范围必须匹配本人 personId；其他范围仍按组织权限判断。</p>
     */
    public static boolean canAccessData(Long targetOrgId, Long targetPersonId) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null || user.isSuperAdmin()) {
            return true;
        }
        DataScope scope = DataScope.of(user.getDataScope());
        if (scope == DataScope.SELF && user.getPersonId() != null) {
            return targetPersonId != null && targetPersonId.equals(user.getPersonId());
        }
        return canAccessOrg(targetOrgId);
    }
}
