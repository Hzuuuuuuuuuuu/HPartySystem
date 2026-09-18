package com.hparty.party.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 党务域的跨表查询。
 *
 * <p>{@code hparty-party} 模块不依赖 {@code hparty-system}（避免模块间循环依赖），
 * 因此查组织名、人员姓名这类跨域信息用注解 SQL 直接解决，
 * 不必为此引入整个 system 模块的实体与 Mapper。</p>
 */
@Mapper
public interface PartyLookupMapper {

    /** 组织 ID → 组织名称 */
    @Select("SELECT org_name FROM sys_dept WHERE org_id = #{orgId} AND del_flag = 0")
    String selectOrgName(@Param("orgId") Long orgId);

    /** 人员 ID → 姓名 */
    @Select("SELECT name FROM party_person WHERE person_id = #{personId} AND del_flag = 0")
    String selectPersonName(@Param("personId") Long personId);

    /** 批量：人员 ID → 姓名 */
    @Select("""
            <script>
            SELECT name FROM party_person
            WHERE del_flag = 0 AND person_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<String> selectPersonNames(@Param("ids") List<Long> ids);

    /** 批量：组织 ID → 组织名称 */
    @Select("""
            <script>
            SELECT org_id, org_name FROM sys_dept
            WHERE del_flag = 0 AND org_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<java.util.Map<String, Object>> selectOrgNames(@Param("ids") List<Long> ids);

    /** 数据权限「本级及以下」：按物化路径前缀取组织 ID 列表 */
    @Select("SELECT org_id FROM sys_dept WHERE del_flag = 0 AND org_path LIKE #{prefix}")
    List<Long> selectOrgIdsByPathPrefix(@Param("prefix") String prefix);

    /** 数据权限「自定义」：按用户角色取组织 ID 列表 */
    @Select("""
            SELECT org_id FROM sys_role_dept
            WHERE role_id IN (SELECT role_id FROM sys_user_role WHERE user_id = #{userId})
            """)
    List<Long> selectOrgIdsByUser(@Param("userId") Long userId);

    /**
     * 批量生成党费账单用：取正式党员（{@code member_status = 5}）及其缴纳基数。
     *
     * <p>{@code orgIds} 为 null 表示不限组织（全部数据权限）；{@code personIds} 不为空时
     * 再按人员收敛（仅本人数据权限）。两个条件都为 null 时不加限制。</p>
     *
     * @return 每行含 person_id / name / org_id / dues_base
     */
    @Select("""
            <script>
            SELECT p.person_id, p.name, p.org_id, mp.dues_base
            FROM party_person p
            LEFT JOIN party_member_profile mp ON mp.person_id = p.person_id
            WHERE p.del_flag = 0 AND p.member_status = 5
            <if test="orgIds != null and orgIds.size() > 0">
                AND p.org_id IN
                <foreach collection="orgIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            <if test="personIds != null and personIds.size() > 0">
                AND p.person_id IN
                <foreach collection="personIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            ORDER BY p.org_id, p.person_id
            </script>
            """)
    List<java.util.Map<String, Object>> selectFullMembersForDues(@Param("orgIds") List<Long> orgIds,
                                                                 @Param("personIds") List<Long> personIds);
}
