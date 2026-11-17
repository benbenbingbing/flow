package com.workflow.dbmigrator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 执行真实 V139 数据迁移；旧草稿和发布配置收敛，历史事实及其他业务配置保持不变。 */
class FlowActionMergedTimingMigrationTest {
    @TempDir Path migrations;

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL", "Oracle"})
    void upgradeMergesAllEightTimingsWithoutChangingIdentityOrderOrHistory(String mode) throws Exception {
        String url = "jdbc:h2:mem:merged_timing_" + UUID.randomUUID() + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1";
        Files.writeString(migrations.resolve("V138__fixture.sql"), """
                CREATE TABLE process_action(id VARCHAR(64) PRIMARY KEY, trigger_timing VARCHAR(50),
                  status VARCHAR(20), deleted INT, enabled BOOLEAN, sort_order INT,
                  predecessor_action_id VARCHAR(64), version_id VARCHAR(64));
                CREATE TABLE process_action_execution(id VARCHAR(64) PRIMARY KEY, trigger_timing VARCHAR(50), payload TEXT);
                """);
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations).load();
        flyway.migrate();
        var replacements = new LinkedHashMap<String, String>();
        replacements.put("PROCESS_START_VALIDATING", "PROCESS_STARTING");
        replacements.put("PROCESS_WITHDRAW_VALIDATING", "PROCESS_WITHDRAWING");
        replacements.put("PROCESS_TERMINATE_VALIDATING", "PROCESS_TERMINATING");
        replacements.put("TASK_SUBMIT_VALIDATING", "TASK_FORM_SAVING");
        replacements.put("TASK_TRANSFER_VALIDATING", "TASK_TRANSFERRING");
        replacements.put("TASK_ADD_SIGN_VALIDATING", "TASK_ADD_SIGNING");
        replacements.put("ADD_SIGN_SOURCE_VALIDATING", "ADD_SIGN_SOURCE_SUBMITTING");
        replacements.put("ADD_SIGN_TASK_VALIDATING", "ADD_SIGN_TASK_COMPLETING");
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            int index = 0;
            for (var mapping : replacements.entrySet()) {
                for (String state : new String[]{"DRAFT", "PUBLISHED", "DELETED"}) {
                    insert(connection, "old-" + index++, mapping.getKey(), state);
                }
                insert(connection, "canonical-" + index, mapping.getValue(), "DRAFT");
            }
            insert(connection, "engine", "TASK_COMPLETING", "PUBLISHED");
            insert(connection, "custom", "CUSTOM_VALIDATING", "DRAFT");
            insert(connection, "empty", null, "DRAFT");
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO process_action_execution VALUES ('audit', 'PROCESS_WITHDRAW_VALIDATING', 'original-payload')");
            }
            var original = rows(connection);
            Files.copy(Path.of("src/main/resources/db/migration/V139__merge_flow_action_before_timings.sql"),
                    migrations.resolve("V139__merge_flow_action_before_timings.sql"));
            assertEquals(1, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            var migrated = rows(connection);
            assertEquals(original.keySet(), migrated.keySet());
            for (var entry : original.entrySet()) {
                var expected = new LinkedHashMap<>(entry.getValue());
                String timing = (String) expected.get("TRIGGER_TIMING");
                expected.put("TRIGGER_TIMING", replacements.getOrDefault(timing, timing));
                assertEquals(expected, migrated.get(entry.getKey()), mode + " / " + entry.getKey());
            }
            try (var statement = connection.createStatement(); var audit = statement.executeQuery("SELECT * FROM process_action_execution")) {
                assertTrue(audit.next()); assertEquals("PROCESS_WITHDRAW_VALIDATING", audit.getString("trigger_timing"));
                assertEquals("original-payload", audit.getString("payload")); assertFalse(audit.next());
            }
        }
    }

    /** 有意同时包含禁用和已删除记录，迁移不能依赖执行过滤条件而遗漏将来恢复的配置。 */
    private void insert(Connection connection, String id, String timing, String state) throws Exception {
        try (var statement = connection.prepareStatement("INSERT INTO process_action VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, id); statement.setString(2, timing); statement.setString(3, state);
            statement.setInt(4, "DELETED".equals(state) ? 1 : 0); statement.setBoolean(5, !"DRAFT".equals(state));
            statement.setInt(6, 13); statement.setString(7, "predecessor"); statement.setString(8, "published-v1");
            statement.executeUpdate();
        }
    }

    private Map<String, Map<String, Object>> rows(Connection connection) throws Exception {
        var result = new LinkedHashMap<String, Map<String, Object>>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM process_action ORDER BY id")) {
            while (rows.next()) {
                var values = new LinkedHashMap<String, Object>();
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                    values.put(rows.getMetaData().getColumnName(column), rows.getObject(column));
                }
                result.put(rows.getString("id"), values);
            }
        }
        return result;
    }
}
