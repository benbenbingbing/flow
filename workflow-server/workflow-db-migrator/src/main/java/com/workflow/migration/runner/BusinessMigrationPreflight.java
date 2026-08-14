package com.workflow.migration.runner;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates data conditions that would otherwise make a non-transactional
 * MySQL Flyway migration fail after partially applying DDL.
 */
public final class BusinessMigrationPreflight {

    private static final int SAMPLE_LIMIT = 10;

    private BusinessMigrationPreflight() {
    }

    public static void verify(Connection connection) throws SQLException {
        if (tableExists(connection, "entity_relation")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT `parent_entity_id`, `relation_code`, COUNT(*)
                    FROM `entity_relation`
                    GROUP BY `parent_entity_id`, `relation_code`
                    HAVING COUNT(*) > 1
                    LIMIT 10
                    """,
                    2,
                    "V043 不能建立关系编码唯一约束",
                    "请先为同一父实体下重复的 relation_code 制定人工重命名映射");
            requireNoConflicts(
                    connection,
                    """
                    SELECT `parent_entity_id`,
                           COALESCE(NULLIF(TRIM(`parent_field_code`), ''),
                                    `relation_code`) AS effective_data_key,
                           COUNT(*)
                    FROM `entity_relation`
                    GROUP BY `parent_entity_id`, effective_data_key
                    HAVING COUNT(*) > 1
                    LIMIT 10
                    """,
                    2,
                    "V043 不能建立关系数据键唯一约束",
                    "请先为同一父实体下重复的有效 data_key 制定人工重命名映射");
        }
        if (tableExists(connection, "entity_record_version")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT `entity_code`, `record_id`, `idempotency_key`,
                           COUNT(*)
                    FROM `entity_record_version`
                    GROUP BY `entity_code`, `record_id`, `idempotency_key`
                    HAVING COUNT(*) > 1
                    LIMIT 10
                    """,
                    3,
                    "V046 不能收紧业务数据版本幂等约束",
                    "请先逐条核对重复 Idempotency-Key 对应的数据版本，禁止静默删除审计记录");
        }
    }

    private static boolean tableExists(
            Connection connection,
            String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(
                connection.getCatalog(),
                null,
                tableName,
                new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private static void requireNoConflicts(
            Connection connection,
            String query,
            int keyColumnCount,
            String title,
            String remediation) throws SQLException {
        List<String> samples = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(query)) {
            while (rows.next() && samples.size() < SAMPLE_LIMIT) {
                List<String> key = new ArrayList<>();
                for (int index = 1; index <= keyColumnCount; index++) {
                    key.add(String.valueOf(rows.getObject(index)));
                }
                key.add("count=" + rows.getLong(keyColumnCount + 1));
                samples.add(String.join("/", key));
            }
        }
        if (!samples.isEmpty()) {
            throw new IllegalStateException(
                    title + "；冲突样例=" + samples + "。" + remediation
                            + "。预检在 Flyway.migrate() 前终止，未写入失败的迁移历史。");
        }
    }
}
