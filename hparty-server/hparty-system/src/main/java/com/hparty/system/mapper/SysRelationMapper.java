package com.hparty.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

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
