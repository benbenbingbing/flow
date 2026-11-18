package com.workflow.dbmigrator;

import db.migration.V143__entity_field_options_interface;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 验证增量迁移执行后历史字典字段无需回填，且新增接口字段可落库。 */
class EntityFieldOptionsInterfaceMigrationTest {
    @TempDir Path fixture;

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL", "Oracle"})
    void leavesExistingDictionaryBindingUnchanged(String mode) throws Exception {
        String url = "jdbc:h2:mem:selection_options_" + UUID.randomUUID() + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1";
        Files.writeString(fixture.resolve("V142__fixture.sql"), """
                CREATE TABLE entity_field(id VARCHAR(64) PRIMARY KEY, dict_type VARCHAR(100));
                INSERT INTO entity_field(id, dict_type) VALUES ('old-field', 'expense_type');
                """);
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            var flyway = Flyway.configure().dataSource(url, "sa", "")
                    .locations("filesystem:" + fixture).javaMigrations(new V143__entity_field_options_interface()).load();
            assertEquals(2, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var row = statement.executeQuery("SELECT dict_type, options_interface_id FROM entity_field")) {
                assertTrue(row.next());
                assertEquals("expense_type", row.getString("dict_type"));
                assertNull(row.getString("options_interface_id"));
                assertEquals(64, row.getMetaData().getPrecision(2));
            }
            assertEquals(1, statement.executeUpdate(
                    "INSERT INTO entity_field(id, options_interface_id) VALUES ('new-field', 'read-provider')"));
        }
    }
}
