package com.workflow.dbmigrator;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 清理真实 SQL 的删除范围与外键顺序，防止误删共享的列表发布、审计和流程引用。 */
class RetiredFormVersionsAndHotfixMigrationTest {
    @Test
    void dropsOnlyRetiredTablesFormVersionsAndDedicatedPermissions() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")) {
            try (Statement sql = db.createStatement()) {
                sql.execute("CREATE TABLE ui_config_release(id VARCHAR PRIMARY KEY,config_type VARCHAR)");
                sql.execute("INSERT INTO ui_config_release VALUES('old-form','FORM'),('list-v6','LIST')");
                sql.execute("CREATE TABLE ui_config_release_audit(id VARCHAR PRIMARY KEY,release_id VARCHAR)");
                sql.execute("INSERT INTO ui_config_release_audit VALUES('form-audit','old-form'),('list-audit','list-v6')");
                sql.execute("CREATE TABLE process_ui_release_binding(form_id VARCHAR,pinned_release_id VARCHAR)");
                sql.execute("INSERT INTO process_ui_release_binding VALUES('form','')");
                sql.execute("CREATE TABLE sys_menu(id VARCHAR PRIMARY KEY,perm VARCHAR,deleted INT)");
                sql.execute("INSERT INTO sys_menu VALUES('hotfix','entity:ui-config:hotfix',1),('review','entity:ui-config:hotfix:review',1),('form','entity:form:publish',0)");
                sql.execute("CREATE TABLE sys_role_menu(role_id VARCHAR,menu_id VARCHAR)");
                sql.execute("INSERT INTO sys_role_menu VALUES('role','hotfix'),('role','review'),('role','form')");
                sql.execute("CREATE TABLE ui_config_hotfix_target(id VARCHAR PRIMARY KEY)");
                sql.execute("CREATE TABLE ui_config_hotfix_request(id VARCHAR PRIMARY KEY)");
                // 保留实际外键方向，父表先删除会使迁移失败。
                sql.execute("CREATE TABLE ui_hotfix_observation_metric(id VARCHAR PRIMARY KEY,request_id VARCHAR REFERENCES ui_config_hotfix_request(id))");
                sql.execute("INSERT INTO ui_config_hotfix_request VALUES('request')");
                sql.execute("INSERT INTO ui_hotfix_observation_metric VALUES('metric','request')");
                script(sql, "V112__entity_form_current_publication.sql");
                sql.execute("INSERT INTO entity_form_published(form_id,entity_id,snapshot_document,content_hash,published_at) VALUES('form','entity','{}','current-hash',CURRENT_TIMESTAMP)");
                sql.execute("INSERT INTO entity_form_current_default VALUES('entity','form')");
                script(sql, "V114__remove_retired_form_versions_and_hotfix.sql");
            }
            for (String table : List.of("ui_config_hotfix_target", "ui_config_hotfix_request", "ui_hotfix_observation_metric")) {
                assertEquals(0, count(db, "INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='" + table.toUpperCase(java.util.Locale.ROOT) + "'"));
            }
            assertEquals(0, count(db, "ui_config_release WHERE config_type='FORM'"));
            assertEquals(1, count(db, "ui_config_release WHERE id='list-v6'"));
            assertEquals(2, count(db, "ui_config_release_audit"));
            assertEquals(1, count(db, "process_ui_release_binding WHERE form_id='form'"));
            assertEquals(1, count(db, "entity_form_published WHERE content_hash='current-hash'"));
            assertEquals(1, count(db, "entity_form_current_default WHERE form_id='form'"));
            assertEquals(1, count(db, "entity_form_publication_lock"));
            assertEquals(1, count(db, "sys_menu WHERE id='form'"));
            assertEquals(1, count(db, "sys_role_menu WHERE menu_id='form'"));
            assertEquals(1, count(db, "sys_menu"));
            assertEquals(1, count(db, "sys_role_menu"));
        }
    }

    private void script(Statement sql, String name) throws Exception {
        try (var source = getClass().getResourceAsStream("/db/migration/" + name)) {
            assertNotNull(source);
            for (String command : new String(source.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!command.isBlank()) sql.execute(command);
            }
        }
    }

    private int count(Connection db, String tableExpression) throws Exception {
        try (var sql = db.createStatement(); var rows = sql.executeQuery("SELECT COUNT(*) FROM " + tableExpression)) {
            rows.next(); return rows.getInt(1);
        }
    }
}
