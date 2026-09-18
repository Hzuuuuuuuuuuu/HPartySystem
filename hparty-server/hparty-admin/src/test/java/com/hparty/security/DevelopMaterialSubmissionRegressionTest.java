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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "hparty.captcha.enabled=false",
        "hparty.job.enabled=false",
        "hparty.file.local-path=target/material-regression-uploads"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DevelopMaterialSubmissionRegressionTest {

    private static final Path TEST_UPLOAD_ROOT = Path.of("target/material-regression-uploads");

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
    void applicantCanUploadOwnCurrentStepMaterialButNotOthersOrFutureSteps() throws Exception {
        Long ownPerson = createPerson(2L, "app-own");
        Long otherPerson = createPerson(2L, "app-other");
        Long ownApplicant = createApplicant(ownPerson, 2L, "STEP_01");
        Long otherApplicant = createApplicant(otherPerson, 2L, "STEP_01");
        String token = login(createBuiltInRoleUser("APPLICANT", ownPerson, 2L));

        Long applyBook = templateId("1-1");
        Long futureVolunteerBook = templateId("4-3");
        Long branchTalk = templateId("1-2");
        try {
            JsonNode own = upload(ownApplicant, applyBook, token, "apply.txt");
            assertThat(own.path("code").asInt()).isEqualTo(200);
            assertThat(activeMaterialCount(ownApplicant, "1-1")).isEqualTo(1);

            assertThat(upload(otherApplicant, applyBook, token, "other.txt").path("code").asInt()).isEqualTo(403);
            assertThat(upload(ownApplicant, futureVolunteerBook, token, "future.txt").path("code").asInt()).isEqualTo(403);
            assertThat(upload(ownApplicant, branchTalk, token, "branch.txt").path("code").asInt()).isEqualTo(403);
        } finally {
            logout(token);
        }
    }

    @Test
    void selfSubmitRequiresOwnRequiredMaterialAndAdvancesStep01Only() throws Exception {
        Long personId = createPerson(2L, "self-submit");
        Long applicantId = createApplicant(personId, 2L, "STEP_01");
        String token = login(createBuiltInRoleUser("APPLICANT", personId, 2L));
        try {
            JsonNode blocked = postJson("/develop/applicant/" + applicantId + "/self-submit", token);
            assertThat(blocked.path("code").asInt()).isNotEqualTo(200);

            assertThat(upload(applicantId, templateId("1-1"), token, "apply.txt").path("code").asInt()).isEqualTo(200);
            JsonNode submitted = postJson("/develop/applicant/" + applicantId + "/self-submit", token);
            assertThat(submitted.path("code").asInt()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT current_step FROM dev_applicant WHERE applicant_id=?", String.class, applicantId))
                    .isEqualTo("STEP_02");

            assertThat(upload(applicantId, templateId("1-2-1"), token, "report.txt").path("code").asInt()).isEqualTo(200);
            JsonNode wrongStep = postJson("/develop/applicant/" + applicantId + "/self-submit", token);
            assertThat(wrongStep.path("code").asInt()).isEqualTo(403);
        } finally {
            logout(token);
        }
    }

    @Test
    void branchMaterialIsScopedByRoleAndOrganization() throws Exception {
        Long sameOrgPerson = createPerson(2L, "branch-same");
        Long otherOrgPerson = createPerson(3L, "branch-other");
        Long sameOrgApplicant = createApplicant(sameOrgPerson, 2L, "STEP_02");
        Long otherOrgApplicant = createApplicant(otherOrgPerson, 3L, "STEP_02");
        String token = login(createBuiltInRoleUser("BRANCH_SECRETARY", null, 2L));
        try {
            assertThat(upload(sameOrgApplicant, templateId("1-2"), token, "talk.txt").path("code").asInt()).isEqualTo(200);
            assertThat(upload(otherOrgApplicant, templateId("1-2"), token, "cross.txt").path("code").asInt()).isEqualTo(403);
            assertThat(upload(sameOrgApplicant, templateId("1-2-1"), token, "applicant-only.txt").path("code").asInt()).isEqualTo(403);
        } finally {
            logout(token);
        }
    }

    @Test
    void assignedTrainerCanUploadTrainerMaterialButUnassignedPersonCannot() throws Exception {
        Long applicantPerson = createPerson(2L, "trainer-applicant");
        Long assignedTrainer = createPerson(2L, "trainer-assigned");
        Long stranger = createPerson(2L, "trainer-stranger");
        Long applicantId = createApplicant(applicantPerson, 2L, "STEP_06");
        jdbc.update("UPDATE dev_applicant SET trainer_ids=? WHERE applicant_id=?", String.valueOf(assignedTrainer), applicantId);

        String assignedToken = login(createUserWithoutBusinessRole(assignedTrainer, 2L));
        String strangerToken = login(createUserWithoutBusinessRole(stranger, 2L));
        try {
            assertThat(upload(applicantId, templateId("2-6"), assignedToken, "inspect.txt").path("code").asInt()).isEqualTo(200);
            String fileUrl = jdbc.queryForObject(
                    "SELECT file_url FROM dev_material WHERE applicant_id=? AND template_code='2-6' AND del_flag=0",
                    String.class, applicantId);
            String previewPath = fileUrl.startsWith("/api/") ? fileUrl.substring(4) : fileUrl;
            MvcResult trainerPreview = mockMvc.perform(get(previewPath)
                            .header("Authorization", "Bearer " + assignedToken))
                    .andReturn();
            assertThat(trainerPreview.getResponse().getStatus()).isEqualTo(200);
            assertThat(trainerPreview.getResponse().getContentAsString()).contains("content-inspect.txt");

            assertThat(upload(applicantId, templateId("2-6"), strangerToken, "nope.txt").path("code").asInt()).isEqualTo(403);
        } finally {
            logout(assignedToken);
            logout(strangerToken);
        }
    }

    @Test
    void parentOrganizationMaterialRequiresCommitteeIdentityAndDataScope() throws Exception {
        Long personId = createPerson(2L, "parent-org");
        Long applicantId = createApplicant(personId, 2L, "STEP_10");
        String committeeToken = login(createBuiltInRoleUser("PARTY_SECRETARY", null, 1L));
        String branchToken = login(createBuiltInRoleUser("BRANCH_SECRETARY", null, 2L));
        try {
            assertThat(upload(applicantId, templateId("3-8"), committeeToken, "reply.txt").path("code").asInt())
                    .isEqualTo(200);
            assertThat(upload(applicantId, templateId("3-8"), branchToken, "branch-reply.txt").path("code").asInt())
                    .isEqualTo(403);
        } finally {
            logout(committeeToken);
            logout(branchToken);
        }
    }

    @Test
    void periodicApplicantMaterialKeepsMultipleActiveSubmissions() throws Exception {
        Long personId = createPerson(2L, "periodic");
        Long applicantId = createApplicant(personId, 2L, "STEP_06");
        String token = login(createBuiltInRoleUser("APPLICANT", personId, 2L));
        try {
            assertThat(upload(applicantId, templateId("2-5"), token, "thought-1.txt").path("code").asInt())
                    .isEqualTo(200);
            assertThat(upload(applicantId, templateId("2-5"), token, "thought-2.txt").path("code").asInt())
                    .isEqualTo(200);
            assertThat(activeMaterialCount(applicantId, "2-5")).isEqualTo(2);
        } finally {
            logout(token);
        }
    }

    @Test
    void materialDeleteIsLimitedToAuthorizedApplicantResource() throws Exception {
        Long ownerPerson = createPerson(2L, "delete-owner");
        Long strangerPerson = createPerson(2L, "delete-stranger");
        Long ownerApplicant = createApplicant(ownerPerson, 2L, "STEP_01");
        Long strangerApplicant = createApplicant(strangerPerson, 2L, "STEP_01");
        String ownerToken = login(createBuiltInRoleUser("APPLICANT", ownerPerson, 2L));
        String strangerToken = login(createBuiltInRoleUser("APPLICANT", strangerPerson, 2L));
        try {
            JsonNode uploaded = upload(ownerApplicant, templateId("1-1"), ownerToken, "delete-me.txt");
            assertThat(uploaded.path("code").asInt()).isEqualTo(200);
            Long materialId = jdbc.queryForObject(
                    "SELECT material_id FROM dev_material WHERE applicant_id=? AND template_code='1-1' AND del_flag=0",
                    Long.class, ownerApplicant);

            assertThat(deleteJson("/develop/applicant/" + strangerApplicant + "/materials/" + materialId, strangerToken)
                    .path("code").asInt()).isNotEqualTo(200);
            assertThat(activeMaterialCount(ownerApplicant, "1-1")).isEqualTo(1);

            assertThat(deleteJson("/develop/applicant/" + ownerApplicant + "/materials/" + materialId, ownerToken)
                    .path("code").asInt()).isEqualTo(200);
            assertThat(activeMaterialCount(ownerApplicant, "1-1")).isZero();
        } finally {
            logout(ownerToken);
            logout(strangerToken);
        }
    }

    @Test
    void sameOrgApplicantCannotPreviewAnotherApplicantsMaterialByLeakedUrl() throws Exception {
        Long ownerPerson = createPerson(2L, "preview-owner");
        Long strangerPerson = createPerson(2L, "preview-stranger");
        Long ownerApplicant = createApplicant(ownerPerson, 2L, "STEP_01");
        String ownerToken = login(createBuiltInRoleUser("APPLICANT", ownerPerson, 2L));
        String strangerToken = login(createBuiltInRoleUser("APPLICANT", strangerPerson, 2L));
        try {
            assertThat(upload(ownerApplicant, templateId("1-1"), ownerToken, "private-apply.txt")
                    .path("code").asInt()).isEqualTo(200);
            String fileUrl = jdbc.queryForObject(
                    "SELECT file_url FROM dev_material WHERE applicant_id=? AND template_code='1-1' AND del_flag=0",
                    String.class, ownerApplicant);

            String previewPath = fileUrl.startsWith("/api/") ? fileUrl.substring(4) : fileUrl;
            MvcResult leakedPreview = mockMvc.perform(get(previewPath)
                            .header("Authorization", "Bearer " + strangerToken))
                    .andReturn();
            assertThat(leakedPreview.getResponse().getContentType()).contains("application/json");
            JsonNode denied = objectMapper.readTree(leakedPreview.getResponse().getContentAsByteArray());
            assertThat(denied.path("code").asInt()).isEqualTo(403);
        } finally {
            logout(ownerToken);
            logout(strangerToken);
        }
    }

    @Test
    void devMaterialBackingFileCannotBeDeletedThroughGenericFileEndpoint() throws Exception {
        Long ownerPerson = createPerson(2L, "generic-delete-owner");
        Long ownerApplicant = createApplicant(ownerPerson, 2L, "STEP_01");
        String ownerToken = login(createBuiltInRoleUser("APPLICANT", ownerPerson, 2L));
        try {
            assertThat(upload(ownerApplicant, templateId("1-1"), ownerToken, "keep-linked.txt")
                    .path("code").asInt()).isEqualTo(200);
            Long materialId = jdbc.queryForObject(
                    "SELECT material_id FROM dev_material WHERE applicant_id=? AND template_code='1-1' AND del_flag=0",
                    Long.class, ownerApplicant);
            Long fileId = jdbc.queryForObject(
                    "SELECT file_id FROM dev_material WHERE material_id=? AND del_flag=0",
                    Long.class, materialId);

            JsonNode denied = deleteJson("/file/" + fileId, ownerToken);
            assertThat(denied.path("code").asInt()).isEqualTo(403);
            assertThat(activeMaterialCount(ownerApplicant, "1-1")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_file WHERE file_id=? AND del_flag=0",
                    Integer.class, fileId)).isEqualTo(1);
        } finally {
            logout(ownerToken);
        }
    }

    @Test
    void singleMaterialReplacementLeavesOneActiveRecordAndRosterIsRejected() throws Exception {
        Long personId = createPerson(2L, "replace");
        Long applicantId = createApplicant(personId, 2L, "STEP_01");
        String token = login(createBuiltInRoleUser("APPLICANT", personId, 2L));
        try {
            assertThat(upload(applicantId, templateId("1-1"), token, "v1.txt").path("code").asInt()).isEqualTo(200);
            assertThat(upload(applicantId, templateId("1-1"), token, "v2.txt").path("code").asInt()).isEqualTo(200);
            assertThat(activeMaterialCount(applicantId, "1-1")).isEqualTo(1);
            assertThat(upload(applicantId, templateId("1-4"), token, "roster.txt").path("code").asInt()).isEqualTo(403);
        } finally {
            logout(token);
        }
    }

    @Test
    void timelineReturnsServerCalculatedMaterialCapabilities() throws Exception {
        Long personId = createPerson(2L, "capability");
        Long applicantId = createApplicant(personId, 2L, "STEP_01");
        String token = login(createBuiltInRoleUser("APPLICANT", personId, 2L));
        try {
            JsonNode timeline = getJson("/develop/applicant/" + applicantId + "/timeline", token);
            assertThat(timeline.path("code").asInt()).isEqualTo(200);
            JsonNode step01 = findStep(timeline.path("data"), "STEP_01");
            JsonNode applyBook = findTemplate(step01.path("materialTemplates"), "1-1");
            assertThat(applyBook.path("canUpload").asBoolean()).isTrue();
            assertThat(step01.path("canSelfSubmit").asBoolean()).isFalse();
            assertThat(step01.path("selfSubmitBlockedReason").asText()).contains("入党申请书");

            JsonNode step14 = findStep(timeline.path("data"), "STEP_14");
            JsonNode volunteerBook = findTemplate(step14.path("materialTemplates"), "4-3");
            assertThat(volunteerBook.path("canUpload").asBoolean()).isFalse();
        } finally {
            logout(token);
        }
    }

    private JsonNode findStep(JsonNode data, String stepCode) {
        for (JsonNode stage : data.path("stages")) {
            for (JsonNode step : stage.path("steps")) {
                if (stepCode.equals(step.path("stepCode").asText())) return step;
            }
        }
        throw new AssertionError("step not found: " + stepCode);
    }

    private JsonNode findTemplate(JsonNode templates, String templateCode) {
        for (JsonNode template : templates) {
            if (templateCode.equals(template.path("templateCode").asText())) return template;
        }
        throw new AssertionError("template not found: " + templateCode);
    }

    private Long createPerson(Long orgId, String marker) {
        String name = "Material " + marker + " " + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO party_person (name,org_id,member_status,is_member,status,remark) VALUES (?,?,1,0,1,'material regression')",
                name, orgId);
        return jdbc.queryForObject("SELECT person_id FROM party_person WHERE name=? AND del_flag=0", Long.class, name);
    }

    private Long createApplicant(Long personId, Long orgId, String stepCode) {
        String stageCode = jdbc.queryForObject("SELECT stage_code FROM dev_step WHERE step_code=?", String.class, stepCode);
        Integer progress = jdbc.queryForObject("SELECT ROUND(step_order * 100.0 / 25) FROM dev_step WHERE step_code=?", Integer.class, stepCode);
        jdbc.update("INSERT INTO dev_applicant (person_id,org_id,current_stage,current_step,status,progress,apply_date,remark) VALUES (?,?,?,?,1,?,CURDATE(),'material regression')",
                personId, orgId, stageCode, stepCode, progress);
        Long applicantId = jdbc.queryForObject("SELECT applicant_id FROM dev_applicant WHERE person_id=? AND del_flag=0 ORDER BY applicant_id DESC LIMIT 1", Long.class, personId);
        jdbc.update("INSERT INTO dev_step_record (applicant_id,person_id,org_id,step_code,stage_code,status,is_overdue) VALUES (?,?,?,?,?,2,0)",
                applicantId, personId, orgId, stepCode, stageCode);
        return applicantId;
    }

    private Long templateId(String code) {
        return jdbc.queryForObject("SELECT template_id FROM dev_material_template WHERE template_code=?", Long.class, code);
    }

    private int activeMaterialCount(Long applicantId, String templateCode) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dev_material WHERE applicant_id=? AND template_code=? AND del_flag=0",
                Integer.class, applicantId, templateCode);
        return count == null ? 0 : count;
    }

    private String createBuiltInRoleUser(String roleKey, Long personId, Long orgId) {
        String username = createRawUser(personId, orgId, "material_" + roleKey.toLowerCase());
        Long userId = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=? AND del_flag=0", Long.class, username);
        Long roleId = jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_key=? AND is_builtin=1 AND status=1 AND del_flag=0", Long.class, roleKey);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", userId, roleId);
        return username;
    }

    private String createUserWithoutBusinessRole(Long personId, Long orgId) {
        return createRawUser(personId, orgId, "material_relation");
    }

    private String createRawUser(Long personId, Long orgId, String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = prefix + "_" + suffix;
        jdbc.update("INSERT INTO sys_user (username,password,nick_name,person_id,org_id,status,pwd_update_date,remark) VALUES (?,?,?,?,?,1,NOW(),'material regression')",
                username, BCrypt.hashpw("placeholder"), "Material Regression", personId, orgId);
        return username;
    }

    private String login(String username) throws Exception {
        String raw = "Mt!" + UUID.randomUUID().toString().replace("-", "") + "9a";
        jdbc.update("UPDATE sys_user SET password=?,pwd_update_date=NOW() WHERE username=?", BCrypt.hashpw(raw), username);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("username", username, "password", raw))))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.path("code").asInt()).isEqualTo(200);
        return json.path("data").path("token").asText();
    }

    private JsonNode upload(Long applicantId, Long templateId, String token, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", ("content-" + fileName).getBytes());
        MvcResult result = mockMvc.perform(multipart("/develop/applicant/{applicantId}/materials/{templateId}", applicantId, templateId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return parse(result);
    }

    private JsonNode postJson(String path, String token) throws Exception {
        return parse(mockMvc.perform(post(path).header("Authorization", "Bearer " + token)).andReturn());
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return parse(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn());
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

    private void logout(String token) throws Exception {
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andReturn();
    }
}
