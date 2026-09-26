package com.hparty.security;

import cn.hutool.crypto.digest.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hparty.develop.mapper.DevPlanMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 管理节点（{@code OrgType.ADMIN_NODE}，V6 迁移）回归测试。
 *
 * <p>背景：超管原先没有归属组织，各模块新增时会把 null 写进业务表的 {@code NOT NULL} 的
 * {@code org_id}（MySQL 1364），前端只拿到一个没有信息量的 500。V6 建了一个
 * {@code org_type = 9} 的独立根作为超管归属，它<b>不是党组织</b>，不对外展示。</p>
 *
 * <p>本用例锁定四件事：</p>
 * <ol>
 *   <li>节点存在、是 {@code parent_id = 0} 的独立根、且 admin 确实挂在其下</li>
 *   <li>不对外展示 —— 组织列表与组织树都查不到</li>
 *   <li>不可按 ID 查看 / 修改 / 删除（只能由系统维护）</li>
 *   <li>「根组织回退」不会落到它头上（它也是 {@code parent_id = 0}）</li>
 * </ol>
 *
 * <p>再加两条端到端断言：11 个新增入口对超管一律 200 且落库的 org_id 就是管理节点；
 * 无归属组织的账号一律 600（而不是 500）。</p>
 */
// 属性集合必须与同模块其它用例保持一致，否则会多建一个 ApplicationContext。
// 上传目录之类的公共覆盖项统一放在 src/test/resources/config/application.yml。
@SpringBootTest(properties = "hparty.captcha.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AdminOrgCrudRegressionTest {

    private static final String NO_ORG_MSG = "未分配所属党组织";
    private static final String RULE_MSG = "管理节点";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DevPlanMapper devPlanMapper;

    // ------------------------------------------------------------------ 管理节点本身

    @Test
    void adminOrgNodeExistsAndAdminIsBoundToIt() {
        Long nodeId = adminNodeId();
        assertThat(nodeId).as("V6 迁移应建出 org_type=9 的管理节点").isNotNull();

        Map<String, Object> node = jdbc.queryForMap(
                "SELECT parent_id, org_path, org_level, org_name, status, del_flag FROM sys_dept WHERE org_id = ?", nodeId);
        assertThat(((Number) node.get("parent_id")).longValue()).as("管理节点是 parent_id=0 的独立根").isZero();
        assertThat(node.get("org_path")).as("物化路径要指向自己").isEqualTo("/" + nodeId + "/");
        assertThat(((Number) node.get("org_level")).intValue()).isEqualTo(1);
        assertThat(node.get("org_name")).isEqualTo("管理员");
        assertThat(((Number) node.get("status")).intValue()).isEqualTo(1);
        assertThat(((Number) node.get("del_flag")).intValue()).isZero();

        Long adminOrgId = jdbc.queryForObject(
                "SELECT org_id FROM sys_user WHERE username = 'admin' AND del_flag = 0", Long.class);
        assertThat(adminOrgId)
                .as("admin 必须有归属组织，否则各模块新增仍会写 null 到 NOT NULL 的 org_id")
                .isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dept WHERE org_id = ? AND del_flag = 0", Long.class, adminOrgId))
                .as("admin 的归属组织必须真实存在").isEqualTo(1L);
    }

    @Test
    void adminOrgNodeIsHiddenFromDeptListAndTree() throws Exception {
        Long nodeId = adminNodeId();
        String token = login("admin");
        try {
            JsonNode list = getJson("/system/dept/list?parentId=0&pageNum=1&pageSize=500", token);
            assertThat(list.path("code").asInt()).isEqualTo(200);
            List<Long> listIds = new ArrayList<>();
            list.path("data").path("records").forEach(record -> listIds.add(record.path("orgId").asLong()));
            assertThat(listIds).as("组织列表不得出现管理节点").doesNotContain(nodeId);

            JsonNode tree = getJson("/system/dept/tree", token);
            assertThat(tree.path("code").asInt()).isEqualTo(200);
            assertThat(flattenOrgIds(tree.path("data"))).as("组织树不得出现管理节点").doesNotContain(nodeId);
        } finally {
            logout(token);
        }
    }

    @Test
    void adminOrgNodeRejectsByIdCrud() throws Exception {
        Long nodeId = adminNodeId();
        String token = login("admin");
        try {
            JsonNode info = getJson("/system/dept/" + nodeId, token);
            assertThat(info.path("code").asInt()).isEqualTo(600);
            assertThat(info.path("msg").asText()).contains(RULE_MSG);

            JsonNode edit = send(put("/system/dept"), token, body("orgId", nodeId, "orgName", "改名测试"));
            assertThat(edit.path("code").asInt()).as("管理节点不可修改").isEqualTo(600);

            JsonNode remove = send(delete("/system/dept/" + nodeId), token, null);
            assertThat(remove.path("code").asInt()).as("管理节点不可删除").isEqualTo(600);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_dept WHERE org_id = ? AND del_flag = 0", Long.class, nodeId))
                    .as("被拒后管理节点必须仍在").isEqualTo(1L);
        } finally {
            logout(token);
        }
    }

    /**
     * 管理节点也是 {@code parent_id = 0}，若根组织回退不排除它，未分配组织的账号会拿它当党组织树的根。
     */
    @Test
    void rootOrgLookupNeverFallsBackToAdminNode() {
        Long nodeId = adminNodeId();
        Long rootId = devPlanMapper.selectRootOrgId();
        Long realRootCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dept WHERE parent_id = 0 AND org_type <> 9 AND del_flag = 0", Long.class);

        if (realRootCount == null || realRootCount == 0) {
            assertThat(rootId).as("没有真实党组织根时不应回退到管理节点").isNull();
        } else {
            assertThat(rootId).as("有真实党组织根时应能取到").isNotNull().isNotEqualTo(nodeId);
            assertThat(jdbc.queryForObject(
                    "SELECT org_type FROM sys_dept WHERE org_id = ?", Integer.class, rootId))
                    .as("取到的根必须是党组织而非管理节点").isNotEqualTo(9);
        }
    }

    // ------------------------------------------------------------------ 各模块新增入口

    /** 超管在 11 个入口都能建出数据，且落库的 org_id 就是管理节点。 */
    @Test
    void adminCanCreateInEveryModule() throws Exception {
        Long nodeId = adminNodeId();
        Long personId = createPerson(2L, "admin-crud");
        String token = login("admin");
        try {
            Long taskId = createTask(token, nodeId);

            for (CreateSite site : createSites(personId)) {
                JsonNode json = send(post(site.path()), token, site.body().get());
                assertThat(json.path("code").asInt())
                        .as("%s：超管新增应成功，实际返回 %s", site.name(), json.path("msg").asText())
                        .isEqualTo(200);
                Long id = json.path("data").asLong();
                assertThat(id).as("%s：应返回新增主键", site.name()).isPositive();
                assertThat(rowOrgId(site, id))
                        .as("%s：新增数据的 %s 应为管理节点", site.name(), site.orgColumn())
                        .isEqualTo(nodeId);
            }

            // 「提交任务材料」的组织取自登录会话，无入参，单独走一次。
            // 必须带附件：Service 已拒绝无附件的空提交（见 AmTaskCrudRegressionTest）
            JsonNode submit = send(multipart("/party/task/submit")
                            .file(new MockMultipartFile("file", "org-node.txt", "text/plain",
                                    "admin-node-submit".getBytes()))
                            .param("taskId", String.valueOf(taskId)),
                    token, null);
            assertThat(submit.path("code").asInt())
                    .as("任务提交应成功，实际返回 %s", submit.path("msg").asText()).isEqualTo(200);
            assertThat(jdbc.queryForObject(
                    "SELECT org_id FROM am_task_submit WHERE submit_id = ?", Long.class, submit.path("data").asLong()))
                    .as("任务提交记录的组织应为管理节点").isEqualTo(nodeId);
        } finally {
            logout(token);
        }
    }

    /** 无归属组织的账号：11 个入口都应给出可读的业务错误（600），而不是 MySQL 1364 转成的 500。 */
    @Test
    void accountWithoutOrgGetsBizErrorInsteadOfSqlFailure() throws Exception {
        Long personId = createPerson(2L, "no-org");
        Long taskId = createTaskAsAdmin();

        List<CreateSite> sites = new ArrayList<>(createSites(personId));
        String[] perms = new String[sites.size() + 1];
        for (int i = 0; i < sites.size(); i++) {
            perms[i] = sites.get(i).perm();
        }
        perms[sites.size()] = "task:submit";

        String token = login(createNoOrgUser(perms));
        try {
            for (CreateSite site : sites) {
                JsonNode json = send(post(site.path()), token, site.body().get());
                assertThat(json.path("code").asInt())
                        .as("%s：无归属组织应返回业务错误而不是 500（实际 %s）", site.name(), json.path("msg").asText())
                        .isEqualTo(600);
                assertThat(json.path("msg").asText())
                        .as("%s：错误信息应说清原因", site.name()).contains(NO_ORG_MSG);
            }

            JsonNode submit = send(post("/party/task/submit").param("taskId", String.valueOf(taskId)),
                    token, null);
            assertThat(submit.path("code").asInt()).isEqualTo(600);
            assertThat(submit.path("msg").asText()).contains(NO_ORG_MSG);
        } finally {
            logout(token);
        }
    }

    // ------------------------------------------------------------------ 用例数据与工具

    /**
     * 一个「新增」入口。{@code body} 用 {@code Supplier} 是因为党费缴纳记录要带上运行期生成的人员 ID。
     *
     * @param perm 与 Controller 上 {@code @SaCheckPermission} 一字不差
     */
    private record CreateSite(String name, String path, Supplier<Map<String, Object>> body, String perm,
                              String table, String idColumn, String orgColumn) {
    }

    private List<CreateSite> createSites(Long personId) {
        return List.of(
                new CreateSite("三会一课-会议", "/party/meeting",
                        () -> body("meetingType", "MEMBER_ASSEMBLY", "title", unique("meeting"),
                                "shouldAttend", 10, "actualAttend", 8),
                        "meeting:add", "am_meeting", "meeting_id", "org_id"),
                new CreateSite("三会一课-任务", "/party/task",
                        // task_type 是 NOT NULL 且无默认值，必须传
                        () -> body("title", unique("task"), "taskType", "MEMBER_ASSEMBLY"),
                        "task:add", "am_task", "task_id", "publish_org_id"),
                new CreateSite("组织生活会-材料", "/party/material",
                        () -> body("category", "NOTICE", "title", unique("material")),
                        "orglife:add", "am_material", "material_id", "org_id"),
                new CreateSite("党纪学习教育", "/party/discipline",
                        () -> body("title", unique("discipline")),
                        "discipline:add", "discipline_study", "study_id", "org_id"),
                new CreateSite("党员教育-活动", "/party/education",
                        () -> body("title", unique("education")),
                        "education:add", "edu_activity", "activity_id", "org_id"),
                new CreateSite("先优评选", "/party/excellent/selection",
                        () -> body("title", unique("excellent")),
                        "excellent:add", "excellent_selection", "selection_id", "org_id"),
                new CreateSite("党员服务", "/party/service",
                        () -> body("title", unique("service")),
                        "service:add", "member_service", "service_id", "org_id"),
                new CreateSite("党组织换届", "/party/election",
                        () -> body("title", unique("election")),
                        "election:add", "org_election", "election_id", "org_id"),
                new CreateSite("党费-缴纳记录", "/party/dues/record",
                        () -> body("personId", personId, "duesYear", 2026, "duesMonth", 3, "duesBase", 3000),
                        "dues:add", "party_dues_record", "dues_id", "org_id"),
                new CreateSite("党费-使用记录", "/party/dues/use",
                        () -> body("amount", 500, "purpose", unique("dues-use")),
                        "dues:use:add", "party_dues_use", "use_id", "org_id"));
    }

    private Long createTask(String token, Long expectedOrgId) throws Exception {
        JsonNode json = send(post("/party/task"), token,
                body("title", unique("task-for-submit"), "taskType", "MEMBER_ASSEMBLY"));
        assertThat(json.path("code").asInt())
                .as("建任务失败：%s", json.path("msg").asText()).isEqualTo(200);
        Long taskId = json.path("data").asLong();
        assertThat(jdbc.queryForObject(
                "SELECT publish_org_id FROM am_task WHERE task_id = ?", Long.class, taskId)).isEqualTo(expectedOrgId);
        return taskId;
    }

    /** 「任务提交」需要一个真实任务，用超管建一个当夹具。 */
    private Long createTaskAsAdmin() throws Exception {
        String token = login("admin");
        try {
            return createTask(token, adminNodeId());
        } finally {
            logout(token);
        }
    }

    private Long rowOrgId(CreateSite site, Long id) {
        return jdbc.queryForObject(
                "SELECT " + site.orgColumn() + " FROM " + site.table() + " WHERE " + site.idColumn() + " = ?",
                Long.class, id);
    }

    private Long adminNodeId() {
        return jdbc.query(
                "SELECT org_id FROM sys_dept WHERE org_type = 9 AND del_flag = 0 ORDER BY org_id LIMIT 1",
                rs -> rs.next() ? rs.getLong("org_id") : null);
    }

    private List<Long> flattenOrgIds(JsonNode nodes) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : nodes) {
            ids.add(node.path("orgId").asLong());
            ids.addAll(flattenOrgIds(node.path("children")));
        }
        return ids;
    }

    private static String unique(String marker) {
        return "reg-" + marker + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> body(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private Long createPerson(Long orgId, String marker) {
        String name = "AdminOrg " + marker + " " + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO party_person (name,org_id,member_status,is_member,status,remark) "
                + "VALUES (?,?,1,0,1,'admin org regression')", name, orgId);
        return jdbc.queryForObject("SELECT person_id FROM party_person WHERE name=? AND del_flag=0", Long.class, name);
    }

    /** data_scope=1 且没有归属组织的账号：除 org_id 为 null 外，其余条件都足以通过业务校验。 */
    private String createNoOrgUser(String... perms) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "noorg_" + suffix;
        jdbc.update("INSERT INTO sys_user (username,password,nick_name,org_id,status,pwd_update_date,remark) "
                        + "VALUES (?,?,?,NULL,1,NOW(),'admin org regression')",
                username, BCrypt.hashpw("bootstrap-" + suffix), "No Org Test");
        Long userId = jdbc.queryForObject(
                "SELECT user_id FROM sys_user WHERE username=? AND del_flag=0", Long.class, username);

        String roleKey = "NO_ORG_TEST_" + suffix;
        jdbc.update("INSERT INTO sys_role (role_name,role_key,role_sort,data_scope,is_builtin,status,remark) "
                + "VALUES ('No Org Test',?,999,1,0,1,'admin org regression')", roleKey);
        Long roleId = jdbc.queryForObject(
                "SELECT role_id FROM sys_role WHERE role_key=? AND del_flag=0", Long.class, roleKey);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", userId, roleId);
        for (String perm : perms) {
            int granted = jdbc.update(
                    "INSERT INTO sys_role_menu(role_id,menu_id) SELECT ?,menu_id FROM sys_menu WHERE perms=?",
                    roleId, perm);
            assertThat(granted).as("sys_menu 里没有权限标识 %s，用例无法覆盖该入口", perm).isEqualTo(1);
        }
        return username;
    }

    private String login(String username) throws Exception {
        String raw = "Rg!" + UUID.randomUUID().toString().replace("-", "") + "9a";
        jdbc.update("UPDATE sys_user SET password=?,pwd_update_date=NOW() WHERE username=?", BCrypt.hashpw(raw), username);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("username", username, "password", raw))))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.path("code").asInt()).as("%s 登录失败：%s", username, json.path("msg").asText()).isEqualTo(200);
        return json.path("data").path("token").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return send(get(path), token, null);
    }

    private JsonNode send(MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void logout(String token) throws Exception {
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andReturn();
    }
}
