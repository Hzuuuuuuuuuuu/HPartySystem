package com.hparty.framework.security;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 集中式会话失效：用户、角色、菜单权限变更后注销受影响用户的全部 Sa-Token 会话。
 * <p>
 * 业务 Service 不直接调用 {@link StpUtil}，统一走本组件；
 * 在事务中调用 {@link #invalidateUsersAfterCommit} 时，注销推迟到事务成功提交之后，回滚则不注销。
 */
@Slf4j
@Component
public class SessionInvalidator {

    /** 立即注销单个用户的全部会话，null 忽略 */
    public void invalidateUser(Long userId) {
        if (userId != null) {
            StpUtil.logout(userId);
        }
    }

    /** 立即注销多个用户的全部会话，忽略 null 与重复 ID */
    public void invalidateUsers(Collection<Long> userIds) {
        normalize(userIds).forEach(this::invalidateUser);
    }

    /**
     * 事务提交后注销；当前无事务同步时立即注销。
     * 提交后的注销失败（如 Redis 不可用）只记录日志，不影响已成功提交的业务事务。
     */
    public void invalidateUsersAfterCommit(Collection<Long> userIds) {
        List<Long> normalized = normalize(userIds);
        if (normalized.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidateUsers(normalized);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long userId : normalized) {
                    try {
                        invalidateUser(userId);
                    } catch (Exception e) {
                        log.error("权限变更后注销用户会话失败: userId={}", userId, e);
                    }
                }
            }
        });
    }

    private static List<Long> normalize(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}
