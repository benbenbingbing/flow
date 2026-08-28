package com.workflow.embed.config;

import com.workflow.embed.security.EmbedSessionAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 禁止 Servlet 容器自动注册认证过滤器，确保它只在专用的 Spring Security Chain 中执行一次。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedFilterRegistrationConfiguration {

    @Bean
    FilterRegistrationBean<EmbedSessionAuthenticationFilter> disableEmbedFilterAutoRegistration(
            EmbedSessionAuthenticationFilter filter) {
        FilterRegistrationBean<EmbedSessionAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
