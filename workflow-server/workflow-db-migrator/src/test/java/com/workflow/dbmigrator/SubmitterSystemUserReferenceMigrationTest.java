package com.workflow.dbmigrator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 验证存量元数据迁移只转换有效动态实体的提交人字段。 */
class SubmitterSystemUserReferenceMigrationTest {
    @TempDir Path migrations;

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL", "Oracle"})
    void upgradesOnlySystemSubmitterAndPreservesPublishedHistory(String mode) throws Exception {
        String url = "jdbc:h2:mem:submitter_reference_" + UUID.randomUUID() + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1";
        Files.writeString(migrations.resolve("V145__fixture.sql"), """
                CREATE TABLE entity_definition (id BIGINT PRIMARY KEY, entity_code VARCHAR(100),
                    storage_mode VARCHAR(20), deleted SMALLINT);
                CREATE TABLE entity_field (id BIGINT PRIMARY KEY, entity_id BIGINT, field_code VARCHAR(100),
                    field_type VARCHAR(50), is_system SMALLINT, deleted SMALLINT, editable SMALLINT,
                    db_column_name VARCHAR(100), ref_entity_type VARCHAR(20), ref_entity_id VARCHAR(64));
                CREATE TABLE entity_publish_history (id BIGINT PRIMARY KEY, fields_snapshot VARCHAR(200));
                INSERT INTO entity_definition VALUES (100, 'sys_user', 'SYSTEM', 0);
                INSERT INTO entity_definition VALUES (200, 'expense', 'DYNAMIC', 0);
                INSERT INTO entity_definition VALUES (201, 'custom', 'DYNAMIC', 0);
                INSERT INTO entity_definition VALUES (202, 'system', 'SYSTEM', 0);
                INSERT INTO entity_definition VALUES (203, 'deleted', 'DYNAMIC', 1);
                INSERT INTO entity_field VALUES (1, 200, 'submitterId', 'STRING', 1, 0, 0, 'submitter_id', NULL, NULL);
                INSERT INTO entity_field VALUES (2, 200, 'submitterName', 'STRING', 1, 0, 0, 'submitter_name', NULL, NULL);
                INSERT INTO entity_field VALUES (3, 201, 'submitterId', 'STRING', 0, 0, 1, 'submitter_id', NULL, NULL);
                INSERT INTO entity_field VALUES (4, 202, 'submitterId', 'STRING', 1, 0, 0, 'submitter_id', NULL, NULL);
                INSERT INTO entity_field VALUES (5, 200, 'submitterId', 'STRING', 1, 1, 0, 'submitter_id', NULL, NULL);
                INSERT INTO entity_field VALUES (6, 203, 'submitterId', 'STRING', 1, 0, 0, 'submitter_id', NULL, NULL);
                INSERT INTO entity_publish_history VALUES (9, '[{"fieldCode":"submitterId","fieldType":"STRING"}]');
                """);
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations).load();
        flyway.migrate();
        String migration = "V146__submitter_system_user_reference.sql";
        Files.copy(Path.of("src/main/resources/db/migration", migration), migrations.resolve(migration));

        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT * FROM entity_field WHERE field_type = 'REFERENCE'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getLong("id"));
                assertEquals("100", rows.getString("ref_entity_id"));
                assertEquals("USER", rows.getString("ref_entity_type"));
                assertEquals("submitter_id", rows.getString("db_column_name"));
                assertEquals(0, rows.getInt("editable"));
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("SELECT fields_snapshot FROM entity_publish_history WHERE id = 9")) {
                assertTrue(rows.next());
                assertEquals("[{\"fieldCode\":\"submitterId\",\"fieldType\":\"STRING\"}]", rows.getString(1));
            }
        }
    }
}
