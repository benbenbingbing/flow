package com.workflow.embed.infrastructure.config;

import com.workflow.embed.infrastructure.web.EmbedSessionAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 禁止 Servlet 容器自动注册认证过滤器，确保它只在专用的 Spring Security Chain 中执行一次。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedFilterRegistrationConfiguration {

    /**
     * 停用嵌入式过滤{@code auto}{@code registration}；结果供调用方的后续步骤使用。
     *
     * @param filter 过滤，供本方法停用嵌入式过滤{@code auto}{@code registration}时使用
     * @return 停用后的嵌入式过滤{@code auto}{@code registration}结果，供调用方继续处理
     */
    @Bean
    FilterRegistrationBean<EmbedSessionAuthenticationFilter> disableEmbedFilterAutoRegistration(
            EmbedSessionAuthenticationFilter filter) {
        FilterRegistrationBean<EmbedSessionAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
