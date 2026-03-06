package com.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域资源共享(CORS)配置类
 * 
 * @description 配置前端跨域访问权限，允许来自不同域的请求
 * @author Workflow Team
 */
@Configuration
public class CorsConfig {

    /**
     * 配置CORS跨域规则
     * 允许所有来源、所有方法、所有请求头
     * 
     * @return WebMvcConfigurer 配置对象
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // 对所有API路径应用CORS配置
                registry.addMapping("/**")
                        // 允许所有来源（生产环境应限制具体域名）
                        .allowedOrigins("*")
                        // 允许的HTTP方法
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        // 允许所有请求头
                        .allowedHeaders("*")
                        // 预检请求缓存时间（秒）
                        .maxAge(3600);
            }
        };
    }
}
