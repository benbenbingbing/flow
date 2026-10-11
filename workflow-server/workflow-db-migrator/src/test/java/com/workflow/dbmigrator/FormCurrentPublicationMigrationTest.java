package com.workflow.dbmigrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import db.migration.V113__migrate_forms_to_current_publication;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HexFormat;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 新迁移只复制合法 ACTIVE 内容；异常升级不得启用草稿或部分覆盖当前配置。 */
class FormCurrentPublicationMigrationTest {
    private final JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
    @Test void movesOnlyCurrentPublicationAndStripsSubformCoordinates() throws Exception {
        try (Connection connection = database()) {
            source(connection, "child", "{\"form\":{\"id\":\"child\"}}", false);
            source(connection, "parent", "{\"form\":{\"id\":\"parent\",\"isDefault\":true},\"nodes\":[{\"propsDocument\":\"{\\\"childFormId\\\":\\\"child\\\",\\\"childFormReleaseId\\\":\\\"old-child\\\",\\\"childFormReleaseVersion\\\":3}\"}]}", false);
            try (Statement sql = connection.createStatement()) {
                sql.execute("INSERT INTO entity_form VALUES('draft-only','entity',0,'')");
                sql.execute("INSERT INTO ui_config_release VALUES('historical','FORM','parent','INACTIVE','{}','invalid',null,CURRENT_TIMESTAMP)");
            }
            migrate(connection);
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM entity_form_published"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM ui_config_release WHERE config_type='FORM' AND status<>'ARCHIVED'"));
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT snapshot_document FROM entity_form_published WHERE form_id='parent'")) {
                assertTrue(rows.next()); String document = rows.getString(1);
                assertTrue(document.contains("childFormId")); assertFalse(document.contains("childFormRelease"));
            }
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM entity_form_current_default WHERE form_id='parent'"));
            assertThrows(SQLException.class, () -> connection.createStatement().execute(
                    "INSERT INTO entity_form_published SELECT * FROM entity_form_published WHERE form_id='parent'"));
        }
    }
    @Test void invalidContentHashAbortsWithoutArchivingOrWriting() throws Exception {
        try (Connection connection = database()) {
            source(connection,"good","{\"form\":{\"id\":\"good\"}}", false);
            source(connection,"bad","{\"form\":{\"id\":\"bad\"}}", true);
            assertThrows(SQLException.class, () -> migrate(connection));
            assertEquals(0, scalar(connection,"SELECT COUNT(*) FROM entity_form_published"));
            assertEquals(2, scalar(connection,"SELECT COUNT(*) FROM ui_config_release WHERE status='ACTIVE'"));
        }
    }
    @Test void missingCurrentChildAbortsWithIdentity() throws Exception {
        try (Connection connection = database()) {
            source(connection,"parent","{\"form\":{\"id\":\"parent\"},\"childFormId\":\"missing\"}", false);
            SQLException error = assertThrows(SQLException.class, () -> migrate(connection));
            assertTrue(error.getMessage().contains("missing"));
            assertEquals(0, scalar(connection,"SELECT COUNT(*) FROM entity_form_published"));
        }
    }
    @Test void externalMissingFormAbortsBeforeAnyWrites() throws Exception {
        for (String statement : java.util.List.of(
                "INSERT INTO process_ui_release_binding VALUES('FORM','missing','task','old-release',7)",
                "INSERT INTO embed_view_release VALUES('missing')",
                "INSERT INTO process_version_history VALUES('old-process','[{\"formId\":\"missing\"}]')",
                "INSERT INTO ui_config_release VALUES('list','LIST','list','ACTIVE','{\"buttons\":[{\"targetFormId\":\"missing\"}]}','unused',null,CURRENT_TIMESTAMP)")) {
            try (Connection connection = database()) {
                source(connection,"good","{\"form\":{\"id\":\"good\"}}", false);
                connection.createStatement().execute(statement);
                assertTrue(assertThrows(SQLException.class, () -> migrate(connection)).getMessage().contains("missing"));
                assertEquals(0, scalar(connection,"SELECT COUNT(*) FROM entity_form_published"));
                assertEquals(1, scalar(connection,"SELECT COUNT(*) FROM ui_config_release WHERE config_type='FORM' AND status='ACTIVE'"));
            }
        }
    }
    @Test void mismatchedEntityAborts() throws Exception {
        try (Connection connection = database()) {
            source(connection,"bad","{\"form\":{\"id\":\"bad\",\"entityId\":\"other\"}}", false);
            assertTrue(assertThrows(SQLException.class, () -> migrate(connection)).getMessage().contains("所属实体"));
            assertEquals(0, scalar(connection,"SELECT COUNT(*) FROM entity_form_published"));
        }
    }
    @Test void legacyHistoryWithClearedPublicationDoesNotPublishDraftOrRewriteHistory() throws Exception {
        try (Connection connection = database()) {
            source(connection,"good","{\"form\":{\"id\":\"good\"}}",false);
            String history = "[{\"nodeId\":\"review\",\"formId\":\"legacy\",\"formReleaseId\":\"cleared-release\",\"formReleaseVersion\":4}]";
            connection.createStatement().execute("INSERT INTO entity_form VALUES('legacy','entity',0,NULL)");
            try (var insert = connection.prepareStatement("INSERT INTO process_version_history VALUES('pre-V034',?)")) {
                insert.setString(1,history); insert.executeUpdate();
            }
            migrate(connection);
            assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM entity_form_published"));
            assertEquals(0,scalar(connection,"SELECT COUNT(*) FROM entity_form_published WHERE form_id='legacy'"));
            assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM entity_form WHERE id='legacy' AND active_release_id IS NULL"));
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT node_forms_snapshot FROM process_version_history WHERE id='pre-V034'")) {
                assertTrue(rows.next()); assertEquals(history,rows.getString(1));
            }
        }
    }
    @Test void legacyHistoryExceptionDoesNotHideDeletedFormsPointersOrRemainingReleases() throws Exception {
        // 旧 pin 不存在不足以认定是清空后的历史残留：删除、当前指针、任何发布记录及绑定仍属于升级异常。
        for (String state : java.util.List.of(
                "INSERT INTO entity_form VALUES('legacy','entity',1,NULL)",
                "INSERT INTO entity_form VALUES('legacy','entity',0,'current-pointer')",
                "INSERT INTO entity_form VALUES('legacy','entity',0,NULL); INSERT INTO ui_config_release VALUES('inactive','FORM','legacy','INACTIVE','{}','unused',null,CURRENT_TIMESTAMP)",
                "INSERT INTO entity_form VALUES('legacy','entity',0,NULL); INSERT INTO ui_config_release VALUES('cleared-release','LIST','other','INACTIVE','{}','unused',null,CURRENT_TIMESTAMP)",
                "INSERT INTO entity_form VALUES('legacy','entity',0,NULL); INSERT INTO process_ui_release_binding VALUES('FORM','legacy','review','cleared-release',4)")) {
            try (Connection connection = database()) {
                source(connection,"good","{\"form\":{\"id\":\"good\"}}",false);
                for (String command : state.split(";")) connection.createStatement().execute(command);
                connection.createStatement().execute("INSERT INTO process_version_history VALUES('old-process','[{\"formId\":\"legacy\",\"formReleaseId\":\"cleared-release\"}]')");
                assertTrue(assertThrows(SQLException.class, () -> migrate(connection)).getMessage().contains("legacy"));
                assertEquals(0,scalar(connection,"SELECT COUNT(*) FROM entity_form_published"));
                assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM ui_config_release WHERE config_type='FORM' AND status='ACTIVE'"));
            }
        }
    }
    @Test void retiresPinnedBindingAndOldExpandedChildIndexWithoutRewritingHistory() throws Exception {
        try (Connection connection = database()) {
            source(connection,"parent","{\"form\":{\"id\":\"parent\"}}",false);
            connection.createStatement().execute("INSERT INTO process_ui_release_binding VALUES('FORM','parent','task','old-parent',7)");
            connection.createStatement().execute("INSERT INTO process_ui_release_binding VALUES('FORM','unreferenced-old-child','task#sub:hash','old-child',3)");
            migrate(connection);
            assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM process_ui_release_binding"));
            assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM process_ui_release_binding WHERE config_id='parent' AND pinned_release_id='' AND pinned_release_version=0"));
        }
    }
    @Test void ignoresInactiveLegacyReferencesAndAllowsManualNavigationCycles() throws Exception {
        try (Connection connection = database()) {
            source(connection,"a","{\"form\":{\"id\":\"a\"},\"legacyProps\":{\"childFormId\":\"missing-old\"},\"viewCompositions\":[{\"config\":{\"presentation\":{\"position\":\"DRAWER\"},\"target\":{\"contentType\":\"FORM\",\"contentId\":\"b\"}}}]}",false);
            source(connection,"b","{\"form\":{\"id\":\"b\"},\"viewCompositions\":[{\"config\":{\"presentation\":{\"position\":\"DRAWER\"},\"target\":{\"contentType\":\"FORM\",\"contentId\":\"a\"}}}]}",false);
            migrate(connection);
            assertEquals(2,scalar(connection,"SELECT COUNT(*) FROM entity_form_published"));
        }
    }
    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE entity_form(id VARCHAR PRIMARY KEY,entity_id VARCHAR,deleted INT,active_release_id VARCHAR)");
            sql.execute("CREATE TABLE ui_config_release(id VARCHAR,config_type VARCHAR,config_id VARCHAR,status VARCHAR,snapshot_document CLOB,content_hash VARCHAR,published_by VARCHAR,published_at TIMESTAMP)");
            sql.execute("CREATE TABLE process_ui_release_binding(config_type VARCHAR,config_id VARCHAR,node_id VARCHAR,pinned_release_id VARCHAR,pinned_release_version INT)");
            sql.execute("CREATE TABLE embed_view_release(default_form_id VARCHAR)");
            sql.execute("CREATE TABLE process_version_history(id VARCHAR,node_forms_snapshot CLOB)");
            sql.execute("CREATE TABLE ui_config_hotfix_target(config_type VARCHAR,status VARCHAR)");
            sql.execute("CREATE TABLE sys_menu(perm VARCHAR,deleted INT)");
            String schema = new String(getClass().getResourceAsStream("/db/migration/V112__entity_form_current_publication.sql").readAllBytes(), StandardCharsets.UTF_8);
            for (String command : schema.split(";")) if (!command.isBlank()) sql.execute(command);
        }
        return connection;
    }
    private void source(Connection c,String id,String document,boolean corrupt) throws Exception {
        var snapshot = codec.readObject(document, "test");
        @SuppressWarnings("unchecked") var form = (java.util.Map<String,Object>) snapshot.get("form");
        form.putIfAbsent("entityId", "entity");
        String canonical = codec.canonicalize(new ObjectMapper().writeValueAsString(snapshot),"test");
        try (PreparedStatement row = c.prepareStatement("INSERT INTO entity_form VALUES(?,'entity',0,'old')")) { row.setString(1,id); row.executeUpdate(); }
        try (PreparedStatement row = c.prepareStatement("INSERT INTO ui_config_release VALUES(?,'FORM',?,'ACTIVE',?,?,null,CURRENT_TIMESTAMP)")) {
            row.setString(1,"old-"+id); row.setString(2,id); row.setString(3,canonical);
            row.setString(4,corrupt ? "broken" : HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)))); row.executeUpdate();
        }
    }
    private int scalar(Connection c,String sql) throws Exception { try (var s=c.createStatement();var r=s.executeQuery(sql)) { r.next();return r.getInt(1); } }
    private void migrate(Connection c) throws Exception {
        new V113__migrate_forms_to_current_publication().migrate(new Context() {
            @Override public Configuration getConfiguration() { return null; }
            @Override public Connection getConnection() { return c; }
        });
    }
}
