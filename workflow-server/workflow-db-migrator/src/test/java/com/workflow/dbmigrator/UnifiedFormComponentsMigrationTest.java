package com.workflow.dbmigrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import db.migration.V117__unify_form_components;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 JDBC 回归：统一配置后必须能删除旧字段，且当前发布指针与文档保持一致。 */
class UnifiedFormComponentsMigrationTest {
    @Test
    void convertsDraftsAndPublishedSnapshotsAndDropsRetiredIdentity() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE ui_extension_definition(id VARCHAR(50),extension_type VARCHAR(20),extension_key VARCHAR(50),version INT,deleted INT)");
                statement.execute("INSERT INTO ui_extension_definition VALUES ('f','FIELD','custom',2,0),('n','NODE','custom',2,0),('x','NODE','summary',1,0)");
                statement.execute("CREATE TABLE entity_form_node(id VARCHAR(50),node_type VARCHAR(20),component_name VARCHAR(50),component_version INT,snapshot_version INT,props_document CLOB)");
                statement.execute("INSERT INTO entity_form_node VALUES ('a','FIELD','custom',2,1,'{\"componentType\":\"input\",\"componentExtensionType\":\"FIELD\",\"componentProps\":{\"title\":\"客户\"}}')");
                statement.execute("INSERT INTO entity_form_node VALUES ('b','FIELD','missing',1,1,'{\"componentType\":\"select_multiple\",\"componentProps\":{\"disabled\":true,\"privateOption\":123}}')");
                statement.execute("CREATE TABLE entity_form_published(form_id VARCHAR(50),snapshot_document CLOB,content_hash VARCHAR(64))");
                statement.execute("CREATE TABLE entity_form(id VARCHAR(50),active_release_id VARCHAR(64))");
                statement.execute("CREATE TABLE ui_config_release(id VARCHAR(50),snapshot_document CLOB,content_hash VARCHAR(64))");
                statement.execute("CREATE TABLE ui_view_composition(id VARCHAR(50),config_document CLOB)");
                statement.execute("INSERT INTO ui_config_release VALUES ('parent','{\"target\":{\"contentHash\":\"old\"},\"specialHandling\":{\"customComponent\":{\"extensionType\":\"NODE\",\"name\":\"summary\",\"version\":1}}}','parent-old')");
                statement.execute("INSERT INTO ui_config_release VALUES ('ancestor','{\"target\":{\"contentHash\":\"parent-old\"}}','ancestor-old')");
                statement.execute("INSERT INTO ui_view_composition VALUES ('view','{\"target\":{\"contentId\":\"form\"},\"specialHandling\":{\"customComponent\":{\"extensionType\":\"NODE\",\"name\":\"summary\",\"version\":1}}}')");
                statement.execute("INSERT INTO entity_form VALUES ('form','old')");
            }
            // 使用结构化 props，避免测试夹具的字符串转义影响迁移行为。
            String document = new ObjectMapper().writeValueAsString(Map.of("nodes", java.util.List.of(Map.of(
                    "nodeType", "FIELD", "componentName", "summary", "componentVersion", 1, "snapshotVersion", 1,
                    "props", Map.of("componentType", "input", "componentExtensionType", "FIELD"))),
                    "business", Map.of("componentName", "业务数据保持原值")));
            try (var insert = connection.prepareStatement("INSERT INTO entity_form_published VALUES ('form',?,'old')")) {
                insert.setString(1, document); insert.executeUpdate();
            }
            migrate(connection);
            assertEquals("custom", new ObjectMapper().readTree(scalar(connection,
                    "SELECT props_document FROM entity_form_node WHERE id='a'")).get("componentType").asText());
            assertFalse(scalar(connection, "SELECT props_document FROM entity_form_node WHERE id='a'").contains("componentExtensionType"));
            assertTrue(scalar(connection, "SELECT props_document FROM entity_form_node WHERE id='b'").contains("select_multiple"));
            assertFalse(new ObjectMapper().readTree(scalar(connection,
                    "SELECT props_document FROM entity_form_node WHERE id='b'")).has("componentProps"));
            assertEquals("客户", new ObjectMapper().readTree(scalar(connection,
                    "SELECT props_document FROM entity_form_node WHERE id='a'")).get("componentProps").get("title").asText());
            assertNull(scalar(connection, "SELECT component_version FROM entity_form_node WHERE id='b'"));
            assertEquals("2", scalar(connection, "SELECT COUNT(*) FROM ui_extension_definition WHERE extension_type='FIELD'"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM ui_extension_definition WHERE extension_type='NODE'"));
            assertThrows(java.sql.SQLException.class, () -> scalar(connection, "SELECT component_name FROM entity_form_node"));
            var published = new ObjectMapper().readTree(scalar(connection, "SELECT snapshot_document FROM entity_form_published"));
            assertFalse(published.get("nodes").get(0).has("componentName"));
            assertEquals("summary", published.get("nodes").get(0).get("props").get("componentType").asText());
            assertEquals("业务数据保持原值", published.get("business").get("componentName").asText());
            String hash = scalar(connection, "SELECT content_hash FROM entity_form_published");
            assertNotEquals("old", hash);
            assertEquals(hash, scalar(connection, "SELECT active_release_id FROM entity_form"));
            var parent = new ObjectMapper().readTree(scalar(connection, "SELECT snapshot_document FROM ui_config_release WHERE id='parent'"));
            assertEquals(hash, parent.get("target").get("contentHash").asText());
            assertFalse(parent.get("specialHandling").has("customComponent"));
            var ancestor = new ObjectMapper().readTree(scalar(connection, "SELECT snapshot_document FROM ui_config_release WHERE id='ancestor'"));
            assertEquals(scalar(connection, "SELECT content_hash FROM ui_config_release WHERE id='parent'"), ancestor.get("target").get("contentHash").asText());
            var view = new ObjectMapper().readTree(scalar(connection, "SELECT config_document FROM ui_view_composition"));
            assertEquals("form", view.get("target").get("contentId").asText());
            assertFalse(view.get("specialHandling").has("customComponent"));
            // 失败重试时已转换的文档和目录不能生成第二份配置。
            migrate(connection);
            assertEquals(hash, scalar(connection, "SELECT content_hash FROM entity_form_published"));
        }
    }

    private void migrate(Connection connection) throws Exception {
        new V117__unify_form_components().migrate(new Context() {
            public Configuration getConfiguration() { return null; }
            public Connection getConnection() { return connection; }
        });
    }

    private String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next()); return rows.getString(1);
        }
    }
}
