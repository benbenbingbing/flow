package com.workflow.config;

import com.workflow.admin.auth.infrastructure.AuthInterceptor;
import com.workflow.admin.authorization.infrastructure.EndpointAuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
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

    private final AuthInterceptor authInterceptor;
    private final EndpointAuthorizationInterceptor
            endpointAuthorizationInterceptor;
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
                        .maxAge(corsProperties.getMaxAge().toSeconds());
            }
            
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // Authentication always runs first; only login is intentionally anonymous.
                registry.addInterceptor(authInterceptor)
                        .addPathPatterns("/api/**")
                        .excludePathPatterns("/api/auth/login");
                // Every mapped API must then declare an explicit access policy.
                registry.addInterceptor(endpointAuthorizationInterceptor)
                        .addPathPatterns("/api/**");
            }
        };
    }
}
