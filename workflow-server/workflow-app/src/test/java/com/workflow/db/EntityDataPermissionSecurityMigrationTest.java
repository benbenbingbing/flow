package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDataPermissionSecurityMigrationTest {

    @Test
    void migrationDisablesUnsafeLegacyRulesAndAddsRuntimeIndex() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V016__secure_entity_list_permission.sql"));

        assertTrue(sql.contains("SET enabled = 0"));
        assertTrue(sql.contains("'CUSTOM_SQL'"));
        assertTrue(sql.contains("'EXPRESSION'"));
        assertTrue(sql.contains("'$.fieldMapping.userField'"));
        assertTrue(sql.contains("CHANGE COLUMN created_at create_time"));
        assertTrue(sql.contains("CHANGE COLUMN updated_at update_time"));
        assertTrue(sql.contains("idx_entity_permission_runtime"));
    }
}
