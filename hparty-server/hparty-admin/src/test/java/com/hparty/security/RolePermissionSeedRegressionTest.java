package com.hparty.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "hparty.captcha.enabled=false")
@ActiveProfiles("dev")
class RolePermissionSeedRegressionTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void partySecretaryHasBusinessPermissionsButNotSystemAdministrationWrites() {
        assertThat(hasPermission("develop:applicant:handle")).isTrue();
        assertThat(hasPermission("develop:applicant:detail")).isTrue();
        assertThat(hasPermission("partyday:add")).isTrue();
        assertThat(hasPermission("system:dept:list")).isTrue();

        assertThat(hasPermission("system:user:add")).isFalse();
        assertThat(hasPermission("system:role:add")).isFalse();
        assertThat(hasPermission("system:menu:add")).isFalse();
        assertThat(hasPermission("system:dict:add")).isFalse();
        assertThat(hasPermission("system:dept:add")).isFalse();
    }

    @Test
    void roleMenuPairsRemainUnique() {
        Integer duplicates = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT rm.role_id, rm.menu_id
                    FROM sys_role_menu rm
                    JOIN sys_role r ON r.role_id = rm.role_id
                    WHERE r.role_key = 'PARTY_SECRETARY'
                      AND r.is_builtin = 1
                    GROUP BY rm.role_id, rm.menu_id
                    HAVING COUNT(*) > 1
                ) duplicated
                """, Integer.class);
        assertThat(duplicates).isZero();
    }

    @Test
    void applicantCanReadOwnDevelopmentDetailAndSelfSubmitWithoutGeneralHandlePermission() {
        assertThat(hasPermission("APPLICANT", "develop:applicant:list")).isTrue();
        assertThat(hasPermission("APPLICANT", "develop:applicant:detail")).isTrue();
        assertThat(hasPermission("APPLICANT", "develop:applicant:self-submit")).isTrue();
        assertThat(hasPermission("APPLICANT", "develop:applicant:handle")).isFalse();
        assertThat(hasPermission("APPLICANT", "develop:material:submit")).isFalse();
    }

    @Test
    void developmentManagersReceiveMaterialSubmissionPermission() {
        assertThat(hasPermission("PARTY_SECRETARY", "develop:material:submit")).isTrue();
        assertThat(hasPermission("BRANCH_SECRETARY", "develop:material:submit")).isTrue();
        assertThat(hasPermission("BRANCH_DEPUTY", "develop:material:submit")).isTrue();
        assertThat(hasPermission("ORG_COMMITTEE", "develop:material:submit")).isTrue();
    }

    private boolean hasPermission(String permission) {
        return hasPermission("PARTY_SECRETARY", permission);
    }

    private boolean hasPermission(String roleKey, String permission) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role r
                JOIN sys_role_menu rm ON rm.role_id = r.role_id
                JOIN sys_menu m ON m.menu_id = rm.menu_id
                WHERE r.role_key = ?
                  AND r.is_builtin = 1
                  AND r.status = 1
                  AND r.del_flag = 0
                  AND m.status = 1
                  AND m.perms = ?
                """, Integer.class, roleKey, permission);
        return count != null && count > 0;
    }
}
