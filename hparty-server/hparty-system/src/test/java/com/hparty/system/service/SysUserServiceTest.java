package com.hparty.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.dto.SysUserQuery;
import com.hparty.system.domain.entity.SysDept;
import com.hparty.system.domain.entity.SysUser;
import com.hparty.system.domain.vo.SysUserVO;
import com.hparty.system.mapper.SysDeptMapper;
import com.hparty.system.mapper.SysRelationMapper;
import com.hparty.system.mapper.SysRoleMapper;
import com.hparty.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 用户列表组装逻辑的回归测试。
 *
 * <p>锁定两个曾经出过问题的行为：</p>
 * <ol>
 *   <li>列表必须带出角色名称 —— 前端「角色」列读的就是 {@code roleNames}，
 *       早期只在详情接口填充，导致列表列恒为空；</li>
 *   <li>{@code org_id} 为 NULL 的账号（超级管理员）不能触发空指针 ——
 *       全部记录都无组织时 {@code loadOrgNames} 返回 {@code Map.of()}，
 *       而不可变空 Map 的 {@code get(null)} 会抛 NPE。</li>
 * </ol>
 *
 * <p>纯 Mockito 单测，不起 Spring、不连数据库；数据权限条件由
 * {@link com.hparty.framework.datascope.DataScopeHelper} 在无登录上下文时跳过，
 * 与本测试关注的组装逻辑无关。</p>
 */
class SysUserServiceTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysRelationMapper relationMapper = mock(SysRelationMapper.class);

    private final SysUserService service =
            new SysUserService(userMapper, roleMapper, deptMapper, relationMapper);

    /** 列表行必须带出角色名称，「角色」列才有内容 */
    @Test
    void listUserFillsRoleNamesForEachRow() {
        SysUser admin = user(1L, "admin", null);
        SysUser zhangsan = user(9L, "zhangsan", 2L);
        stubPage(admin, zhangsan);
        when(deptMapper.selectList(any())).thenReturn(List.of(dept(2L, "第一党支部")));
        when(relationMapper.selectRoleNamesByUserIds(anyCollection())).thenReturn(List.of(
                roleRow(1L, "超级管理员"),
                roleRow(9L, "支部书记"),
                roleRow(9L, "组织委员")));

        try (MockedStatic<SecurityUtils> ignored = mockStatic(SecurityUtils.class)) {
            PageResult<SysUserVO> result = service.listUser(new SysUserQuery());

            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getRecords().get(0).getRoleNames()).containsExactly("超级管理员");
            assertThat(result.getRecords().get(1).getRoleNames()).containsExactly("支部书记", "组织委员");
            assertThat(result.getRecords().get(1).getOrgName()).isEqualTo("第一党支部");
        }
    }

    /** 命中「单独搜 admin」场景：整页都是无组织账号，不能抛 NPE */
    @Test
    void listUserWithOnlyOrglessUsersDoesNotThrow() {
        SysUser admin = user(1L, "admin", null);
        stubPage(admin);
        when(relationMapper.selectRoleNamesByUserIds(anyCollection()))
                .thenReturn(List.of(roleRow(1L, "超级管理员")));

        try (MockedStatic<SecurityUtils> ignored = mockStatic(SecurityUtils.class)) {
            PageResult<SysUserVO> result = service.listUser(new SysUserQuery());

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getOrgName()).isNull();
            assertThat(result.getRecords().get(0).getRoleNames()).containsExactly("超级管理员");
        }
    }

    /** 没有分配角色的用户返回空集合，前端按「—」展示，不该是 null */
    @Test
    void listUserReturnsEmptyRoleNamesWhenUserHasNoRole() {
        SysUser nobody = user(9L, "nobody", 2L);
        stubPage(nobody);
        when(deptMapper.selectList(any())).thenReturn(List.of(dept(2L, "第一党支部")));
        when(relationMapper.selectRoleNamesByUserIds(anyCollection())).thenReturn(List.of());

        try (MockedStatic<SecurityUtils> ignored = mockStatic(SecurityUtils.class)) {
            PageResult<SysUserVO> result = service.listUser(new SysUserQuery());

            assertThat(result.getRecords().get(0).getRoleNames()).isEmpty();
        }
    }

    // ==================== 构造测试数据 ====================

    private void stubPage(SysUser... users) {
        Page<SysUser> page = new Page<>(1, 10, users.length);
        page.setRecords(List.of(users));
        when(userMapper.selectPage(any(), any())).thenReturn(page);
    }

    private SysUser user(Long userId, String username, Long orgId) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setNickName(username);
        user.setOrgId(orgId);
        return user;
    }

    private SysDept dept(Long orgId, String orgName) {
        SysDept dept = new SysDept();
        dept.setOrgId(orgId);
        dept.setOrgName(orgName);
        return dept;
    }

    /** 模拟批量角色查询返回的行：列为 user_id / role_name */
    private Map<String, Object> roleRow(Long userId, String roleName) {
        return Map.of("user_id", userId, "role_name", roleName);
    }
}
