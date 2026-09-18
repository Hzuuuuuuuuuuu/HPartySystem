package com.hparty.framework.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 鉴权配置。
 * <p>全局登录校验，白名单内的接口无需登录。细粒度权限由
 * {@code @SaCheckPermission} / {@code @SaCheckRole} 注解控制。</p>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** 免登录白名单 */
    public static final String[] WHITE_LIST = {
            "/auth/login",
            "/auth/captcha",
            "/auth/logout",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/favicon.ico",
            "/error"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(WHITE_LIST);
    }
}
