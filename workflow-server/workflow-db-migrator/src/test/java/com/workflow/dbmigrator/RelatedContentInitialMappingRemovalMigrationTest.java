package com.workflow.dbmigrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import db.migration.V120__remove_related_content_relation_actions;
import db.migration.V121__remove_related_content_initial_mappings;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 验证初值映射退役后只剩参数传递，数据库清理不会损坏发布依赖或主从组件。 */
class RelatedContentInitialMappingRemovalMigrationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final JsonDocumentCodec codec = new JsonDocumentCodec(json);

    @Test
    void removesSettingsAfterPreviousMigrationAndPreservesParametersAndCompositionEditors() throws Exception {
        try (Connection connection = database()) {
            Map<String, Object> config = configWithInitialMappings();
            Map<String, Object> nodes = Map.of("nodeType", "FIELD", "propsDocument",
                    "{\"componentType\":\"sub_form\",\"componentProps\":{\"parameterMappings\":[{\"parameter\":\"parentId\"}]}}");
            Map<String, Object> snapshot = snapshot(config);
            snapshot.put("nodes", List.of(nodes));
            snapshot.put("business", Map.of("actionSettings", Map.of("create", "保留业务值")));
            insertDraft(connection, "draft", config);
            insertForm(connection, snapshot);
            // 真实升级路径依次执行两次迁移；V120 必须保留旧初值，V121 才整体删除设置。
            new V120__remove_related_content_relation_actions().migrate(context(connection));
            assertTrue(draft(connection).has("actionSettings"));
            migrate(connection);

            Map<String, Object> expectedConfig = new LinkedHashMap<>(config);
            expectedConfig.remove("actionSettings");
            assertEquals(json.valueToTree(expectedConfig), draft(connection));
            JsonNode published = json.readTree(scalar(connection, "SELECT snapshot_document FROM entity_form_published"));
            assertEquals(json.valueToTree(expectedConfig), published.at("/viewCompositions/0/config"));
            assertEquals(json.valueToTree(List.of(nodes)), published.get("nodes"));
            assertEquals(json.valueToTree(snapshot.get("business")), published.get("business"));
            assertEquals("5", scalar(connection, "SELECT revision FROM ui_view_composition"));
            assertEquals("4", scalar(connection, "SELECT revision FROM entity_form"));
            assertNull(scalar(connection, "SELECT draft_hash FROM entity_form"));
            String document = scalar(connection, "SELECT snapshot_document FROM entity_form_published");
            String hash = scalar(connection, "SELECT content_hash FROM entity_form_published");
            assertEquals(hash(document), hash);
            assertEquals(hash, scalar(connection, "SELECT active_release_id FROM entity_form"));
            migrate(connection);
            assertEquals(document, scalar(connection, "SELECT snapshot_document FROM entity_form_published"));
            assertEquals("5", scalar(connection, "SELECT revision FROM ui_view_composition"));
            assertEquals("4", scalar(connection, "SELECT revision FROM entity_form"));
        }
    }

    @Test
    void removesEmptySettingsAndDoesNotGenerateParameterMappings() throws Exception {
        try (Connection connection = database()) {
            Map<String, Object> config = configWithInitialMappings();
            config.remove("parameterMappings");
            config.put("actionSettings", Map.of());
            insertDraft(connection, "draft", config);

            migrate(connection);

            JsonNode migrated = draft(connection);
            assertFalse(migrated.has("actionSettings"));
            assertFalse(migrated.has("parameterMappings"), "不把退役配置自动转换成新的运行规则");
            assertEquals(json.valueToTree(List.of("VIEW", "CREATE", "EDIT")), migrated.get("actions"));
        }
    }

    @Test
    void updatesPinnedListHashesThroughParentPublicationsAndDrafts() throws Exception {
        try (Connection connection = database()) {
            String leaf = insertList(connection, "z-leaf", snapshot(configWithInitialMappings()));
            String parent = insertList(connection, "a-parent", snapshot(listReference("z-leaf", leaf)));
            insertForm(connection, snapshot(listReference("a-parent", parent)));
            insertDraft(connection, "draft", listReference("a-parent", parent));

            migrate(connection);

            String newLeaf = scalar(connection, "SELECT content_hash FROM ui_config_release WHERE id='z-leaf'");
            String newParent = scalar(connection, "SELECT content_hash FROM ui_config_release WHERE id='a-parent'");
            assertNotEquals(leaf, newLeaf);
            assertNotEquals(parent, newParent);
            JsonNode parentSnapshot = json.readTree(scalar(connection, "SELECT snapshot_document FROM ui_config_release WHERE id='a-parent'"));
            JsonNode formSnapshot = json.readTree(scalar(connection, "SELECT snapshot_document FROM entity_form_published"));
            assertEquals(newLeaf, parentSnapshot.at("/viewCompositions/0/config/target/contentHash").asText());
            assertEquals(newParent, formSnapshot.at("/viewCompositions/0/config/target/contentHash").asText());
            assertEquals(newParent, draft(connection).at("/target/contentHash").asText());
            assertEquals("a-parent", draft(connection).at("/target/releaseId").asText());
            for (String id : List.of("z-leaf", "a-parent")) {
                assertEquals(hash(scalar(connection, "SELECT snapshot_document FROM ui_config_release WHERE id='" + id + "'")),
                        scalar(connection, "SELECT content_hash FROM ui_config_release WHERE id='" + id + "'"));
            }
        }
    }

    @Test
    void failsBeforeWritingWhenPublishedHashIsInvalid() throws Exception {
        try (Connection connection = database()) {
            insertDraft(connection, "draft", configWithInitialMappings());
            insertForm(connection, snapshot(configWithInitialMappings()));
            execute(connection, "UPDATE entity_form_published SET content_hash=?", "0".repeat(64));
            String before = scalar(connection, "SELECT config_document FROM ui_view_composition");

            assertThrows(IllegalStateException.class, () -> migrate(connection));

            assertEquals(before, scalar(connection, "SELECT config_document FROM ui_view_composition"));
            assertEquals("4", scalar(connection, "SELECT revision FROM ui_view_composition"));
            assertTrue(connection.getAutoCommit());
        }
    }

    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ui_view_composition(id VARCHAR(64) PRIMARY KEY, owner_type VARCHAR(20), owner_id VARCHAR(64), config_document CLOB, revision INT, update_time TIMESTAMP)");
            statement.execute("CREATE TABLE entity_form(id VARCHAR(64) PRIMARY KEY, revision INT, draft_hash VARCHAR(64), active_release_id VARCHAR(64))");
            statement.execute("CREATE TABLE entity_list_config(id VARCHAR(64) PRIMARY KEY, revision INT, draft_hash VARCHAR(64))");
            statement.execute("CREATE TABLE entity_form_published(form_id VARCHAR(64) PRIMARY KEY, snapshot_document CLOB, content_hash VARCHAR(64))");
            statement.execute("CREATE TABLE ui_config_release(id VARCHAR(64) PRIMARY KEY, config_id VARCHAR(64), config_type VARCHAR(20), snapshot_document CLOB, content_hash VARCHAR(64))");
            statement.execute("INSERT INTO entity_form VALUES ('form',3,'cached-draft',NULL)");
        }
        return connection;
    }

    private Map<String, Object> configWithInitialMappings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actions", List.of("VIEW", "CREATE", "EDIT"));
        result.put("target", Map.of("contentType", "FORM", "contentId", "child"));
        result.put("relation", Map.of("type", "ENTITY_RELATION", "relationCode", "items"));
        result.put("actionSettings", Map.of("create", Map.of("initialMappings", List.of(
                Map.of("sourceField", "id", "targetField", "ownerId")))));
        result.put("parameterMappings", List.of(Map.of("parameter", "projectId", "sourceType", "FIELD", "sourceField", "id")));
        result.put("specialHandling", Map.of("mode", "CUSTOM_COMPONENT", "customComponent", Map.of("name", "custom",
                "props", Map.of("actionSettings", Map.of("create", "组件自有设置")))));
        return result;
    }

    private Map<String, Object> listReference(String id, String hash) {
        Map<String, Object> config = configWithInitialMappings();
        config.remove("actionSettings");
        config.put("target", Map.of("contentType", "LIST", "contentId", id,
                "releaseId", id, "releaseVersion", 1, "contentHash", hash));
        return config;
    }

    private Map<String, Object> snapshot(Map<String, Object> config) {
        return new LinkedHashMap<>(Map.of("viewCompositions", List.of(Map.of("compositionKey", "related", "config", config))));
    }

    private void insertDraft(Connection connection, String id, Map<String, Object> config) throws Exception {
        execute(connection, "INSERT INTO ui_view_composition VALUES (?,'FORM','form',?,4,CURRENT_TIMESTAMP)", id, canonical(config));
    }

    private void insertForm(Connection connection, Map<String, Object> snapshot) throws Exception {
        String document = canonical(snapshot);
        execute(connection, "INSERT INTO entity_form_published VALUES ('form',?,?)", document, hash(document));
        execute(connection, "UPDATE entity_form SET active_release_id=?", hash(document));
    }

    private String insertList(Connection connection, String id, Map<String, Object> snapshot) throws Exception {
        String document = canonical(snapshot);
        execute(connection, "INSERT INTO entity_list_config VALUES (?,1,'cached')", id);
        execute(connection, "INSERT INTO ui_config_release VALUES (?,?,'LIST',?,?)", id, id, document, hash(document));
        return hash(document);
    }

    private JsonNode draft(Connection connection) throws Exception {
        return json.readTree(scalar(connection, "SELECT config_document FROM ui_view_composition"));
    }

    private String canonical(Object value) throws Exception {
        return codec.canonicalize(json.writeValueAsString(value), "migration test");
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void migrate(Connection connection) throws Exception {
        new V121__remove_related_content_initial_mappings().migrate(context(connection));
    }

    private Context context(Connection connection) {
        return new Context() {
            public Configuration getConfiguration() { return null; }
            public Connection getConnection() { return connection; }
        };
    }

    private void execute(Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            statement.executeUpdate();
        }
    }

    private String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }
}
