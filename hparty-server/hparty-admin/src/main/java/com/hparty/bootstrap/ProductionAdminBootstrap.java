package com.hparty.bootstrap;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hparty.common.util.PasswordPolicy;
import com.hparty.system.domain.entity.SysUser;
import com.hparty.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 生产环境管理员初始密码安全闸门。
 *
 * <p>V2 是已经发布的 Flyway 迁移，不能再修改；历史空库初始化会得到 admin/123456。
 * 因此生产启动时只要检测到 admin 仍使用这个遗留口令，就必须由部署环境提供一次性的
 * {@code HPARTY_ADMIN_INITIAL_PASSWORD}。没有提供、或提供的口令不满足密码策略时直接阻止
 * 应用启动，避免服务带着公开默认口令上线。</p>
 *
 * <p>替换后故意把 {@code pwd_update_date} 置空：这个环境变量只用于引导首次安全登录，
 * admin 登录后仍会被现有的首次改密机制要求设置个人密码。若 admin 已经改过密码，则本
 * 组件不会要求环境变量，也不会覆盖现有密码。</p>
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionAdminBootstrap implements InitializingBean {

    private static final String ADMIN_USERNAME = "admin";
    private static final String LEGACY_DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final String initialPassword;

    public ProductionAdminBootstrap(
            SysUserMapper userMapper,
            @Value("${hparty.bootstrap.admin.initial-password:}") String initialPassword) {
        this.userMapper = userMapper;
        this.initialPassword = initialPassword;
    }

    @Override
    public void afterPropertiesSet() {
        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, ADMIN_USERNAME));
        if (admin == null || !StringUtils.hasText(admin.getPassword())) {
            return;
        }
        if (!BCrypt.checkpw(LEGACY_DEFAULT_PASSWORD, admin.getPassword())) {
            return;
        }

        String candidate = initialPassword == null ? "" : initialPassword.trim();
        if (!StringUtils.hasText(candidate)) {
            throw new IllegalStateException(
                    "检测到生产环境 admin 仍使用遗留默认密码。请设置环境变量 HPARTY_ADMIN_INITIAL_PASSWORD 后重新启动。");
        }

        String invalidReason = PasswordPolicy.validate(candidate, ADMIN_USERNAME);
        if (invalidReason != null) {
            throw new IllegalStateException(
                    "HPARTY_ADMIN_INITIAL_PASSWORD 不符合密码强度要求：" + invalidReason);
        }

        int updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUserId, admin.getUserId())
                .eq(SysUser::getPassword, admin.getPassword())
                .set(SysUser::getPassword, BCrypt.hashpw(candidate))
                .set(SysUser::getPwdUpdateDate, null));
        if (updated != 1) {
            throw new IllegalStateException("生产管理员初始密码替换失败，请检查数据库状态后重新启动。");
        }

        log.warn("检测到 admin 遗留默认密码，已使用生产引导密码完成替换；首次登录仍将强制修改密码。");
    }
}
