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

/** 执行真实 V144 发起时机迁移；所有旧启动配置统一后移，保持配置身份和历史事实。 */
class FlowActionMergedStartTimingMigrationTest {
    @TempDir Path migrations;

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL", "Oracle"})
    void upgradeMovesAllEngineStartActionsToStartAfterWithoutChangingIdentityOrderOrHistory(String mode) throws Exception {
        String url = "jdbc:h2:mem:merged_timing_" + UUID.randomUUID() + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1";
        Files.writeString(migrations.resolve("V143__fixture.sql"), """
                CREATE TABLE process_action(id VARCHAR(64) PRIMARY KEY, trigger_timing VARCHAR(50),
                  status VARCHAR(20), deleted INT, enabled BOOLEAN, sort_order INT,
                  predecessor_action_id VARCHAR(64), version_id VARCHAR(64),
                  execution_mode VARCHAR(30), failure_policy VARCHAR(30), extra_params TEXT);
                CREATE TABLE process_action_execution(id VARCHAR(64) PRIMARY KEY, trigger_timing VARCHAR(50), payload TEXT);
                """);
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations).load();
        flyway.migrate();
        var replacements = new LinkedHashMap<String, String>();
        replacements.put("PROCESS_STARTED", "PROCESS_START_EXECUTED");
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            int index = 0;
            for (var mapping : replacements.entrySet()) {
                for (String state : new String[]{"DRAFT", "PUBLISHED", "DELETED"}) {
                    insert(connection, "old-" + index++, mapping.getKey(), state);
                }
                insert(connection, "canonical-" + index, mapping.getValue(), "DRAFT");
            }
            insert(connection, "before", "PROCESS_STARTING", "PUBLISHED");
            insert(connection, "end", "PROCESS_COMPLETED", "PUBLISHED");
            insert(connection, "custom", "CUSTOM_VALIDATING", "DRAFT");
            insert(connection, "empty", null, "DRAFT");
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO process_action_execution VALUES ('audit', 'PROCESS_STARTED', 'original-payload')");
            }
            var original = rows(connection);
            Files.copy(Path.of("src/main/resources/db/migration/V144__merge_process_started_into_start_after.sql"),
                    migrations.resolve("V144__merge_process_started_into_start_after.sql"));
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
                assertTrue(audit.next()); assertEquals("PROCESS_STARTED", audit.getString("trigger_timing"));
                assertEquals("original-payload", audit.getString("payload")); assertFalse(audit.next());
            }
        }
    }

    /** 有意同时包含禁用和已删除记录，迁移不能依赖执行过滤条件而遗漏将来恢复的配置。 */
    private void insert(Connection connection, String id, String timing, String state) throws Exception {
        try (var statement = connection.prepareStatement("INSERT INTO process_action VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, id); statement.setString(2, timing); statement.setString(3, state);
            statement.setInt(4, "DELETED".equals(state) ? 1 : 0); statement.setBoolean(5, !"DRAFT".equals(state));
            statement.setInt(6, 13); statement.setString(7, "predecessor"); statement.setString(8, "published-v1");
            statement.setString(9, "DRAFT".equals(state) ? "IN_TRANSACTION" : "AFTER_COMMIT");
            statement.setString(10, "DRAFT".equals(state) ? "ROLLBACK" : "RETRY");
            statement.setString(11, "{\"requiredOutcome\":true}");
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
