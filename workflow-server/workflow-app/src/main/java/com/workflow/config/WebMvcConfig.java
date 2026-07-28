package com.workflow.config;

import com.workflow.storage.infrastructure.config.FileStorageProperties;

import java.nio.file.Path;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * Web MVC 配置
 */
@Configuration
@ConditionalOnProperty(
        name = "file.storage.type",
        havingValue = "local",
        matchIfMissing = true)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<FileStorageProperties> fileStoragePropertiesProvider;

    /**
     * 配置静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        FileStorageProperties fileStorageProperties =
                fileStoragePropertiesProvider.getIfAvailable(FileStorageProperties::new);
        // 映射上传文件目录
        String path = fileStorageProperties.getLocal().getPath();
        String resourceLocation = path.startsWith("file:")
                ? path
                : Path.of(path).toAbsolutePath().normalize().toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation = resourceLocation + "/";
        }
        
        String accessUrl = fileStorageProperties.getLocal().getAccessUrl();
        String resourcePattern = (accessUrl.endsWith("/")
                ? accessUrl.substring(0, accessUrl.length() - 1)
                : accessUrl) + "/**";
        registry.addResourceHandler(resourcePattern)
                .addResourceLocations(resourceLocation);
    }
}
