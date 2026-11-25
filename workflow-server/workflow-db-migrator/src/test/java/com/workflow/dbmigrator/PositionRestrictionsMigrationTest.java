package com.workflow.dbmigrator;

import db.migration.V145__remove_position_restrictions;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PositionRestrictionsMigrationTest {
    @TempDir Path migrations;

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL", "Oracle"})
    void removesLegacyFieldsAndKeepsDefinitionsAndAssignments(String mode) throws Exception {
        Files.writeString(migrations.resolve("V144__fixture.sql"), """
                CREATE TABLE sys_position (
                  id VARCHAR(64) PRIMARY KEY,
                  position_code VARCHAR(100) NOT NULL UNIQUE,
                  applicable_unit_type VARCHAR(16) NOT NULL,
                  holder_mode VARCHAR(16) NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  CONSTRAINT chk_sys_position_unit_type CHECK (applicable_unit_type IN ('ORG','DEPT','ANY')),
                  CONSTRAINT chk_sys_position_holder_mode CHECK (holder_mode IN ('SINGLE','MULTIPLE')),
                  CONSTRAINT chk_sys_position_status CHECK (status IN ('ENABLED','DISABLED'))
                );
                CREATE TABLE sys_position_assignment (
                  position_id VARCHAR(64) REFERENCES sys_position(id),
                  organization_unit_id VARCHAR(64),
                  user_id VARCHAR(64),
                  PRIMARY KEY (position_id, organization_unit_id, user_id)
                );
                INSERT INTO sys_position VALUES ('p1', 'UNIT_LEADER', 'ANY', 'SINGLE', 'ENABLED');
                INSERT INTO sys_position VALUES ('p2', 'MANAGER', 'DEPT', 'MULTIPLE', 'ENABLED');
                INSERT INTO sys_position_assignment VALUES ('p1', 'org', 'original');
                """);
        String url = "jdbc:h2:mem:position_restrictions_" + mode + ";MODE=" + mode;
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            var migration = new V145__remove_position_restrictions();
            var flyway = Flyway.configure().dataSource(url, "sa", "")
                    .locations("filesystem:" + migrations).javaMigrations(migration).load();
            assertEquals(2, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);

            // 非事务 DDL 中断后重新调用时，已经完成的列删除不会再次执行。
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            migration.migrate(context);

            try (var statement = connection.createStatement()) {
                try (var position = statement.executeQuery("SELECT * FROM sys_position WHERE id = 'p1'")) {
                    var metadata = position.getMetaData();
                    assertEquals(3, metadata.getColumnCount());
                    assertEquals(Set.of("id", "position_code", "status"), Set.of(
                            metadata.getColumnName(1).toLowerCase(Locale.ROOT),
                            metadata.getColumnName(2).toLowerCase(Locale.ROOT),
                            metadata.getColumnName(3).toLowerCase(Locale.ROOT)));
                    assertTrue(position.next());
                    assertEquals("UNIT_LEADER", position.getString("position_code"));
                }
                try (var assignment = statement.executeQuery(
                        "SELECT user_id FROM sys_position_assignment WHERE position_id = 'p1'")) {
                    assertTrue(assignment.next());
                    assertEquals("original", assignment.getString(1));
                }
                assertEquals(1, statement.executeUpdate(
                        "INSERT INTO sys_position_assignment VALUES ('p1', 'org', 'another')"));
                assertEquals(1, statement.executeUpdate(
                        "INSERT INTO sys_position_assignment VALUES ('p2', 'org', 'another')"));
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO sys_position VALUES ('p3', 'INVALID', 'INVALID_STATUS')"));
            }
        }
    }
}
