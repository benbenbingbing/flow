package com.workflow.config;

import com.workflow.migration.runner.BusinessMigrationPreflight;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Applies validated, forward-only Flyway migrations.
 */
@Configuration(proxyBeanMethods = false)
public class FreshInstallFlywayConfiguration {

    @Bean
    FlywayMigrationStrategy forwardOnlyMigrationStrategy() {
        return flyway -> {
            DataSource dataSource = flyway.getConfiguration().getDataSource();
            if (dataSource == null) {
                throw new IllegalStateException("无法获取 Flyway 数据源进行迁移预检");
            }
            try (Connection connection = dataSource.getConnection()) {
                BusinessMigrationPreflight.verify(connection);
            } catch (SQLException exception) {
                throw new IllegalStateException("业务数据库迁移预检失败", exception);
            }
            flyway.migrate();
        };
    }
}
