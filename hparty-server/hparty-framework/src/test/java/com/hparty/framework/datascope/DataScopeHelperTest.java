package com.hparty.framework.datascope;

import com.hparty.common.enums.DataScope;
import com.hparty.framework.mapper.DataScopeMapper;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DataScopeHelperTest {

    private final DataScopeMapper mapper = mock(DataScopeMapper.class);

    DataScopeHelperTest() {
        new OrgPathResolver(mapper);
    }

    @AfterEach
    void resetResolver() {
        new OrgPathResolver(null);
    }

    @Test
    void selfScopePersonAwareAccessOnlyAllowsOwnPerson() {
        LoginUser user = user(DataScope.SELF, 10L, 2L, 21L, "/1/2/");

        withUser(user, () -> {
            assertThat(DataScopeHelper.canAccessData(2L, 21L)).isTrue();
            assertThat(DataScopeHelper.canAccessData(2L, 22L)).isFalse();
            assertThat(DataScopeHelper.canAccessData(3L, 21L)).isTrue();
            assertThat(DataScopeHelper.canAccessData(2L, null)).isFalse();
        });
    }

    @Test
    void selfScopeWithoutPersonFallsBackToCurrentOrg() {
        LoginUser user = user(DataScope.SELF, 10L, 2L, null, "/1/2/");

        withUser(user, () -> {
            assertThat(DataScopeHelper.canAccessData(2L, null)).isTrue();
            assertThat(DataScopeHelper.canAccessData(3L, null)).isFalse();
        });
    }

    @Test
    void customScopeDirectAccessUsesRoleDeptAuthorization() {
        LoginUser user = user(DataScope.CUSTOM, 10L, 2L, 21L, "/1/2/");
        when(mapper.countCustomOrgAccess(10L, 2L)).thenReturn(1);
        when(mapper.countCustomOrgAccess(10L, 3L)).thenReturn(0);

        withUser(user, () -> {
            assertThat(DataScopeHelper.canAccessOrg(2L)).isTrue();
            assertThat(DataScopeHelper.canAccessOrg(3L)).isFalse();
            assertThat(DataScopeHelper.canAccessData(2L, 999L)).isTrue();
            assertThat(DataScopeHelper.canAccessData(3L, 21L)).isFalse();
        });
    }

    @Test
    void currentAndChildScopeKeepsSubtreeSemantics() {
        LoginUser user = user(DataScope.CURRENT_AND_CHILD, 10L, 2L, 21L, "/1/2/");
        when(mapper.selectOrgPath(3L)).thenReturn("/1/2/3/");
        when(mapper.selectOrgPath(4L)).thenReturn("/1/4/");

        withUser(user, () -> {
            assertThat(DataScopeHelper.canAccessOrg(2L)).isTrue();
            assertThat(DataScopeHelper.canAccessOrg(3L)).isTrue();
            assertThat(DataScopeHelper.canAccessOrg(4L)).isFalse();
            assertThat(DataScopeHelper.canAccessData(3L, 999L)).isTrue();
        });
    }

    @Test
    void currentAndAllScopesKeepExistingSemantics() {
        LoginUser current = user(DataScope.CURRENT, 10L, 2L, 21L, "/1/2/");
        withUser(current, () -> {
            assertThat(DataScopeHelper.canAccessOrg(2L)).isTrue();
            assertThat(DataScopeHelper.canAccessOrg(3L)).isFalse();
            assertThat(DataScopeHelper.canAccessData(2L, 999L)).isTrue();
        });

        LoginUser all = user(DataScope.ALL, 10L, 2L, 21L, "/1/2/");
        withUser(all, () -> {
            assertThat(DataScopeHelper.canAccessOrg(999L)).isTrue();
            assertThat(DataScopeHelper.canAccessData(999L, 999L)).isTrue();
        });
    }

    private LoginUser user(DataScope scope, Long userId, Long orgId, Long personId, String orgPath) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setOrgId(orgId);
        user.setPersonId(personId);
        user.setOrgPath(orgPath);
        user.setDataScope(scope.getCode());
        return user;
    }

    private void withUser(LoginUser user, Runnable assertion) {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getLoginUserOrNull).thenReturn(user);
            assertion.run();
        }
    }
}
