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

    /**
     * 处理{@code forward}仅迁移{@code strategy}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code forward}仅迁移{@code strategy}结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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
