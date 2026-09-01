package com.workflow.config;

import com.workflow.admin.auth.infrastructure.AuthInterceptor;
import com.workflow.admin.authorization.infrastructure.EndpointAuthorizationInterceptor;
import com.workflow.embed.security.EmbedDelegatedRuntimeAuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 * 
 * @description 配置跨域访问权限和拦截器
 * @author Workflow Team
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    /**
     * Embed Runtime 由专属 Spring Security 链建立身份，不能再进入普通用户拦截器。
     * 管理端 /api/embed-management/** 故意不在此列表中，仍沿用普通 Flow 登录态。
     */
    static final String[] EMBED_RUNTIME_PATH_PATTERNS = {
            "/api/embed/v1/launches/*/exchange",
            "/api/embed/v1/runtime/**",
            "/api/embed/v1/session",
            "/api/embed/v1/session/**"
    };

    private final AuthInterceptor authInterceptor;
    private final EndpointAuthorizationInterceptor
            endpointAuthorizationInterceptor;
    private final ObjectProvider<EmbedDelegatedRuntimeAuthorizationInterceptor>
            embedDelegatedRuntimeAuthorizationInterceptorProvider;
    private final CorsProperties corsProperties;

    /**
     * 配置CORS跨域规则和拦截器
     * 
     * @return WebMvcConfigurer 配置对象
     */
    @Bean
    public WebMvcConfigurer webConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(
                                corsProperties.getAllowedOrigins()
                                        .toArray(String[]::new))
                        .allowedMethods(
                                corsProperties.getAllowedMethods()
                                        .toArray(String[]::new))
                        .allowedHeaders(
                                corsProperties.getAllowedHeaders()
                                        .toArray(String[]::new))
                        .allowCredentials(
                                corsProperties.isAllowCredentials())
                        .maxAge(corsProperties.getMaxAge().toSeconds());
            }
            
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // Authentication always runs first; only login is intentionally anonymous.
                registry.addInterceptor(authInterceptor)
                        .addPathPatterns("/api/**")
                        .excludePathPatterns(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/open/**")
                        .excludePathPatterns(
                                EMBED_RUNTIME_PATH_PATTERNS);
                // Embed iframe 是隔离 Origin 上的原生 Flow 客户端。opaque
                // Session 只负责建立 mapped UserContext，不再维护一份会随
                // 新组件漂移的逐端点 Embed 白名单；下方平台统一权限与
                // DataScope 仍按映射用户原样执行。
                EmbedDelegatedRuntimeAuthorizationInterceptor delegated =
                        embedDelegatedRuntimeAuthorizationInterceptorProvider
                                .getIfAvailable();
                if (delegated != null) {
                    // 只有显式声明 target binding 的端点额外校验固定
                    // Release；未声明的普通数据面端点不在此维护白名单。
                    registry.addInterceptor(delegated)
                            .addPathPatterns("/api/**")
                            .excludePathPatterns(
                                    EMBED_RUNTIME_PATH_PATTERNS);
                }
                // Every mapped API must then declare an explicit access policy.
                registry.addInterceptor(endpointAuthorizationInterceptor)
                        .addPathPatterns("/api/**")
                        .excludePathPatterns("/api/open/**")
                        .excludePathPatterns(
                                EMBED_RUNTIME_PATH_PATTERNS);
            }
        };
    }
}
