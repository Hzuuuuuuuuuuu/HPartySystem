package com.hparty.security;

import cn.hutool.crypto.digest.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hparty.system.service.SysRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 权限变更后会话失效的端到端回归测试。
 *
 * <p>背景：登录时把角色/权限缓存进 Sa-Token 会话，之后改角色、改菜单授权、停用账号，
 * 旧会话仍按旧权限放行或拒绝（「没有操作权限：develop:step:list」时有时无）。
 * 现在这些变更在事务提交后注销受影响用户的全部会话。</p>
 *
 * <p><b>本类刻意不加 {@code @Transactional}</b>：会话注销挂在 afterCommit 上，
 * 测试事务回滚时它永远不会触发。夹具数据在 {@link #cleanup()} 里逐条删除；
 * 也不改 admin 的密码，操作人是专门建的测试账号。</p>
 */
// 属性集合必须与同模块其它用例保持一致，否则会多建一个 ApplicationContext。
@SpringBootTest(properties = "hparty.captcha.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PermissionSessionInvalidationTest {

    private static final String PROBE = "/develop/flow/steps";
    private static final String PROBE_PERM = "develop:step:list";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SysRoleService roleService;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> roleIds = new ArrayList<>();
    private final List<Long> menuIds = new ArrayList<>();
    private final List<String> usernames = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private final Map<String, String> passwords = new LinkedHashMap<>();

    private Long orgId;
    private Long targetRoleId;
    private Long target1;
    private Long target2;
    private String targetToken1;
    private String targetToken2;
    private String operatorToken;

    @BeforeEach
    void fixtures() throws Exception {
        orgId = jdbc.queryForObject(
                "SELECT org_id FROM sys_dept WHERE del_flag = 0 ORDER BY org_id LIMIT 1", Long.class);

        // 操作人：独立角色，只拿用户/角色管理权限，不与被测用户共享角色
        Long operatorRole = createRole("OP", "system:role:edit", "system:user:edit",
                "system:menu:add", "system:menu:edit", "system:menu:remove");
        Long operator = createUser("op", operatorRole);

        targetRoleId = createRole("TG", PROBE_PERM);
        target1 = createUser("t1", targetRoleId);
        target2 = createUser("t2", targetRoleId);

        operatorToken = login(operator);
        targetToken1 = login(target1);
        targetToken2 = login(target2);

        assertThat(probe(targetToken1)).as("夹具：被测用户登录后应能访问 " + PROBE).isEqualTo(200);
        assertThat(probe(targetToken2)).isEqualTo(200);
    }

    @AfterEach
    void cleanup() throws Exception {
        for (String token : tokens) {
            mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andReturn();
        }
        for (Long menuId : menuIds) {
            jdbc.update("DELETE FROM sys_role_menu WHERE menu_id = ?", menuId);
            jdbc.update("DELETE FROM sys_menu WHERE menu_id = ?", menuId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM sys_user WHERE user_id = ?", userId);
        }
        for (Long roleId : roleIds) {
            jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
            jdbc.update("DELETE FROM sys_user_role WHERE role_id = ?", roleId);
            jdbc.update("DELETE FROM sys_role WHERE role_id = ?", roleId);
        }
        for (String username : usernames) {
            jdbc.update("DELETE FROM sys_login_log WHERE username = ?", username);
            jdbc.update("DELETE FROM sys_oper_log WHERE oper_name = ?", username);
        }
    }

    // ------------------------------------------------------------------ 角色

    @Test
    void assigningRoleMenusInvalidatesEveryUserOfThatRole() throws Exception {
        JsonNode res = send(put("/system/role/" + targetRoleId + "/menus"), operatorToken, List.of());
        assertThat(res.path("code").asInt()).as(res.path("msg").asText()).isEqualTo(200);

        assertThat(probe(targetToken1)).as("角色授权变更后旧会话应失效").isEqualTo(401);
        assertThat(probe(targetToken2)).isEqualTo(401);
        assertThat(code(get("/auth/userInfo"), operatorToken)).as("不相关用户的会话不受影响").isEqualTo(200);
    }

    @Test
    void changingRoleStatusInvalidatesEveryUserOfThatRole() throws Exception {
        JsonNode res = send(put("/system/role/" + targetRoleId + "/status?status=0"), operatorToken, null);
        assertThat(res.path("code").asInt()).as(res.path("msg").asText()).isEqualTo(200);

        assertThat(probe(targetToken1)).isEqualTo(401);
        assertThat(probe(targetToken2)).isEqualTo(401);
    }

    @Test
    void rolledBackRoleMutationKeepsSessions() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            roleService.assignMenus(targetRoleId, List.of());
            status.setRollbackOnly();
        });

        Integer grants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = ?", Integer.class, targetRoleId);
        assertThat(grants).as("回滚后授权应仍在").isPositive();
        assertThat(probe(targetToken1)).as("事务回滚不应注销会话").isEqualTo(200);
        assertThat(probe(targetToken2)).isEqualTo(200);
    }

    // ------------------------------------------------------------------ 用户

    @Test
    void changingUserStatusInvalidatesOnlyThatUser() throws Exception {
        JsonNode res = send(put("/system/user/" + target1 + "/status?status=0"), operatorToken, null);
        assertThat(res.path("code").asInt()).as(res.path("msg").asText()).isEqualTo(200);

        assertThat(probe(targetToken1)).isEqualTo(401);
        assertThat(probe(targetToken2)).as("同角色的其他用户不受影响").isEqualTo(200);
    }

    @Test
    void updatingUserRolesInvalidatesThatUser() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", target1);
        body.put("roleIds", List.of());
        JsonNode res = send(put("/system/user"), operatorToken, body);
        assertThat(res.path("code").asInt()).as(res.path("msg").asText()).isEqualTo(200);

        assertThat(probe(targetToken1)).isEqualTo(401);
        assertThat(probe(targetToken2)).isEqualTo(200);

        // 重新登录后按新权限（已无角色）判定
        String fresh = login(target1);
        assertThat(probe(fresh)).as("重新登录后应按新权限拒绝").isEqualTo(403);
    }

    // ------------------------------------------------------------------ 菜单

    @Test
    void updatingMenuInvalidatesUsersHoldingIt() throws Exception {
        Long menuId = createMenu(targetRoleId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("menuId", menuId);
        body.put("parentId", 0);
        body.put("menuName", "PSI menu renamed");
        body.put("menuType", "F");
        body.put("status", 0);
        JsonNode res = send(put("/system/menu"), operatorToken, body);
        assertThat(res.path("code").asInt()).as(res.path("msg").asText()).isEqualTo(200);

        assertThat(probe(targetToken1)).as("菜单变更后拥有该菜单的用户会话应失效").isEqualTo(401);
        assertThat(probe(targetToken2)).isEqualTo(401);
        assertThat(code(get("/auth/userInfo"), operatorToken)).as("未持有该菜单的用户不受影响").isEqualTo(200);
    }

    @Test
    void removingMenuInvalidatesUsersCollectedBeforeAssociationCleanup() throws Exception {
        Long menuId = createMenu(targetRoleId);
        JsonNode res = send(delete("/system/menu/" + menuId), operatorToken, null);
        assertThat(res.path("code").asInt()).as(res.path("msg").asText()).isEqualTo(200);

        assertThat(probe(targetToken1)).isEqualTo(401);
        assertThat(probe(targetToken2)).isEqualTo(401);
    }

    @Test
    void addingMenuDoesNotInvalidateSessions() throws Exception {
        String name = "PSI menu add " + suffix();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentId", 0);
        body.put("menuName", name);
        body.put("menuType", "F");
        body.put("perms", "psi:add:" + suffix());
        JsonNode res = send(post("/system/menu"), operatorToken, body);
        assertThat(res.path("code").asInt()).as(res.path("msg").asText()).isEqualTo(200);
        menuIds.addAll(jdbc.queryForList("SELECT menu_id FROM sys_menu WHERE menu_name = ?", Long.class, name));

        assertThat(probe(targetToken1)).as("新增菜单不应注销现有会话").isEqualTo(200);
        assertThat(probe(targetToken2)).isEqualTo(200);
    }

    // ------------------------------------------------------------------ 夹具

    /** 建一个按钮菜单并授权给指定角色 */
    private Long createMenu(Long roleId) {
        String name = "PSI menu " + suffix();
        jdbc.update("INSERT INTO sys_menu (parent_id,menu_name,order_num,menu_type,visible,status,perms,remark) "
                + "VALUES (0,?,999,'F',0,1,?,'permission session regression')", name, "psi:probe:" + suffix());
        Long menuId = jdbc.queryForObject("SELECT menu_id FROM sys_menu WHERE menu_name = ?", Long.class, name);
        menuIds.add(menuId);
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (?,?)", roleId, menuId);
        return menuId;
    }

    private Long createRole(String marker, String... perms) {
        String roleKey = "PSI_" + marker + "_" + suffix();
        jdbc.update("INSERT INTO sys_role (role_name,role_key,role_sort,data_scope,is_builtin,status,remark) "
                + "VALUES (?,?,999,1,0,1,'permission session regression')", "PSI " + marker, roleKey);
        Long roleId = jdbc.queryForObject(
                "SELECT role_id FROM sys_role WHERE role_key = ? AND del_flag = 0", Long.class, roleKey);
        roleIds.add(roleId);
        for (String perm : perms) {
            int granted = jdbc.update(
                    "INSERT INTO sys_role_menu(role_id,menu_id) SELECT ?,menu_id FROM sys_menu WHERE perms = ?",
                    roleId, perm);
            assertThat(granted).as("sys_menu 里没有权限标识 %s", perm).isPositive();
        }
        return roleId;
    }

    private Long createUser(String marker, Long roleId) {
        String username = "psi_" + marker + "_" + suffix();
        String raw = "Ps!" + UUID.randomUUID().toString().replace("-", "") + "9a";
        jdbc.update("INSERT INTO sys_user (username,password,nick_name,org_id,status,pwd_update_date,remark) "
                        + "VALUES (?,?,?,?,1,NOW(),'permission session regression')",
                username, BCrypt.hashpw(raw), "PSI " + marker, orgId);
        Long userId = jdbc.queryForObject(
                "SELECT user_id FROM sys_user WHERE username = ? AND del_flag = 0", Long.class, username);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", userId, roleId);
        userIds.add(userId);
        usernames.add(username);
        passwords.put(username, raw);
        return userId;
    }

    private String login(Long userId) throws Exception {
        String username = jdbc.queryForObject("SELECT username FROM sys_user WHERE user_id = ?", String.class, userId);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                Map.of("username", username, "password", passwords.get(username)))))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.path("code").asInt()).as("%s 登录失败：%s", username, json.path("msg").asText()).isEqualTo(200);
        String token = json.path("data").path("token").asText();
        tokens.add(token);
        return token;
    }

    private int probe(String token) throws Exception {
        return code(get(PROBE), token);
    }

    private int code(MockHttpServletRequestBuilder builder, String token) throws Exception {
        return send(builder, token, null).path("code").asInt();
    }

    private JsonNode send(MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
