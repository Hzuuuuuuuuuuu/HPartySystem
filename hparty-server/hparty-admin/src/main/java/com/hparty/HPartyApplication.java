package com.hparty;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.net.InetAddress;

/**
 * 智慧党建管理系统 启动类。
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.hparty")
@ConfigurationPropertiesScan("com.hparty")
@EnableTransactionManagement
// 定时任务：党费账单生成、超期扫描、日志清理。任务实现见 com.hparty.job.HPartyScheduledTasks
@EnableScheduling
public class HPartyApplication {

    public static void main(String[] args) throws Exception {
        Environment env = SpringApplication.run(HPartyApplication.class, args).getEnvironment();

        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String host = InetAddress.getLocalHost().getHostAddress();

        log.info("""

                        ----------------------------------------------------------
                          智慧党建管理系统 启动成功
                        ----------------------------------------------------------
                          本地地址:  http://localhost:{}{}
                          外部地址:  http://{}:{}{}
                          接口文档:  http://localhost:{}{}/doc.html
                          当前环境:  {}
                        ----------------------------------------------------------""",
                port, contextPath,
                host, port, contextPath,
                port, contextPath,
                String.join(",", env.getActiveProfiles().length == 0
                        ? new String[]{"default"} : env.getActiveProfiles()));
    }
}
