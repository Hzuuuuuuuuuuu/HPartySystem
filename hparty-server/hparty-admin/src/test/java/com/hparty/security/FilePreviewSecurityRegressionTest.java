package com.hparty.security;

import cn.hutool.crypto.digest.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hparty.HPartyApplication;
import com.hparty.framework.file.FileStorageProperties;
import com.hparty.system.domain.dto.LoginDTO;
import com.hparty.system.domain.vo.LoginVO;
import com.hparty.system.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HPartyApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "hparty.captcha.enabled=false",
        "hparty.job.enabled=false"
})
@Transactional
class FilePreviewSecurityRegressionTest {

    private static final byte[] FILE_BYTES = "preview-security-regression".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorageProperties fileProperties;

    private Path managedFile;
    private Path orphanFile;
    private String previewPath;
    private String orphanPreviewPath;
    private String username;
    private String password;
    private Long roleId;
    private Long userId;
    private String currentToken;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String bizType = "security_test";
        String date = "20260917";
        String fileName = "preview_" + suffix + ".txt";
        String orphanName = "orphan_" + suffix + ".txt";

        Path dir = Path.of(fileProperties.getLocalPath()).resolve(bizType).resolve(date);
        Files.createDirectories(dir);
        managedFile = dir.resolve(fileName);
        orphanFile = dir.resolve(orphanName);
        Files.write(managedFile, FILE_BYTES);
        Files.write(orphanFile, "orphan".getBytes(StandardCharsets.UTF_8));

        String relativePath = bizType + "/" + date + "/" + fileName;
        previewPath = "/file/preview/" + relativePath;
        orphanPreviewPath = "/file/preview/" + bizType + "/" + date + "/" + orphanName;

        jdbc.update("""
                INSERT INTO sys_file
                    (file_name, file_path, file_url, file_suffix, file_size, content_type,
                     storage_type, biz_type, biz_id, org_id, upload_by, del_flag, create_time)
                VALUES (?, ?, ?, 'txt', ?, 'text/plain', 'local', ?, 1, 2, 1, 0, NOW())
                """,
                "security.txt", relativePath, "/api" + previewPath,
                FILE_BYTES.length, bizType);
        username = "preview_" + suffix;
        password = "Preview@" + suffix + "9a";
        jdbc.update("""
                INSERT INTO sys_role
                    (role_name, role_key, role_sort, data_scope, status, is_builtin, del_flag, create_time)
                VALUES (?, ?, 99, 2, 1, 0, 0, NOW())
                """, "File Preview Test", "FILE_PREVIEW_" + suffix);
        roleId = jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_key = ?", Long.class, "FILE_PREVIEW_" + suffix);

        jdbc.update("""
                INSERT INTO sys_user
                    (username, password, nick_name, org_id, status, del_flag, pwd_update_date, create_time)
                VALUES (?, ?, 'File Preview Test', 2, 1, 0, NOW(), NOW())
                """, username, BCrypt.hashpw(password));
        userId = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username = ?", Long.class, username);
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (currentToken != null) {
            mockMvc.perform(post("/auth/logout")
                    .header("Authorization", "Bearer " + currentToken)).andReturn();
        }
        Files.deleteIfExists(managedFile);
        Files.deleteIfExists(orphanFile);
    }

    @Test
    void anonymousPreviewIsRejected() throws Exception {
        assertBusinessError(mockMvc.perform(get(previewPath)).andReturn());
    }

    @Test
    void authorizedUserCanPreviewFile() throws Exception {
        String token = login();

        mockMvc.perform(get(previewPath).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes(FILE_BYTES))
                .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }

    @Test
    void userOutsideFileOrgCannotPreview() throws Exception {
        jdbc.update("UPDATE sys_role SET data_scope = 2 WHERE role_id = ?", roleId);
        jdbc.update("UPDATE sys_user SET org_id = 3 WHERE user_id = ?", userId);
        String token = login();

        assertBusinessError(mockMvc.perform(get(previewPath)
                .header("Authorization", "Bearer " + token)).andReturn());
    }

    @Test
    void diskFileWithoutMetadataCannotBePreviewed() throws Exception {
        String token = login();

        assertBusinessError(mockMvc.perform(get(orphanPreviewPath)
                .header("Authorization", "Bearer " + token)).andReturn());
    }

    private String login() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        LoginVO login = authService.login(dto, new MockHttpServletRequest());
        assertThat(login.getToken()).isNotBlank();
        currentToken = login.getToken();
        return currentToken;
    }

    private void assertBusinessError(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).contains("application/json");
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.path("code").asInt()).isNotEqualTo(200);
    }
}
