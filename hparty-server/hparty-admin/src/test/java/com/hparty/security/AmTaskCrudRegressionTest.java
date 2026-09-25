package com.hparty.security;

import cn.hutool.crypto.digest.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 活动任务通知（{@code /party/task}）CRUD 与越权回归。
 *
 * <p>锁定以下几类曾经缺失或容易改坏的行为：</p>
 * <ul>
 *   <li>发布 / 详情 / 修改 / 删除 / 提交记录 / 上传材料 的完整往返；</li>
 *   <li>按主键的详情、修改、删除、提交必须做组织越权校验 —— 列表条件拦不住直接构造 ID 的请求；</li>
 *   <li>{@code publish_org_id} 不能被调用方通过 PUT「过户」到别的组织；</li>
 *   <li>无附件的提交必须被拒绝（历史版本会静默落一条空记录）；</li>
 *   <li>四个写接口的 {@code @OperLog} 审计 —— 成功与失败都要留痕。</li>
 * </ul>
 */
// 属性集合必须与 AdminOrgCrudRegressionTest 等保持一致，否则会多建一个 ApplicationContext，
// 进而踩到 OrgPathResolver 的 static DataScopeMapper 被后建上下文覆盖的坑。
// 上传目录由 src/test/resources/config/application.yml 统一指向 target/test-uploads。
@SpringBootTest(properties = "hparty.captcha.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AmTaskCrudRegressionTest {

    /** 用例上传的任务材料落盘目录，只清理自己这一类，不动同目录下别的用例的产物 */
    private static final Path TEST_UPLOAD_ROOT = Path.of("target/test-uploads/task_material");

    /** 活动任务四个写接口 + 读接口的权限标识，与 V2 迁移种下的菜单一致 */
    private static final String TASK_PERMS = "'task:list','task:add','task:edit','task:remove','task:submit'";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void cleanTestUploads() throws IOException {
        if (!Files.exists(TEST_UPLOAD_ROOT)) return;
        try (var paths = Files.walk(TEST_UPLOAD_ROOT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test
    void crudRoundTripKeepsUntouchedColumns() throws Exception {
        List<Long> orgs = twoDistinctOrgs();
        String username = createTaskUser(orgs.get(0), 2);
        String token = login(username);
        try {
            JsonNode created = postJson("/party/task", taskBody("原始标题"), token);
            assertThat(created.path("code").asInt()).isEqualTo(200);
            Long taskId = created.path("data").asLong();

            // 发布组织、发布人、发布时间由后端填充，不取前端传值
            assertThat(orgId(taskId)).isEqualTo(orgs.get(0));
            assertThat(column(taskId, "publish_by", String.class)).isEqualTo(username);
            assertThat(column(taskId, "publish_org_name", String.class)).isNotBlank();
            assertThat(column(taskId, "publish_time", Object.class)).isNotNull();

            JsonNode detail = getJson("/party/task/" + taskId, token);
            assertThat(detail.path("code").asInt()).isEqualTo(200);
            assertThat(detail.path("data").path("title").asText()).isEqualTo("原始标题");

            // 筛选：命中自己的类型与状态，换一个状态就查不到
            assertThat(listIds("/party/task/list?status=1", token)).contains(taskId);
            assertThat(listIds("/party/task/list?taskType=MEMBER_ASSEMBLY&status=1", token)).contains(taskId);
            assertThat(listIds("/party/task/list?status=3", token)).doesNotContain(taskId);

            // 只改标题与截止日期：其余列按 MyBatis-Plus 的 NOT_NULL 策略保持原值
            Map<String, Object> patch = new HashMap<>();
            patch.put("taskId", taskId);
            patch.put("title", "修改后标题");
            patch.put("deadline", "2026-10-31");
            assertThat(putJson("/party/task", patch, token).path("code").asInt()).isEqualTo(200);
            assertThat(column(taskId, "title", String.class)).isEqualTo("修改后标题");
            assertThat(column(taskId, "activity_name", String.class)).isEqualTo("学党纪强党性主题党日");
            assertThat(column(taskId, "content", String.class)).isEqualTo("活动内容与报送要求");
            assertThat(column(taskId, "start_date", String.class)).isEqualTo("2026-09-01");

            assertThat(deleteJson("/party/task/" + taskId, token).path("code").asInt()).isEqualTo(200);
            assertThat(column(taskId, "del_flag", Integer.class)).isEqualTo(1);
        } finally {
            logout(token);
        }
    }

    @Test
    void publishOrgCannotBeTransferredByUpdate() throws Exception {
        List<Long> orgs = twoDistinctOrgs();
        String username = createTaskUser(orgs.get(0), 2);
        String token = login(username);
        try {
            Long taskId = postJson("/party/task", taskBody("过户测试"), token).path("data").asLong();

            Map<String, Object> patch = taskBody("过户测试");
            patch.put("taskId", taskId);
            // 试图把任务挂到另一个组织的名下
            patch.put("publishOrgId", orgs.get(1));
            patch.put("publishOrgName", "伪造的发布单位");
            assertThat(putJson("/party/task", patch, token).path("code").asInt()).isEqualTo(200);

            assertThat(orgId(taskId)).isEqualTo(orgs.get(0));
            assertThat(column(taskId, "publish_org_name", String.class)).isNotEqualTo("伪造的发布单位");
        } finally {
            logout(token);
        }
    }

    @Test
    void writeEndpointsAreRejectedForAnotherOrgsTaskById() throws Exception {
        List<Long> orgs = twoDistinctOrgs();
        String ownerToken = login(createTaskUser(orgs.get(0), 2));
        String strangerToken = login(createTaskUser(orgs.get(1), 2));
        try {
            Long taskId = postJson("/party/task", taskBody("跨组织任务"), ownerToken).path("data").asLong();

            // 列表层必须看不到
            assertThat(listIds("/party/task/list", strangerToken)).doesNotContain(taskId);

            // 按主键的四个入口逐个校验：只靠列表条件拦不住构造 ID 的请求
            assertThat(getJson("/party/task/" + taskId, strangerToken).path("code").asInt()).isEqualTo(403);
            assertThat(getJson("/party/task/" + taskId + "/submits", strangerToken).path("code").asInt())
                    .isEqualTo(403);
            assertThat(updateJson(taskId, strangerToken).path("code").asInt()).isEqualTo(403);
            assertThat(deleteJson("/party/task/" + taskId, strangerToken).path("code").asInt()).isEqualTo(403);
            assertThat(submitWithoutFile(taskId, strangerToken).path("code").asInt()).isEqualTo(403);

            // 未授权的一次都没落地
            assertThat(column(taskId, "title", String.class)).isEqualTo("跨组织任务");
            assertThat(column(taskId, "del_flag", Integer.class)).isEqualTo(0);
            assertThat(submitCount(taskId)).isZero();
        } finally {
            logout(ownerToken);
            logout(strangerToken);
        }
    }

    @Test
    void addRejectsBlankTitleAndForeignPublishOrg() throws Exception {
        List<Long> orgs = twoDistinctOrgs();
        String token = login(createTaskUser(orgs.get(0), 2));
        try {
            Map<String, Object> blank = taskBody("  ");
            assertThat(postJson("/party/task", blank, token).path("code").asInt()).isEqualTo(600);

            Map<String, Object> foreign = taskBody("别人的任务");
            foreign.put("publishOrgId", orgs.get(1));
            assertThat(postJson("/party/task", foreign, token).path("code").asInt()).isEqualTo(403);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM am_task WHERE title IN ('  ','别人的任务') AND del_flag=0",
                    Integer.class)).isZero();
        } finally {
            logout(token);
        }
    }

    @Test
    void updateRejectsBlankTitleButKeepsStoredValue() throws Exception {
        List<Long> orgs = twoDistinctOrgs();
        String token = login(createTaskUser(orgs.get(0), 2));
        try {
            Long taskId = postJson("/party/task", taskBody("原标题"), token).path("data").asLong();

            Map<String, Object> patch = new HashMap<>();
            patch.put("taskId", taskId);
            patch.put("title", "");
            JsonNode rejected = putJson("/party/task", patch, token);
            assertThat(rejected.path("code").asInt()).isEqualTo(600);
            assertThat(rejected.path("msg").asText()).contains("任务标题");
            assertThat(column(taskId, "title", String.class)).isEqualTo("原标题");
        } finally {
            logout(token);
        }
    }

    @Test
    void submitRequiresAnAttachmentAndRegistersIt() throws Exception {
        List<Long> orgs = twoDistinctOrgs();
        String username = createTaskUser(orgs.get(0), 2);
        String token = login(username);
        try {
            Long taskId = postJson("/party/task", taskBody("材料报送"), token).path("data").asLong();

            // 三个附件参数全空 → 拒绝，且不落记录
            JsonNode rejected = submitWithoutFile(taskId, token);
            assertThat(rejected.path("code").asInt()).isEqualTo(600);
            assertThat(rejected.path("msg").asText()).contains("请上传任务材料");
            assertThat(submitCount(taskId)).isZero();
            assertThat(column(taskId, "status", Integer.class)).isEqualTo(1);

            // 带文件提交 → 落记录并登记 sys_file
            assertThat(submitWithFile(taskId, token, "report.txt").path("code").asInt()).isEqualTo(200);
            assertThat(submitCount(taskId)).isEqualTo(1);

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT org_id,file_id,file_url,submit_by FROM am_task_submit WHERE task_id=?", taskId);
            assertThat(row.get("org_id")).isEqualTo(orgs.get(0));
            assertThat(row.get("submit_by")).isEqualTo(username);
            assertThat((Long) row.get("file_id")).isNotNull();
            assertThat((String) row.get("file_url")).isNotBlank();

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_file WHERE file_id=? AND biz_type='task_material' AND biz_id=?",
                    Integer.class, row.get("file_id"), taskId)).isEqualTo(1);

            // 首次提交把任务从「已发布」推进到「已截止」
            assertThat(column(taskId, "status", Integer.class)).isEqualTo(2);

            JsonNode submits = getJson("/party/task/" + taskId + "/submits", token);
            assertThat(submits.path("code").asInt()).isEqualTo(200);
            assertThat(submits.path("data").size()).isEqualTo(1);
            assertThat(submits.path("data").get(0).path("submitBy").asText()).isEqualTo(username);
        } finally {
            logout(token);
        }
    }

    @Test
    void taskWritesAreAuditedOnSuccessAndFailure() throws Exception {
        List<Long> orgs = twoDistinctOrgs();
        String username = createTaskUser(orgs.get(0), 2);
        String token = login(username);
        try {
            Long taskId = postJson("/party/task", taskBody("审计测试"), token).path("data").asLong();
            assertThat(operLogCount(username, 1, 1)).isEqualTo(1);

            // 被拒绝的修改同样要留痕（status=0），否则越权与规则失败就查不到了
            Map<String, Object> patch = new HashMap<>();
            patch.put("taskId", taskId);
            patch.put("title", "");
            assertThat(putJson("/party/task", patch, token).path("code").asInt()).isEqualTo(600);
            assertThat(operLogCount(username, 2, 0)).isEqualTo(1);
            assertThat(operLogCount(username, 2, 1)).isZero();

            assertThat(submitWithoutFile(taskId, token).path("code").asInt()).isEqualTo(600);
            assertThat(operLogCount(username, 6, 0)).isEqualTo(1);

            assertThat(deleteJson("/party/task/" + taskId, token).path("code").asInt()).isEqualTo(200);
            assertThat(operLogCount(username, 3, 1)).isEqualTo(1);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_oper_log WHERE oper_name=? AND title='活动任务通知'",
                    Integer.class, username)).isEqualTo(4);
        } finally {
            logout(token);
        }
    }

    // ==================== 辅助方法 ====================

    /** 取两个互不相同的组织，用于构造跨组织越权场景 */
    private List<Long> twoDistinctOrgs() {
        List<Long> ids = jdbc.queryForList("SELECT org_id FROM sys_dept WHERE del_flag=0 ORDER BY org_id", Long.class);
        assertThat(ids.size()).isGreaterThanOrEqualTo(2);
        return List.of(ids.get(0), ids.get(1));
    }

    private Map<String, Object> taskBody(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("taskType", "MEMBER_ASSEMBLY");
        body.put("activityName", "学党纪强党性主题党日");
        body.put("content", "活动内容与报送要求");
        body.put("startDate", "2026-09-01");
        body.put("endDate", "2026-09-30");
        body.put("deadline", "2026-09-25");
        body.put("status", 1);
        return body;
    }

    private Long orgId(Long taskId) {
        return column(taskId, "publish_org_id", Long.class);
    }

    private <T> T column(Long taskId, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM am_task WHERE task_id=?", type, taskId);
    }

    private int submitCount(Long taskId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM am_task_submit WHERE task_id=?", Integer.class, taskId);
        return count == null ? 0 : count;
    }

    private int operLogCount(String username, int businessType, int status) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_oper_log WHERE oper_name=? AND title='活动任务通知' "
                        + "AND business_type=? AND status=?",
                Integer.class, username, businessType, status);
        return count == null ? 0 : count;
    }

    private List<Long> listIds(String path, String token) throws Exception {
        JsonNode json = getJson(path, token);
        assertThat(json.path("code").asInt()).isEqualTo(200);
        return json.path("data").findValuesAsText("taskId").stream().map(Long::valueOf).toList();
    }

    private JsonNode updateJson(Long taskId, String token) throws Exception {
        Map<String, Object> patch = taskBody("被越权的标题");
        patch.put("taskId", taskId);
        return putJson("/party/task", patch, token);
    }

    private JsonNode submitWithoutFile(Long taskId, String token) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/party/task/submit")
                        .param("taskId", String.valueOf(taskId))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return parse(result);
    }

    private JsonNode submitWithFile(Long taskId, String token, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "text/plain", ("content-" + fileName).getBytes());
        MvcResult result = mockMvc.perform(multipart("/party/task/submit")
                        .file(file)
                        .param("taskId", String.valueOf(taskId))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return parse(result);
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return parse(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn());
    }

    private JsonNode postJson(String path, Object body, String token) throws Exception {
        return parse(mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body))
                        .header("Authorization", "Bearer " + token))
                .andReturn());
    }

    private JsonNode putJson(String path, Object body, String token) throws Exception {
        return parse(mockMvc.perform(put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body))
                        .header("Authorization", "Bearer " + token))
                .andReturn());
    }

    private JsonNode deleteJson(String path, String token) throws Exception {
        return parse(mockMvc.perform(delete(path).header("Authorization", "Bearer " + token)).andReturn());
    }

    private JsonNode parse(MvcResult result) throws Exception {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        if (bytes.length == 0) {
            return objectMapper.createObjectNode().put("httpStatus", result.getResponse().getStatus());
        }
        return objectMapper.readTree(bytes);
    }

    /** 建一个只拥有活动任务权限、数据范围为指定值的账号 */
    private String createTaskUser(Long orgId, int dataScope) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "task_" + suffix;
        String roleKey = "TASK_TEST_" + suffix;

        jdbc.update("INSERT INTO sys_user (username,password,nick_name,org_id,status,pwd_update_date,remark) "
                        + "VALUES (?,?,?,?,1,NOW(),'task regression')",
                username, BCrypt.hashpw("placeholder"), "Task Regression", orgId);
        Long userId = jdbc.queryForObject(
                "SELECT user_id FROM sys_user WHERE username=? AND del_flag=0", Long.class, username);

        jdbc.update("INSERT INTO sys_role (role_name,role_key,role_sort,data_scope,is_builtin,status,remark) "
                        + "VALUES ('Task Test',?,999,?,0,1,'task regression')",
                roleKey, dataScope);
        Long roleId = jdbc.queryForObject(
                "SELECT role_id FROM sys_role WHERE role_key=? AND del_flag=0", Long.class, roleKey);

        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", userId, roleId);
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) SELECT ?,menu_id FROM sys_menu "
                + "WHERE perms IN (" + TASK_PERMS + ")", roleId);
        return username;
    }

    private String login(String username) throws Exception {
        String raw = "Tk!" + UUID.randomUUID().toString().replace("-", "") + "9a";
        jdbc.update("UPDATE sys_user SET password=?,pwd_update_date=NOW() WHERE username=?",
                BCrypt.hashpw(raw), username);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("username", username, "password", raw))))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.path("code").asInt()).isEqualTo(200);
        return json.path("data").path("token").asText();
    }

    private void logout(String token) throws Exception {
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andReturn();
    }
}
