package com.workflow.config;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Applies idempotent additions to databases that already recorded the V001 baseline.
 */
@Component
public class CurrentBaselineSchemaUpgrade {

    private static final String PATCH_RESOURCE =
            "db/upgrade/V001__current_baseline_patch.sql";
    private static final String DATA_SOURCE_TABLE =
            "ui_data_source_definition";
    private static final String OPERATIONS_COLUMN =
            "operations_document";

    public void apply(Flyway flyway) {
        DataSource dataSource =
                flyway.getConfiguration().getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException(
                    "无法获取数据库连接，不能执行 V001 兼容升级");
        }
        try (Connection connection =
                     dataSource.getConnection()) {
            addOperationsColumnIfMissing(connection);
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

    private void addOperationsColumnIfMissing(
            Connection connection) throws SQLException {
        if (hasColumn(
                connection,
                DATA_SOURCE_TABLE,
                OPERATIONS_COLUMN)) {
            return;
        }
        try (Statement statement =
                     connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE `ui_data_source_definition`
                    ADD COLUMN `operations_document`
                    longtext COLLATE utf8mb4_unicode_ci
                    COMMENT '接口服务操作定义JSON数组'
                    AFTER `execution_policy_document`
                    """);
        }
    }

    private boolean hasColumn(
            Connection connection,
            String tableName,
            String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(
                connection.getCatalog(),
                null,
                tableName,
                columnName)) {
            return columns.next();
        }
    }
}
