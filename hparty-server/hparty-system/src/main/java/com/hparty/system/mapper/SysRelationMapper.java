package com.hparty.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 关联表 Mapper。
 *
 * <p>{@code sys_user_role}、{@code sys_role_menu}、{@code sys_role_dept} 三张表只有
 * 联合主键、没有独立业务字段与逻辑删除列，不值得各建一个实体，统一在此维护。</p>
 *
 * <p>注意：批量写入方法在集合为空时会拼出非法 SQL，调用方必须先判空。</p>
 */
@Mapper
public interface SysRelationMapper {

    // ==================== 用户 - 角色 ====================

    /** 查询用户已分配的角色 ID */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 批量：用户 ID → 角色名称，供用户列表补全「角色」列。
     *
     * <p>列表页一屏十几行，逐行调用 {@link #selectRoleIdsByUserId} 会变成 N+1 查询，
     * 因此一次性按 user_id 批量取。返回值沿用跨表批量查询的
     * {@code List<Map<String, Object>>} 形式，键为 {@code user_id} / {@code role_name}。</p>
     *
     * <p>过滤条件只排除已删除的角色：停用（status=0）的角色仍然返回，
     * 因为「这个账号挂着哪些角色」属于现状，列表要如实展示。</p>
     */
    @Select("""
            <script>
            SELECT ur.user_id, r.role_name
            FROM sys_user_role ur
            JOIN sys_role r ON r.role_id = ur.role_id AND r.del_flag = 0
            WHERE ur.user_id IN
            <foreach collection="userIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY ur.user_id, r.role_sort, r.role_id
            </script>
            """)
    List<Map<String, Object>> selectRoleNamesByUserIds(@Param("userIds") Collection<Long> userIds);

    /** 清空用户的角色关联 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRolesByUserId(@Param("userId") Long userId);

    /** 批量写入用户角色关联 */
    @Insert("<script>INSERT INTO sys_user_role (user_id, role_id) VALUES "
            + "<foreach collection='roleIds' item='roleId' separator=','>(#{userId}, #{roleId})</foreach>"
            + "</script>")
    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") Collection<Long> roleIds);

    /** 统计引用了该角色的用户数 */
    @Select("SELECT COUNT(1) FROM sys_user_role WHERE role_id = #{roleId}")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    /** 按角色清空用户关联 */
    @Delete("DELETE FROM sys_user_role WHERE role_id = #{roleId}")
    int deleteUserRolesByRoleId(@Param("roleId") Long roleId);

    // ==================== 角色 - 菜单 ====================

    /** 查询角色已分配的菜单 ID */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /** 清空角色的菜单关联 */
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenusByRoleId(@Param("roleId") Long roleId);

    /** 批量写入角色菜单关联 */
    @Insert("<script>INSERT INTO sys_role_menu (role_id, menu_id) VALUES "
            + "<foreach collection='menuIds' item='menuId' separator=','>(#{roleId}, #{menuId})</foreach>"
            + "</script>")
    int insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") Collection<Long> menuIds);

    /** 统计引用了该菜单的角色数 */
    @Select("SELECT COUNT(1) FROM sys_role_menu WHERE menu_id = #{menuId}")
    long countRolesByMenuId(@Param("menuId") Long menuId);

    /** 按菜单清空角色关联 */
    @Delete("DELETE FROM sys_role_menu WHERE menu_id = #{menuId}")
    int deleteRoleMenusByMenuId(@Param("menuId") Long menuId);

    // ==================== 角色 - 组织（数据范围=自定义） ====================

    /** 查询角色自定义数据范围内的组织 ID */
    @Select("SELECT org_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectOrgIdsByRoleId(@Param("roleId") Long roleId);

    /** 清空角色的组织关联 */
    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    int deleteRoleDeptsByRoleId(@Param("roleId") Long roleId);

    /** 批量写入角色组织关联 */
    @Insert("<script>INSERT INTO sys_role_dept (role_id, org_id) VALUES "
            + "<foreach collection='orgIds' item='orgId' separator=','>(#{roleId}, #{orgId})</foreach>"
            + "</script>")
    int insertRoleDepts(@Param("roleId") Long roleId, @Param("orgIds") Collection<Long> orgIds);
}
