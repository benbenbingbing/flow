package com.workflow.dbmigrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import db.migration.V134__file_references_use_file_id;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 执行真实迁移，覆盖旧值形状、分页、精确地址匹配及迁移失败后的事务回滚。 */
class FileReferenceMigrationTest {
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL", "Oracle"})
    void migratesReferencesWithoutChangingStorageOrPlainTextAndCanRepeat(String mode) throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            fixture(connection);
            try (var insert = connection.prepareStatement("INSERT INTO biz_case VALUES (?, ?, ?)")) {
                for (int i = 0; i < 205; i++) {
                    insert.setInt(1, i); insert.setString(2, "{\"合同\":[{\"url\":\"s3://files/a.pdf\",\"name\":\"原名.pdf\"}],\"资料\":[\"/uploads/b.pdf\"]}");
                    insert.setString(3, "s3://files/a.pdf"); insert.addBatch();
                }
                insert.executeBatch();
            }
            var flyway = flyway(url);
            assertEquals(1, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT attachment, notes FROM biz_case WHERE id=204")) {
                assertTrue(rows.next()); var value = json.readTree(rows.getString(1));
                assertEquals("file-a", value.path("合同").get(0).path("fileId").asText());
                assertEquals("原名.pdf", value.path("合同").get(0).path("name").asText());
                assertFalse(value.path("合同").get(0).has("url"));
                assertEquals("file-b", value.path("资料").get(0).path("fileId").asText());
                assertEquals("s3://files/a.pdf", rows.getString(2));
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT form_data FROM process_task WHERE id='task-1'")) {
                assertTrue(rows.next()); var value = json.readTree(rows.getString(1));
                assertEquals("file-a", value.path("attachment").path("fileId").asText());
                assertEquals("s3://files/a.pdf", value.path("notes").asText());
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT source_form_data FROM process_task_add_sign")) {
                assertTrue(rows.next()); var value = json.readTree(rows.getString(1));
                assertEquals("file-a", value.path("attachment").get(0).path("fileId").asText());
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT form_data FROM process_task WHERE id='task-text'")) {
                assertTrue(rows.next()); assertEquals("s3://files/a.pdf", json.readTree(rows.getString(1)).path("attachment").asText());
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT storage_url FROM storage_file_object WHERE id='file-a'")) {
                assertTrue(rows.next()); assertEquals("s3://files/a.pdf", rows.getString(1));
            }
        }
    }

    @Test
    void unknownAddressStopsMigrationAndRollsBackPreviousConversions() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            fixture(connection);
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO biz_case VALUES(1,'s3://files/a.pdf','text'),(2,'s3://unknown/file.pdf','text')");
            }
            assertThrows(Exception.class, () -> flyway(url).migrate());
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT attachment FROM biz_case WHERE id=1")) {
                assertTrue(rows.next()); assertEquals("s3://files/a.pdf", rows.getString(1));
            }
        }
    }

    private Flyway flyway(String url) {
        return Flyway.configure().dataSource(url, "sa", "").locations("classpath:file-reference-only")
                .baselineOnMigrate(true).baselineVersion("133").javaMigrations(new V134__file_references_use_file_id()).load();
    }

    private void fixture(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE storage_file_object(id VARCHAR(32) PRIMARY KEY, storage_url VARCHAR(1024), original_name VARCHAR(512), content_length BIGINT)");
            statement.execute("INSERT INTO storage_file_object VALUES('file-a','s3://files/a.pdf','合同.pdf',123),('file-b','/uploads/b.pdf','资料.pdf',456)");
            statement.execute("CREATE TABLE entity_definition(id INT, table_name VARCHAR(100), entity_code VARCHAR(100))");
            statement.execute("INSERT INTO entity_definition VALUES(1,'biz_case','case')");
            statement.execute("CREATE TABLE entity_field(entity_id INT, field_code VARCHAR(100), db_column_name VARCHAR(100), field_type VARCHAR(30))");
            statement.execute("INSERT INTO entity_field VALUES(1,'attachment','attachment','FILE'),(1,'unpublished','not_created','IMAGE')");
            statement.execute("CREATE TABLE biz_case(id INT PRIMARY KEY, attachment CLOB, notes CLOB)");
            statement.execute("CREATE TABLE process_task(id VARCHAR(64) PRIMARY KEY, form_data CLOB, entity_code VARCHAR(100), task_id VARCHAR(64))");
            statement.execute("INSERT INTO process_task VALUES('task-1','{\"attachment\":\"s3://files/a.pdf\",\"notes\":\"s3://files/a.pdf\"}','case','source-task')");
            statement.execute("INSERT INTO process_task VALUES('task-text','{\"attachment\":\"s3://files/a.pdf\"}','other-entity','text-task')");
            statement.execute("CREATE TABLE process_task_add_sign(id VARCHAR(64) PRIMARY KEY, source_task_id VARCHAR(64), source_form_data CLOB)");
        }
        try (var statement = connection.prepareStatement("INSERT INTO process_task_add_sign VALUES('sign-1','source-task',?)")) {
            statement.setString(1, json.writeValueAsString(java.util.Map.of("attachment",
                    json.writeValueAsString(java.util.List.of(java.util.Map.of("url", "s3://files/a.pdf"))))));
            statement.executeUpdate();
        }
    }
}
