package com.workflow.biz.project.embed.demo;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 第三方宿主后端示例，只通过公开 HTTP 契约接入 Flow，不依赖 Flow 内部模块或数据库。
 * 复制到第三方应用后显式启用 partner-embed-demo；Flow 常规运行不注册本包的示例 Bean。
 */
@Configuration
@Profile("partner-embed-demo")
@EnableConfigurationProperties(EmbedDemoProperties.class)
public class EmbedDemoConfiguration {
    @Bean("partnerEmbedDemoClock")
    Clock clock() {
        return Clock.systemUTC();
    }
}
