package com.hparty.party.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 民主评议的跨域查询。
 *
 * <p>与 {@link PartyLookupMapper} 同理：{@code hparty-party} 不依赖 {@code hparty-system}，
 * 取党员名册、组织名这类跨域信息一律用注解 SQL 直接查表，不引入整个 system 模块。</p>
 */
@Mapper
public interface PartyReviewLookupMapper {

    /**
     * 取某组织**及其整棵子树**内的党员（含预备党员，{@code member_status IN (4,5)}）。
     *
     * <p>民主评议按支部开展，但党委级账号发起评议时应覆盖下辖各支部，
     * 因此这里用物化路径前缀取子树，而不是只取 {@code org_id = ?}。
     * 党员判定沿用 {@code member_status}（4=预备党员 5=正式党员）。</p>
     *
     * @param orgId 组织ID
     * @return 每行含 person_id / name / org_id
     */
    @Select("""
            SELECT p.person_id, p.name, p.org_id
            FROM party_person p
            WHERE p.del_flag = 0
              AND p.member_status IN (4, 5)
              AND p.org_id IN (
                  SELECT d.org_id FROM sys_dept d
                  WHERE d.del_flag = 0
                    AND d.org_path LIKE CONCAT(
                        (SELECT d2.org_path FROM sys_dept d2 WHERE d2.org_id = #{orgId} AND d2.del_flag = 0), '%')
              )
            ORDER BY p.org_id, p.person_id
            """)
    List<Map<String, Object>> selectMembersOfOrgTree(@Param("orgId") Long orgId);

    /** 组织 ID → 组织名称 */
    @Select("SELECT org_name FROM sys_dept WHERE org_id = #{orgId} AND del_flag = 0")
    String selectOrgName(@Param("orgId") Long orgId);

    /** 批量：组织 ID → 组织名称，避免列表页 N+1 */
    @Select("""
            <script>
            SELECT org_id, org_name FROM sys_dept
            WHERE del_flag = 0 AND org_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectOrgNames(@Param("ids") List<Long> ids);
}
