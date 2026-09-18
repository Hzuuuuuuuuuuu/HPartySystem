package com.hparty.bootstrap;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hparty.system.domain.entity.SysUser;
import com.hparty.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "hparty.captcha.enabled=false")
@ActiveProfiles("dev")
@Transactional
class ProductionAdminBootstrapTest {

    @Autowired
    private SysUserMapper userMapper;

    @Test
    void legacyDefaultPasswordWithoutBootstrapSecretFailsClosed() {
        setAdminPassword("123456", LocalDateTime.now());

        ProductionAdminBootstrap bootstrap = new ProductionAdminBootstrap(userMapper, "");

        assertThatThrownBy(bootstrap::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HPARTY_ADMIN_INITIAL_PASSWORD");
    }

    @Test
    void weakBootstrapSecretIsRejected() {
        setAdminPassword("123456", null);

        ProductionAdminBootstrap bootstrap = new ProductionAdminBootstrap(userMapper, "admin123");

        assertThatThrownBy(bootstrap::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不符合密码强度要求");
    }

    @Test
    void strongBootstrapSecretReplacesLegacyDefaultAndStillForcesFirstLoginChange() throws Exception {
        setAdminPassword("123456", LocalDateTime.now());
        String bootstrapSecret = "ProdInit-9x7K42";

        new ProductionAdminBootstrap(userMapper, bootstrapSecret).afterPropertiesSet();

        SysUser admin = loadAdmin();
        assertThat(BCrypt.checkpw("123456", admin.getPassword())).isFalse();
        assertThat(BCrypt.checkpw(bootstrapSecret, admin.getPassword())).isTrue();
        assertThat(admin.getPwdUpdateDate()).isNull();
    }

    @Test
    void alreadyChangedAdminDoesNotRequireBootstrapSecret() throws Exception {
        String changedPassword = "Changed-Admin-9x7K42";
        setAdminPassword(changedPassword, LocalDateTime.now());

        new ProductionAdminBootstrap(userMapper, "").afterPropertiesSet();

        SysUser admin = loadAdmin();
        assertThat(BCrypt.checkpw(changedPassword, admin.getPassword())).isTrue();
        assertThat(admin.getPwdUpdateDate()).isNotNull();
    }

    private void setAdminPassword(String rawPassword, LocalDateTime pwdUpdateDate) {
        SysUser admin = loadAdmin();
        admin.setPassword(BCrypt.hashpw(rawPassword));
        admin.setPwdUpdateDate(pwdUpdateDate);
        userMapper.updateById(admin);
    }

    private SysUser loadAdmin() {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "admin"));
    }
}
