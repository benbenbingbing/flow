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

/** 验证空库脚本和 Flyway 共用的平台状态接口注册契约。 */
class EntityStatusOptionsProviderMigrationTest {
    @TempDir Path migrations;

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL", "Oracle"})
    void registersAReadOnlyFormOptionsInterface(String mode) throws Exception {
        String url = "jdbc:h2:mem:entity_status_options_" + UUID.randomUUID()
                + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1";
        Files.writeString(migrations.resolve("V146__fixture.sql"), """
                CREATE TABLE ui_extension_definition (
                    id VARCHAR(64) PRIMARY KEY, extension_type VARCHAR(20), extension_key VARCHAR(255),
                    display_name VARCHAR(200), version INTEGER, snapshot_version INTEGER,
                    visibility_scope VARCHAR(20), implementation_type VARCHAR(30), provider_code VARCHAR(100),
                    scope_type VARCHAR(20), interface_kind VARCHAR(20), interface_context_type VARCHAR(20),
                    provider_operation_code VARCHAR(100), output_schema_document VARCHAR(4000),
                    status VARCHAR(20), revision INTEGER, create_time TIMESTAMP, update_time TIMESTAMP, deleted SMALLINT
                );
                """);
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + migrations).load();
        flyway.migrate();
        String migration = "V147__entity_status_options_provider.sql";
        Files.copy(Path.of("src/main/resources/db/migration", migration), migrations.resolve(migration));
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM ui_extension_definition")) {
            assertTrue(rows.next());
            assertEquals("platform_entity_status_options", rows.getString("id"));
            assertEquals("entity_status_options", rows.getString("provider_code"));
            assertEquals("REGISTERED_PROVIDER", rows.getString("implementation_type"));
            assertEquals("READ", rows.getString("interface_kind"));
            assertEquals("FORM", rows.getString("interface_context_type"));
            assertEquals("ACTIVE", rows.getString("status"));
            assertTrue(rows.getString("output_schema_document").contains("\"required\":[\"value\",\"label\"]"));
            assertFalse(rows.next());
        }
    }
}
