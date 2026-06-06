package com.workflow.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaRequiredTablesTest {

    @Test
    void schemaDefinesTablesUsedByRoleMenuAndNodeFormMappers() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `sys_role_menu`"),
                "schema.sql must define sys_role_menu used by SysRoleMenuMapper");
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `process_node_form`"),
                "schema.sql must define process_node_form used by ProcessNodeFormMapper");
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `process_node_approval`"),
                "schema.sql must define process_node_approval used by ProcessNodeApprovalMapper");
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS `process_cc_record`"),
                "schema.sql must define process_cc_record used by ProcessCcRecordMapper");
    }

    @Test
    void migrationCreatesTablesUsedByRoleMenuAndNodeFormMappers() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V29__create_missing_role_menu_and_node_form_tables.sql");

        assertTrue(Files.exists(migration),
                "migration must create sys_role_menu and process_node_form for existing databases");

        String sql = Files.readString(migration);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `sys_role_menu`"),
                "migration must create sys_role_menu");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `process_node_form`"),
                "migration must create process_node_form");
    }

    @Test
    void migrationCreatesTablesUsedByApprovalAndCcMappers() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V35__create_process_node_approval_and_cc_tables.sql");

        assertTrue(Files.exists(migration),
                "migration must create process_node_approval and process_cc_record for existing databases");

        String sql = Files.readString(migration);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `process_node_approval`"),
                "migration must create process_node_approval");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `process_cc_record`"),
                "migration must create process_cc_record");
    }
}
