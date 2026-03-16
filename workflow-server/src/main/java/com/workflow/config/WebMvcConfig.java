package com.workflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Value("${file.access.url:/uploads}")
    private String accessUrl;

    /**
     * 配置静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射上传文件目录
        String path = uploadPath;
        if (!path.startsWith("file:")) {
            path = "file:" + path;
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        
        registry.addResourceHandler(accessUrl + "/**")
                .addResourceLocations(path);
    }
}
