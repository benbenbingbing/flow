package com.workflow.storage.infrastructure.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Prevents replica-local attachment storage in production.
 */
@Component
@Profile("production")
public class ProductionStorageConfigurationGuard {

    public ProductionStorageConfigurationGuard(
            FileStorageProperties properties) {
        if (!"s3".equalsIgnoreCase(properties.getType())) {
            throw new IllegalStateException(
                    "Production requires shared S3-compatible file storage");
        }
    }
}
