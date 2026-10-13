package com.workflow.dbmigrator;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V117 只扩展存储，不自动扩大存量接口的页面范围或修改已发布契约。 */
class InterfaceExtensionEntityScopesMigrationTest {

    @Test
    void addsEntityCollectionWithoutRewritingLegacyScopes() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:interface_scope_v117;MODE=MySQL;DB_CLOSE_DELAY=-1");
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE ui_extension_definition (
                        id VARCHAR(64) PRIMARY KEY,
                        scope_type VARCHAR(20),
                        scope_id VARCHAR(64)
                    )
                    """);
            statement.execute("""
                    INSERT INTO ui_extension_definition VALUES
                        ('legacy', 'FORM', 'form-a'),
                        ('multiple', 'ENTITY', 'entity-a')
                    """);
            try (var resource = Objects.requireNonNull(getClass().getResourceAsStream(
                    "/db/migration/V118__interface_extension_entity_scopes.sql"))) {
                statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (var rows = statement.executeQuery("""
                    SELECT scope_type, scope_id, scope_entity_ids_document
                    FROM ui_extension_definition WHERE id = 'legacy'
                    """)) {
                assertTrue(rows.next());
                assertEquals("FORM", rows.getString(1));
                assertEquals("form-a", rows.getString(2));
                assertNull(rows.getString(3));
            }
            statement.execute("""
                    UPDATE ui_extension_definition
                    SET scope_id = NULL, scope_entity_ids_document = '["entity-a","entity-b"]'
                    WHERE id = 'multiple'
                    """);
            try (var rows = statement.executeQuery("""
                    SELECT scope_entity_ids_document FROM ui_extension_definition WHERE id = 'multiple'
                    """)) {
                assertTrue(rows.next());
                assertEquals("[\"entity-a\",\"entity-b\"]", rows.getString(1));
            }
        }
    }
}
