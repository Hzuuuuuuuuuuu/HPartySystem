package com.hparty.security;

import com.hparty.system.mapper.SysRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SysRelationMapper} 中「权限变更影响哪些用户」三条查询的契约测试。
 *
 * <p>关联表没有外键，直接插入使用远离真实数据的 ID 的夹具行，
 * 事务在用例结束后回滚，不污染开发库。</p>
 */
// 属性集合必须与同模块其它用例保持一致，否则会多建一个 ApplicationContext。
@SpringBootTest(properties = "hparty.captcha.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SysRelationMapperUserQueryTest {

    private static final long ROLE_A = 990_000_001L;
    private static final long ROLE_B = 990_000_002L;
    private static final long ROLE_UNUSED = 990_000_003L;
    private static final long MENU = 990_000_101L;
    private static final long MENU_UNUSED = 990_000_102L;
    private static final long USER_1 = 990_000_201L;
    private static final long USER_2 = 990_000_202L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SysRelationMapper relationMapper;

    @BeforeEach
    void fixtures() {
        // USER_1 同时挂 ROLE_A、ROLE_B，用来验证去重
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?), (?, ?), (?, ?)",
                USER_1, ROLE_A, USER_1, ROLE_B, USER_2, ROLE_B);
        // 两个角色都授权了同一菜单
        jdbc.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?), (?, ?)",
                ROLE_A, MENU, ROLE_B, MENU);
    }

    @Test
    void selectUserIdsByRoleId() {
        assertThat(relationMapper.selectUserIdsByRoleId(ROLE_A)).containsExactly(USER_1);
        assertThat(relationMapper.selectUserIdsByRoleId(ROLE_B)).containsExactlyInAnyOrder(USER_1, USER_2);
        assertThat(relationMapper.selectUserIdsByRoleId(ROLE_UNUSED)).isEmpty();
    }

    @Test
    void selectUserIdsByRoleIdsIsDistinct() {
        assertThat(relationMapper.selectUserIdsByRoleIds(List.of(ROLE_A))).containsExactly(USER_1);
        assertThat(relationMapper.selectUserIdsByRoleIds(List.of(ROLE_A, ROLE_B, ROLE_UNUSED)))
                .as("USER_1 挂两个角色，只应出现一次")
                .containsExactlyInAnyOrder(USER_1, USER_2);
        // 空集合不在此调用：会拼出 IN ()，由 Service 层短路
    }

    @Test
    void selectUserIdsByMenuIdIsDistinct() {
        assertThat(relationMapper.selectUserIdsByMenuId(MENU))
                .as("USER_1 经两个角色拥有该菜单，只应出现一次")
                .containsExactlyInAnyOrder(USER_1, USER_2);
        assertThat(relationMapper.selectUserIdsByMenuId(MENU_UNUSED)).isEmpty();
    }
}
