package com.workflow.config;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Applies idempotent additions to databases that already recorded the V001 baseline.
 */
@Component
public class CurrentBaselineSchemaUpgrade {

    private static final String PATCH_RESOURCE =
            "db/upgrade/V001__current_baseline_patch.sql";
    /** 执行仍需保留的幂等基线补丁。 */
    public void apply(Flyway flyway) {
        DataSource dataSource =
                flyway.getConfiguration().getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException(
                    "无法获取数据库连接，不能执行 V001 兼容升级");
        }
        try (Connection connection =
                     dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(
                            new ClassPathResource(PATCH_RESOURCE),
                            StandardCharsets.UTF_8));
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "执行 V001 兼容升级失败",
                    exception);
        }
    }
}
