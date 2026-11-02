package com.workflow.dbmigrator;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 迁移只清理档案专属权限及授权，保留实体查看、导入管理和历史业务数据。 */
class ProcessArchivePermissionMigrationTest {
    @Test
    void removesDedicatedGrantsBeforePermissionWithoutChangingArchives() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
             var sql = db.createStatement()) {
            sql.execute("CREATE TABLE sys_menu(id VARCHAR PRIMARY KEY, perm VARCHAR)");
            sql.execute("CREATE TABLE sys_role_menu(role_id VARCHAR, menu_id VARCHAR REFERENCES sys_menu(id))");
            sql.execute("CREATE TABLE process_archive_instance(id VARCHAR PRIMARY KEY, document_json CLOB)");
            sql.execute("INSERT INTO sys_menu VALUES ('process_archive_read','process:archive:read'),"
                    + "('custom_archive_read','process:archive:read'),('view','entity:expense:view'),"
                    + "('history_import_menu','history-import:read'),('execute','history-import:execute')");
            sql.execute("INSERT INTO sys_role_menu SELECT 'viewer',id FROM sys_menu");
            sql.execute("INSERT INTO process_archive_instance VALUES ('archive','{\"events\":[1,2]}')");
            try (var source = getClass().getResourceAsStream("/db/migration/V125__merge_process_archive_into_entity_view.sql")) {
                assertNotNull(source);
                for (String command : new String(source.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                    if (!command.isBlank()) sql.execute(command);
                }
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM sys_menu")) {
                rows.next(); assertEquals(3, rows.getInt(1));
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM sys_role_menu")) {
                rows.next(); assertEquals(3, rows.getInt(1));
            }
            try (var rows = sql.executeQuery("SELECT document_json FROM process_archive_instance WHERE id='archive'")) {
                assertTrue(rows.next()); assertEquals("{\"events\":[1,2]}", rows.getString(1));
            }
        }
    }
}
