package com.hparty.framework.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据权限专用查询。
 *
 * <p>用于判断「目标组织是否在当前用户的子树内」—— 这需要读取
 * {@code sys_dept.org_path} 物化路径，而 {@link com.hparty.framework.datascope.DataScopeHelper}
 * 是静态工具类，无法直接注入 Mapper。</p>
 */
@Mapper
public interface DataScopeMapper {

    /** 取组织的物化路径，形如 {@code /1/3/7/} */
    @Select("SELECT org_path FROM sys_dept WHERE org_id = #{orgId} AND del_flag = 0")
    String selectOrgPath(@Param("orgId") Long orgId);

    /** 组织是否存在且启用 */
    @Select("SELECT COUNT(1) FROM sys_dept WHERE org_id = #{orgId} AND del_flag = 0 AND status = 1")
    int countEnabled(@Param("orgId") Long orgId);

    /**
     * 当前用户是否通过任一启用的 CUSTOM 角色获得目标组织访问权。
     *
     * <p>自定义范围不能只依赖列表 SQL；详情、修改、删除等按主键操作
     * 也必须落到同一份 sys_role_dept 授权集合上。</p>
     */
    @Select("""
            SELECT COUNT(1)
            FROM sys_role_dept rd
            JOIN sys_user_role ur ON ur.role_id = rd.role_id
            JOIN sys_role r ON r.role_id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND rd.org_id = #{orgId}
              AND r.data_scope = 5
              AND r.status = 1
              AND r.del_flag = 0
            """)
    int countCustomOrgAccess(@Param("userId") Long userId, @Param("orgId") Long orgId);
}
