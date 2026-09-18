package com.hparty.system.mapper;

import com.hparty.system.domain.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 登录鉴权专用查询。
 *
 * <p>这几条查询都要跨 3 张表连接（用户-角色-菜单），用注解 SQL 比
 * QueryWrapper 更直观，也避免为它们各自建 VO。</p>
 */
@Mapper
public interface AuthMapper {

    /**
     * 查询用户的角色标识集合。
     * <p>只取启用状态的角色。</p>
     */
    @Select("""
            SELECT r.role_key
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.role_id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.del_flag = 0
            """)
    List<String> selectRoleKeys(@Param("userId") Long userId);

    /**
     * 查询用户的权限标识集合。
     * <p>只取启用状态的菜单且 perms 非空的项。</p>
     */
    @Select("""
            SELECT DISTINCT m.perms
            FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id
            JOIN sys_role r ON r.role_id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND m.status = 1
              AND m.perms IS NOT NULL
              AND m.perms <> ''
              AND r.status = 1
              AND r.del_flag = 0
            """)
    List<String> selectPerms(@Param("userId") Long userId);

    /**
     * 查询用户的数据权限范围。
     * <p>用户可能有多个角色，取其中<b>最宽</b>的范围：
     * data_scope 取值 1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义，
     * 数值越小范围越大，因此取 MIN。</p>
     */
    @Select("""
            SELECT MIN(r.data_scope)
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.role_id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.del_flag = 0
            """)
    Integer selectDataScope(@Param("userId") Long userId);

    /**
     * 查询用户可见的菜单（目录与菜单，不含按钮），用于构建前端动态路由。
     */
    @Select("""
            SELECT DISTINCT m.menu_id, m.parent_id, m.menu_name, m.order_num, m.path, m.component,
                   m.query, m.is_frame, m.is_cache, m.menu_type, m.visible, m.status, m.perms, m.icon
            FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id
            JOIN sys_role r ON r.role_id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND m.menu_type IN ('M', 'C')
              AND m.status = 1
              AND r.status = 1
              AND r.del_flag = 0
            ORDER BY m.parent_id, m.order_num
            """)
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 查询用户是否拥有某个权限标识（用于按钮级校验的后备判断）。
     */
    @Select("""
            SELECT COUNT(1)
            FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId} AND m.perms = #{perm}
            """)
    int countPerm(@Param("userId") Long userId, @Param("perm") String perm);
}
