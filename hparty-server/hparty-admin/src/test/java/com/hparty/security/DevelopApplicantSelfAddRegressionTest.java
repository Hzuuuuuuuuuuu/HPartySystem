package com.hparty.security;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 发展对象新增的「本人不可自报」回归。
 *
 * <p>页面上候选人下拉来自人员档案列表，当前登录账号若已关联人员档案，本人就在其中。
 * 修复前组织委员可以把自己选为发展对象 —— 发展对象须由组织指定，本人不得自报自审。</p>
 */
@SpringBootTest(properties = {
        "hparty.captcha.enabled=false",
        "hparty.job.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DevelopApplicantSelfAddRegressionTest {

    private static final Long ORG = 2L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void committeeCannotAddSelfButCanAddAnotherPerson() throws Exception {
        Long selfPerson = createPerson("self");
        Long otherPerson = createPerson("other");
        String token = login(createBuiltInRoleUser("ORG_COMMITTEE", selfPerson, ORG));
        try {
            JsonNode blocked = addApplicant(selfPerson, token);
            assertThat(blocked.path("code").asInt()).isNotEqualTo(200);
            assertThat(blocked.path("msg").asText()).contains("本人");
            assertThat(applicantCount(selfPerson)).isZero();

            assertThat(addApplicant(otherPerson, token).path("code").asInt()).isEqualTo(200);
            assertThat(applicantCount(otherPerson)).isEqualTo(1);
        } finally {
            logout(token);
        }
    }

    /** 账号未关联人员档案时（personId 为空）本校验不生效，不应误伤 */
    @Test
    void accountWithoutPersonRecordIsNotBlocked() throws Exception {
        Long target = createPerson("no-record-target");
        String token = login(createBuiltInRoleUser("ORG_COMMITTEE", null, ORG));
        try {
            assertThat(addApplicant(target, token).path("code").asInt()).isEqualTo(200);
            assertThat(applicantCount(target)).isEqualTo(1);
        } finally {
            logout(token);
        }
    }

    // ==================== 工具 ====================

    private JsonNode addApplicant(Long personId, String token) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("personId", personId);
        body.put("orgId", ORG);
        MvcResult result = mockMvc.perform(post("/develop/applicant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private int applicantCount(Long personId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dev_applicant WHERE person_id=? AND del_flag=0", Integer.class, personId);
        return count == null ? 0 : count;
    }

    private Long createPerson(String marker) {
        String name = "SelfAdd " + marker + " " + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO party_person (name,org_id,member_status,is_member,status,remark) "
                + "VALUES (?,?,1,0,1,'self-add regression')", name, ORG);
        return jdbc.queryForObject("SELECT person_id FROM party_person WHERE name=? AND del_flag=0", Long.class, name);
    }

    private String createBuiltInRoleUser(String roleKey, Long personId, Long orgId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "selfadd_" + roleKey.toLowerCase() + "_" + suffix;
        jdbc.update("INSERT INTO sys_user (username,password,nick_name,person_id,org_id,status,pwd_update_date,remark) "
                        + "VALUES (?,?,?,?,?,1,NOW(),'self-add regression')",
                username, BCrypt.hashpw("placeholder"), "Self Add Regression", personId, orgId);
        Long userId = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=? AND del_flag=0", Long.class, username);
        Long roleId = jdbc.queryForObject(
                "SELECT role_id FROM sys_role WHERE role_key=? AND is_builtin=1 AND status=1 AND del_flag=0",
                Long.class, roleKey);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", userId, roleId);
        return username;
    }

    private String login(String username) throws Exception {
        String raw = "Sa!" + UUID.randomUUID().toString().replace("-", "") + "7b";
        jdbc.update("UPDATE sys_user SET password=?,pwd_update_date=NOW() WHERE username=?", BCrypt.hashpw(raw), username);
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
