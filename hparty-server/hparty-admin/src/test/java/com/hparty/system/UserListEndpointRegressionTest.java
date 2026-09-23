package com.hparty.system;

import cn.hutool.crypto.digest.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 用户列表接口的回归测试（真实 Spring MVC + MySQL）。
 *
 * <p>锁定两个曾经出过问题的行为：</p>
 * <ol>
 *   <li>列表必须返回 {@code roleNames} —— 前端「角色」列读的就是它，
 *       早期只在详情接口填充，列表列恒为空；</li>
 *   <li>{@code org_id} 为 NULL 的账号（超级管理员这类无归属组织的账号）
 *       不能让列表接口抛空指针 —— 不可变空 Map 的 {@code get(null)} 会抛 NPE。</li>
 * </ol>
 *
 * <p>测试自建用户与角色，不依赖 demo 数据的具体 ID；整体事务回滚，不污染 dev 库。</p>
 */
@SpringBootTest(properties = "hparty.captcha.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class UserListEndpointRegressionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    /** 列表要带出角色名称，且按 role_sort 排序 */
    @Test
    void userListReturnsRoleNames() throws Exception {
        String suffix = uniqueSuffix();
        // 授权账号与目标账号都有组织，避免混入「无组织」这条路径
        String viewerUsername = "roleviewer_" + suffix;
        Long viewerId = createUser(viewerUsername, 2L);
        Long authRoleId = createRole("回归授权角色 " + suffix, "REG_AUTH_" + suffix, 900, 1);
        grantMenuPerm(authRoleId, "system:user:list");
        assignRole(viewerId, authRoleId);

        String targetUsername = "roletarget_" + suffix;
        Long targetId = createUser(targetUsername, 2L);
        Long firstRoleId = createRole("回归展示角色A " + suffix, "REG_SHOW_A_" + suffix, 901, 4);
        Long secondRoleId = createRole("回归展示角色B " + suffix, "REG_SHOW_B_" + suffix, 902, 4);
        assignRole(targetId, firstRoleId);
        assignRole(targetId, secondRoleId);

        String token = login(viewerUsername);
        try {
            JsonNode json = getJson("/system/user/list?username=" + targetUsername, token);

            assertThat(json.path("code").asInt()).isEqualTo(200);
            JsonNode records = json.path("data").path("records");
            assertThat(records).hasSize(1);
            assertThat(roleNamesOf(records.get(0)))
                    .containsExactly("回归展示角色A " + suffix, "回归展示角色B " + suffix);
        } finally {
            logout(token);
        }
    }

    /** 整页都是无组织账号时不能报空指针 —— 即「单独搜 admin」的场景 */
    @Test
    void userListWithOnlyOrglessUsersDoesNotFail() throws Exception {
        String suffix = uniqueSuffix();
        // 授权账号自己有组织（否则它自己也进不了列表范围），被查账号无组织
        Long viewerId = createUser("orglessviewer_" + suffix, 2L);
        Long authRoleId = createRole("无组织回归授权 " + suffix, "REG_OGLESS_" + suffix, 903, 1);
        grantMenuPerm(authRoleId, "system:user:list");
        assignRole(viewerId, authRoleId);

        String targetUsername = "orgless_" + suffix;
        createOrglessUser(targetUsername);

        String token = login("orglessviewer_" + suffix);
        try {
            JsonNode json = getJson("/system/user/list?username=" + targetUsername, token);

            assertThat(json.path("code").asInt()).isEqualTo(200);
            JsonNode records = json.path("data").path("records");
            assertThat(records).hasSize(1);
            // 响应体配了 default-property-inclusion=non_null，null 字段会整个省略，
            // 因此「无组织」表现为字段缺失或为 null，两者都算通过
            assertThat(isNullOrMissing(records.get(0), "orgId")).isTrue();
            assertThat(isNullOrMissing(records.get(0), "orgName")).isTrue();
            assertThat(roleNamesOf(records.get(0))).isEmpty();
        } finally {
            logout(token);
        }
    }

    // ==================== 断言辅助 ====================

    /** 字段为 null 或整个缺失（non_null 序列化）都视为「无值」 */
    private boolean isNullOrMissing(JsonNode record, String field) {
        JsonNode node = record.path(field);
        return node.isMissingNode() || node.isNull();
    }

    private List<String> roleNamesOf(JsonNode record) {
        JsonNode names = record.path("roleNames");
        assertThat(names.isArray()).as("roleNames 必须是数组，当前为 %s", names).isTrue();
        List<String> result = new ArrayList<>();
        names.forEach(name -> result.add(name.asText()));
        return result;
    }

    // ==================== 造数据 ====================

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** 创建无归属组织（org_id = NULL）的账号，形状与超级管理员一致 */
    private Long createOrglessUser(String username) {
        return createUser(username, null);
    }

    private Long createUser(String username, Long orgId) {
        jdbc.update("INSERT INTO sys_user (username,password,nick_name,org_id,status,pwd_update_date,remark) " +
                        "VALUES (?,?,?,?,1,NOW(),'user list regression')",
                username, BCrypt.hashpw(loginPassword(username)), username, orgId);
        return jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=? AND del_flag=0", Long.class, username);
    }

    private Long createRole(String roleName, String roleKey, int roleSort, int dataScope) {
        jdbc.update("INSERT INTO sys_role (role_name,role_key,role_sort,data_scope,is_builtin,status,remark) " +
                        "VALUES (?,?,?,?,0,1,'user list regression')",
                roleName, roleKey, roleSort, dataScope);
        return jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_key=? AND del_flag=0", Long.class, roleKey);
    }

    private void grantMenuPerm(Long roleId, String perms) {
        int rows = jdbc.update("INSERT INTO sys_role_menu (role_id,menu_id) SELECT ?,menu_id FROM sys_menu WHERE perms=?",
                roleId, perms);
        assertThat(rows).as("菜单权限 %s 必须存在", perms).isPositive();
    }

    private void assignRole(Long userId, Long roleId) {
        jdbc.update("INSERT INTO sys_user_role (user_id,role_id) VALUES (?,?)", userId, roleId);
    }

    // ==================== 登录与请求 ====================

    /** 每个测试账号使用独立口令，避免依赖 demo 账号的密码 */
    private String loginPassword(String username) {
        return "Rg!" + username + "9a";
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                Map.of("username", username, "password", loginPassword(username)))))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.path("code").asInt()).as("登录失败：%s", json).isEqualTo(200);
        return json.path("data").path("token").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void logout(String token) throws Exception {
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andReturn();
    }
}
