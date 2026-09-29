package com.hparty.framework.security;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mockStatic;

class SessionInvalidatorTest {

    private final SessionInvalidator invalidator = new SessionInvalidator();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void invalidatesDistinctNonNullUsersImmediatelyWhenNoTransactionIsActive() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            invalidator.invalidateUsersAfterCommit(Arrays.asList(3L, null, 3L, 7L));

            stp.verify(() -> StpUtil.logout(3L));
            stp.verify(() -> StpUtil.logout(7L));
            stp.verifyNoMoreInteractions();
        }
    }

    @Test
    void invalidateUsersIgnoresNullAndDuplicates() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            invalidator.invalidateUsers(Arrays.asList(null, 5L, 5L));
            invalidator.invalidateUser(null);

            stp.verify(() -> StpUtil.logout(5L));
            stp.verifyNoMoreInteractions();
        }
    }

    @Test
    void waitsUntilCommitBeforeInvalidatingUsers() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            TransactionSynchronizationManager.initSynchronization();

            invalidator.invalidateUsersAfterCommit(List.of(3L, 3L));
            stp.verifyNoInteractions();

            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);
            syncs.forEach(TransactionSynchronization::afterCommit);
            syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

            stp.verify(() -> StpUtil.logout(3L));
            stp.verifyNoMoreInteractions();
        }
    }

    @Test
    void doesNotInvalidateUsersWhenTransactionRollsBack() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            TransactionSynchronizationManager.initSynchronization();

            invalidator.invalidateUsersAfterCommit(List.of(3L));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            stp.verifyNoInteractions();
        }
    }

    @Test
    void emptyOrNullCollectionsDoNothing() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            TransactionSynchronizationManager.initSynchronization();

            invalidator.invalidateUsersAfterCommit(null);
            invalidator.invalidateUsersAfterCommit(List.of());
            invalidator.invalidateUsersAfterCommit(Arrays.asList((Long) null));
            invalidator.invalidateUsers(null);
            invalidator.invalidateUsers(List.of());

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            stp.verifyNoInteractions();
        }
    }

    @Test
    void logoutFailureAfterCommitIsSwallowedAndRemainingUsersStillInvalidated() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.logout(3L)).thenThrow(new IllegalStateException("redis down"));
            TransactionSynchronizationManager.initSynchronization();

            invalidator.invalidateUsersAfterCommit(List.of(3L, 7L));

            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
            stp.verify(() -> StpUtil.logout(3L));
            stp.verify(() -> StpUtil.logout(7L));
        }
    }
}
