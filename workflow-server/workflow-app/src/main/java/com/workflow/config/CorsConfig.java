package com.workflow.config;

import org.springframework.beans.factory.annotation.Autowired;
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
public class CorsConfig {

    @Autowired
    private AuthInterceptor authInterceptor;

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
                // 对所有API路径应用CORS配置
                registry.addMapping("/**")
                        // 允许所有来源（生产环境应限制具体域名）
                        .allowedOrigins("*")
                        // 允许的HTTP方法
                        .allowedMethods("GET", "POST", "OPTIONS")
                        // 允许所有请求头
                        .allowedHeaders("*")
                        // 预检请求缓存时间（秒）
                        .maxAge(3600);
            }
            
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // 注册认证拦截器，排除登录相关接口
                registry.addInterceptor(authInterceptor)
                        .addPathPatterns("/api/**")
                        .excludePathPatterns("/api/auth/login", "/api/auth/logout");
            }
        };
    }
}
