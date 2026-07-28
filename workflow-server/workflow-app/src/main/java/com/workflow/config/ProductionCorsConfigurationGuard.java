package com.workflow.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Prevents permissive browser origins in production.
 */
@Component
@Profile("production")
public class ProductionCorsConfigurationGuard {

    public ProductionCorsConfigurationGuard(
            CorsProperties properties) {
        if (properties.getAllowedOrigins().isEmpty()
                || properties.getAllowedOrigins().stream()
                        .anyMatch(origin ->
                                origin == null
                                        || origin.isBlank()
                                        || origin.contains("*"))) {
            throw new IllegalStateException(
                    "Production CORS origins must be explicit");
        }
    }
}
