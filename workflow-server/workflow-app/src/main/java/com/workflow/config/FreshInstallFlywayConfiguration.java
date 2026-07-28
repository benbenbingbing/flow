package com.workflow.config;

import org.flywaydb.core.Flyway;
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
        // migrate() validates applied migrations while allowing pending versions to run.
        return Flyway::migrate;
    }
}
