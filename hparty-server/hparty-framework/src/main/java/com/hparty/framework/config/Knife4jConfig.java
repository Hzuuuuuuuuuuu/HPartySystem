package com.hparty.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置，访问 http://localhost:8080/doc.html
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI hpartyOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("智慧党建管理系统 API")
                .description("三会一课、发展党员（5 阶段 25 步）、组织生活会等模块接口")
                .version("1.0.0")
                .contact(new Contact().name("HPartySystem")));
    }
}
