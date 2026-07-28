package com.workflow.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies validated, forward-only Flyway migrations.
 */
@Configuration(proxyBeanMethods = false)
public class FreshInstallFlywayConfiguration {

    @Bean
    FlywayMigrationStrategy forwardOnlyMigrationStrategy() {
        return flyway -> {
            flyway.validate();
            flyway.migrate();
        };
    }
}
