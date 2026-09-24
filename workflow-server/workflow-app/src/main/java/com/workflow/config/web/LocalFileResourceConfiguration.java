package com.workflow.config.web;

import com.workflow.storage.infrastructure.config.FileStorageProperties;

import java.nio.file.Path;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * 本地文件存储的静态资源映射配置。
 *
 * <p>仅在启用本地存储时，将配置的访问 URL 映射到上传目录；对象存储由其自身提供访问地址。
 */
@Configuration
@ConditionalOnProperty(
        name = "file.storage.type",
        havingValue = "local",
        matchIfMissing = true)
@RequiredArgsConstructor
public class LocalFileResourceConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<FileStorageProperties> fileStoragePropertiesProvider;

    /**
     * 配置静态资源映射
     *
     * @param registry {@code registry}，供本方法添加资源{@code handlers}时使用
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
