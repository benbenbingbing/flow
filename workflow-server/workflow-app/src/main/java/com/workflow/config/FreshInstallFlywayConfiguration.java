package com.workflow.config;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enforces the fresh-install-only database contract.
 */
@Configuration(proxyBeanMethods = false)
public class FreshInstallFlywayConfiguration {

    private static final MigrationVersion BUSINESS_BASELINE_VERSION =
            MigrationVersion.fromVersion("1");

    @Bean
    FlywayMigrationStrategy freshInstallOnlyMigrationStrategy() {
        return flyway -> {
            MigrationInfo current = flyway.info().current();
            if (current != null
                    && !BUSINESS_BASELINE_VERSION.equals(current.getVersion())) {
                String currentVersion = current.getVersion() == null
                        ? "unknown"
                        : current.getVersion().getVersion();
                throw new IllegalStateException(
                        "检测到不兼容的历史数据库版本 "
                                + currentVersion
                                + "。当前版本统一使用 V001 基线，"
                                + "请先备份数据库并执行一次性基线接管。");
            }
            flyway.migrate();
        };
    }
}
