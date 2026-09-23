package com.workflow.storage.infrastructure.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Prevents replica-local attachment storage in production.
 */
@Component
@Profile("production")
public class ProductionStorageConfigurationGuard {

    /**
     * 初始化{@code production}存储配置保护，保存构造参数供后续方法使用。
     *
     * @param properties 属性集合，保存在对象中供后续校验、查询或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    public ProductionStorageConfigurationGuard(
            FileStorageProperties properties) {
        if (!"s3".equalsIgnoreCase(properties.getType())
                && !"minio".equalsIgnoreCase(properties.getType())) {
            throw new IllegalStateException(
                    "Production requires shared S3-compatible file storage (s3 or minio)");
        }
    }
}
