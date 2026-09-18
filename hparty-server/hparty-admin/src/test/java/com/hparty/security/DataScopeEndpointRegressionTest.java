package com.hparty.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.hutool.crypto.digest.BCrypt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "hparty.captcha.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DataScopeEndpointRegressionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void selfScopeCannotReadAnotherPersonInSameOrgById() throws Exception {
        Long ownPersonId = createPerson(2L, "self-own");
        Long otherPersonId = createPerson(2L, "self-other");
        Long ownApplicantId = createApplicant(ownPersonId, 2L);
        Long otherApplicantId = createApplicant(otherPersonId, 2L);

        String username = createScopedUser(4, ownPersonId, 2L, null);
        String token = login(username);
        try {
            assertThat(getJson("/develop/applicant/" + ownApplicantId + "/timeline", token).path("code").asInt())
                    .isEqualTo(200);

            JsonNode denied = getJson("/develop/applicant/" + otherApplicantId + "/timeline", token);
            assertThat(denied.path("code").asInt()).isEqualTo(403);
            assertThat(denied.path("data").isNull() || denied.path("data").isMissingNode()).isTrue();

            JsonNode missing = getJson("/develop/applicant/9223372036854775000/timeline", token);
            assertThat(missing.path("code").asInt()).isEqualTo(600);
        } finally {
            logout(token);
        }
    }

    @Test
    void customScopeCannotReadUnassignedOrgById() throws Exception {
        Long allowedPersonId = createPerson(2L, "custom-allowed");
        Long deniedPersonId = createPerson(3L, "custom-denied");
        Long allowedApplicantId = createApplicant(allowedPersonId, 2L);
        Long deniedApplicantId = createApplicant(deniedPersonId, 3L);

        String username = createScopedUser(5, null, 2L, 2L);
        String token = login(username);
        try {
            assertThat(getJson("/develop/applicant/" + allowedApplicantId + "/timeline", token).path("code").asInt())
                    .isEqualTo(200);

            JsonNode denied = getJson("/develop/applicant/" + deniedApplicantId + "/timeline", token);
            assertThat(denied.path("code").asInt()).isEqualTo(403);
            assertThat(denied.path("data").isNull() || denied.path("data").isMissingNode()).isTrue();
        } finally {
            logout(token);
        }
    }

    @Test
    void applicantRoleCanReadOwnTimelineButNotAnotherPersonsTimeline() throws Exception {
        Long ownPersonId = createPerson(2L, "applicant-own");
        Long otherPersonId = createPerson(2L, "applicant-other");
        Long ownApplicantId = createApplicant(ownPersonId, 2L);
        Long otherApplicantId = createApplicant(otherPersonId, 2L);

        String username = createBuiltInRoleUser("APPLICANT", ownPersonId, 2L);
        String token = login(username);
        try {
            assertThat(getJson("/develop/applicant/" + ownApplicantId + "/timeline", token).path("code").asInt())
                    .isEqualTo(200);
            assertThat(getJson("/develop/applicant/" + otherApplicantId + "/timeline", token).path("code").asInt())
                    .isEqualTo(403);
        } finally {
            logout(token);
        }
    }

    private Long createPerson(Long orgId, String marker) {
        String name = "Scope " + marker + " " + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO party_person (name,org_id,member_status,is_member,status,remark) VALUES (?,?,1,0,1,'datascope regression')",
                name, orgId);
        return jdbc.queryForObject("SELECT person_id FROM party_person WHERE name=? AND del_flag=0", Long.class, name);
    }

    private Long createApplicant(Long personId, Long orgId) {
        jdbc.update("INSERT INTO dev_applicant (person_id,org_id,current_stage,current_step,status,progress,remark) " +
                        "VALUES (?,?,'STAGE_1','STEP_01',1,4,'datascope regression')",
                personId, orgId);
        return jdbc.queryForObject("SELECT applicant_id FROM dev_applicant WHERE person_id=? AND del_flag=0", Long.class, personId);
    }

    private String createScopedUser(int dataScope, Long personId, Long orgId, Long customOrgId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "scope_" + dataScope + "_" + suffix;
        String roleKey = "SCOPE_TEST_" + dataScope + "_" + suffix;
        String password = BCrypt.hashpw("bootstrap-" + suffix);

        jdbc.update("INSERT INTO sys_user (username,password,nick_name,person_id,org_id,status,pwd_update_date,remark) " +
                        "VALUES (?,?,?, ?,?,1,NOW(),'datascope regression')",
                username, password, "DataScope Test", personId, orgId);
        Long userId = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=? AND del_flag=0", Long.class, username);

        jdbc.update("INSERT INTO sys_role (role_name,role_key,role_sort,data_scope,is_builtin,status,remark) " +
                        "VALUES ('DataScope Test',?,999,?,0,1,'datascope regression')",
                roleKey, dataScope);
        Long roleId = jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_key=? AND del_flag=0", Long.class, roleKey);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", userId, roleId);
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) SELECT ?,menu_id FROM sys_menu WHERE perms IN ('develop:applicant:list','develop:applicant:detail')", roleId);
        if (dataScope == 5 && customOrgId != null) {
            jdbc.update("INSERT INTO sys_role_dept(role_id,org_id) VALUES (?,?)", roleId, customOrgId);
        }
        return username;
    }

    private String createBuiltInRoleUser(String roleKey, Long personId, Long orgId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "builtin_" + roleKey.toLowerCase() + "_" + suffix;
        String password = BCrypt.hashpw("bootstrap-" + suffix);

        jdbc.update("INSERT INTO sys_user (username,password,nick_name,person_id,org_id,status,pwd_update_date,remark) " +
                        "VALUES (?,?,?,?,?,1,NOW(),'datascope regression')",
                username, password, "Built-in Role Test", personId, orgId);
        Long userId = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=? AND del_flag=0", Long.class, username);
        Long roleId = jdbc.queryForObject(
                "SELECT role_id FROM sys_role WHERE role_key=? AND is_builtin=1 AND status=1 AND del_flag=0",
                Long.class, roleKey);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", userId, roleId);
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
        assertThat(json.path("code").asInt()).isEqualTo(200);
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
