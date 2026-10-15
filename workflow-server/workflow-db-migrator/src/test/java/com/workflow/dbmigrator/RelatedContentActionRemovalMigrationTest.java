package com.workflow.dbmigrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import db.migration.V120__remove_related_content_relation_actions;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 JDBC 验证一次性清理旧动作，以及发布摘要、依赖引用和事务的一致性。 */
class RelatedContentActionRemovalMigrationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final JsonDocumentCodec codec = new JsonDocumentCodec(json);

    @Test
    void removesRetiredDraftSettingsWithoutTouchingEntityFieldsOrButtonAppearance() throws Exception {
        try (Connection connection = database()) {
            formOwner(connection, "owner");
            Map<String, Object> oldConfig = retiredConfig();
            insertDraft(connection, "draft", "FORM", "owner", oldConfig);

            migrate(connection);

            JsonNode config = json.readTree(scalar(connection,
                    "SELECT config_document FROM ui_view_composition WHERE id='draft'"));
            assertEquals(json.valueToTree(List.of("CREATE", "VIEW", "EDIT")), config.get("actions"));
            assertFalse(config.path("actionSettings").has("select"));
            assertFalse(config.path("actionSettings").path("create").has("associateAfterCreate"));
            assertEquals(json.valueToTree(oldConfig).path("actionSettings").path("create").get("initialMappings"),
                    config.path("actionSettings").path("create").get("initialMappings"));
            assertFalse(config.path("presentation").path("actionBar").path("buttons").has("link"));
            assertEquals("LINK", config.path("presentation").path("actionBar").path("buttons")
                    .path("open").path("buttonAppearance").asText());
            assertEquals(json.valueToTree(oldConfig.get("relation")), config.get("relation"));
            assertEquals(json.valueToTree(oldConfig.get("specialHandling")), config.get("specialHandling"));
            assertEquals("5", scalar(connection, "SELECT revision FROM ui_view_composition WHERE id='draft'"));
            assertEquals("4", scalar(connection, "SELECT revision FROM entity_form WHERE id='owner'"));
            assertNull(scalar(connection, "SELECT draft_hash FROM entity_form WHERE id='owner'"));

            Map<String, List<List<String>>> once = databaseState(connection);
            migrate(connection);
            assertEquals(once, databaseState(connection), "迁移重试不能再次增加修订号或改写摘要");
        }
    }

    @Test
    void clearsEmptyLegacySettingsEvenForViewOnlyForms() throws Exception {
        try (Connection connection = database()) {
            formOwner(connection, "view-only");
            Map<String, Object> config = cleanConfig();
            config.put("actionSettings", Map.of("select", Map.of(),
                    "create", Map.of("associateAfterCreate", true, "initialMappings", List.of())));
            insertDraft(connection, "view-only-draft", "FORM", "view-only", config);
            insertFormPublication(connection, "view-only", snapshot(config));

            migrate(connection);

            JsonNode draft = json.readTree(scalar(connection,
                    "SELECT config_document FROM ui_view_composition WHERE id='view-only-draft'"));
            JsonNode published = composition(publication(connection, "entity_form_published", "form_id", "view-only"));
            for (JsonNode cleaned : List.of(draft, published)) {
                assertEquals(json.valueToTree(List.of("VIEW")), cleaned.get("actions"));
                assertFalse(cleaned.path("actionSettings").has("select"));
                assertFalse(cleaned.path("actionSettings").path("create").has("associateAfterCreate"));
            }
        }
    }

    @Test
    void defaultsRemovedOnlyActionsToViewAndIncrementsAnOwnerOnlyOnce() throws Exception {
        try (Connection connection = database()) {
            listOwner(connection, "list-owner");
            for (String id : List.of("one", "two")) {
                Map<String, Object> config = retiredConfig();
                config.put("actions", List.of("SELECT", "LINK", "UNLINK", "SAVE_WITH_FORM"));
                insertDraft(connection, id, "LIST", "list-owner", config);
            }

            migrate(connection);

            for (String id : List.of("one", "two")) {
                JsonNode config = json.readTree(scalar(connection,
                        "SELECT config_document FROM ui_view_composition WHERE id='" + id + "'"));
                assertEquals(json.valueToTree(List.of("VIEW")), config.get("actions"));
                assertEquals("5", scalar(connection,
                        "SELECT revision FROM ui_view_composition WHERE id='" + id + "'"));
            }
            assertEquals("8", scalar(connection, "SELECT revision FROM entity_list_config WHERE id='list-owner'"));
            assertNull(scalar(connection, "SELECT draft_hash FROM entity_list_config WHERE id='list-owner'"));
        }
    }

    @Test
    void removesRetiredStandardActionBindingsAndDisablesEmptyInterfaceMode() throws Exception {
        try (Connection connection = database()) {
            formOwner(connection, "bindings");
            Map<String, Object> config = cleanConfig();
            config.put("specialHandling", Map.of("mode", "INTERFACE_SERVICE", "failurePolicy", "ERROR",
                    "actionServices", List.of(
                            Map.of("actionKey", "SELECT", "extensionId", "old-select"),
                            Map.of("actionKey", " link ", "extensionId", "old-link"),
                            Map.of("actionKey", "UNLINK", "extensionId", "old-unlink"),
                            Map.of("actionKey", "SAVE_WITH_FORM", "extensionId", "old-save"))));
            insertDraft(connection, "bindings-draft", "FORM", "bindings", config);
            insertFormPublication(connection, "bindings", snapshot(config));

            migrate(connection);

            JsonNode draft = json.readTree(scalar(connection,
                    "SELECT config_document FROM ui_view_composition WHERE id='bindings-draft'"));
            JsonNode published = composition(publication(connection, "entity_form_published", "form_id", "bindings"));
            for (JsonNode cleaned : List.of(draft, published)) {
                assertEquals("NONE", cleaned.path("specialHandling").path("mode").asText());
                assertEquals(json.valueToTree(List.of()), cleaned.path("specialHandling").get("actionServices"));
                assertEquals("ERROR", cleaned.path("specialHandling").path("failurePolicy").asText());
            }
            verifiedHash(connection, "entity_form_published", "form_id", "bindings");
            Map<String, List<List<String>>> once = databaseState(connection);
            migrate(connection);
            assertEquals(once, databaseState(connection));
        }
    }

    @Test
    void keepsCustomComponentWhenRetiredBindingsWereTheOnlyInterfaceCapability() throws Exception {
        try (Connection connection = database()) {
            formOwner(connection, "component");
            Map<String, Object> component = Map.of("name", "summary", "version", 2,
                    "props", Map.of("actionKey", "LINK", "select", "业务展示参数"));
            Map<String, Object> config = cleanConfig();
            config.put("specialHandling", Map.of("mode", "BOTH", "failurePolicy", "PLACEHOLDER",
                    "customComponent", component, "interfaceService", Map.of("extensionId", " "),
                    "actionServices", List.of(Map.of("actionKey", "LINK", "extensionId", "old-link"))));
            insertDraft(connection, "component-draft", "FORM", "component", config);
            insertFormPublication(connection, "component", snapshot(config));

            migrate(connection);

            JsonNode draft = json.readTree(scalar(connection,
                    "SELECT config_document FROM ui_view_composition WHERE id='component-draft'"));
            JsonNode published = composition(publication(connection, "entity_form_published", "form_id", "component"));
            for (JsonNode cleaned : List.of(draft, published)) {
                JsonNode special = cleaned.path("specialHandling");
                assertEquals("CUSTOM_COMPONENT", special.path("mode").asText());
                assertEquals(json.valueToTree(component), special.get("customComponent"));
                assertEquals(json.valueToTree(List.of()), special.get("actionServices"));
                assertEquals("PLACEHOLDER", special.path("failurePolicy").asText());
            }
        }
    }

    @Test
    void preservesCustomActionAndReadInterfaceBindingsWithTheirInterfaceMode() throws Exception {
        // 分别覆盖仅自定义动作、仅取数接口，以及两者同时存在，不能把有效接口模式降为 NONE。
        for (String retained : List.of("CUSTOM", "READ", "BOTH")) {
            try (Connection connection = database()) {
                formOwner(connection, "extensions");
                Map<String, Object> custom = Map.of("actionKey", "calculateRisk", "extensionId", "risk-service",
                        "inputMappings", List.of(Map.of("source", "source.select", "target", "category")));
                Map<String, Object> read = Map.of("extensionId", "read-service", "operationCode", "SELECT",
                        "outputMappings", List.of(Map.of("source", "result", "target", "records")));
                List<Map<String, Object>> services = new ArrayList<>();
                services.add(Map.of("actionKey", "UNLINK", "extensionId", "old-unlink"));
                if (!retained.equals("READ")) services.add(custom);
                Map<String, Object> special = new LinkedHashMap<>();
                special.put("mode", "INTERFACE_SERVICE");
                special.put("failurePolicy", "ERROR");
                special.put("actionServices", services);
                if (!retained.equals("CUSTOM")) special.put("interfaceService", read);
                Map<String, Object> config = cleanConfig();
                config.put("relation", Map.of("type", "SAME_RECORD"));
                config.put("specialHandling", special);
                insertDraft(connection, "extensions-draft", "FORM", "extensions", config);
                insertFormPublication(connection, "extensions", snapshot(config));

                migrate(connection);

                Map<String, Object> expected = new LinkedHashMap<>(special);
                expected.put("actionServices", retained.equals("READ") ? List.of() : List.of(custom));
                JsonNode draft = json.readTree(scalar(connection,
                        "SELECT config_document FROM ui_view_composition WHERE id='extensions-draft'"));
                JsonNode published = composition(publication(connection, "entity_form_published", "form_id", "extensions"));
                assertEquals(json.valueToTree(expected), draft.get("specialHandling"), retained);
                assertEquals(json.valueToTree(expected), published.get("specialHandling"), retained);
            }
        }
    }

    @Test
    void recomputesNestedListHashesFormPointerAndDraftTargetReferences() throws Exception {
        try (Connection connection = database()) {
            listOwner(connection, "leaf-owner");
            listOwner(connection, "middle-owner");
            formOwner(connection, "form-owner");
            formOwner(connection, "draft-owner");
            String leafHash = insertRelease(connection, "z-leaf", "leaf-owner", snapshot(retiredConfig()));
            String middleHash = insertRelease(connection, "a-middle", "middle-owner",
                    snapshot(listReference("z-leaf", "leaf-owner", leafHash)));
            Map<String, Object> formSnapshot = snapshot(listReference("a-middle", "middle-owner", middleHash));
            // 同名动作/设置出现在业务数据或实体字段中，不能因为递归搜索而误清除。
            Map<String, Object> business = Map.of("actions", List.of("SELECT", "LINK"),
                    "actionSettings", Map.of("select", Map.of("mode", "MULTIPLE")),
                    "viewCompositions", List.of(Map.of("config", retiredConfig())));
            formSnapshot.put("business", business);
            formSnapshot.put("legacyFields", List.of(Map.of("fieldCode", "select", "fieldType", "SELECT")));
            String formHash = insertFormPublication(connection, "form-owner", formSnapshot);
            insertDraft(connection, "dependent-draft", "FORM", "draft-owner",
                    listReference("a-middle", "middle-owner", middleHash));

            migrate(connection);

            String newLeafHash = verifiedHash(connection, "ui_config_release", "id", "z-leaf");
            String newMiddleHash = verifiedHash(connection, "ui_config_release", "id", "a-middle");
            String newFormHash = verifiedHash(connection, "entity_form_published", "form_id", "form-owner");
            assertNotEquals(leafHash, newLeafHash);
            assertNotEquals(middleHash, newMiddleHash);
            assertNotEquals(formHash, newFormHash);
            JsonNode middle = publication(connection, "ui_config_release", "id", "a-middle");
            assertEquals(newLeafHash, composition(middle).path("target").path("contentHash").asText());
            assertEquals("z-leaf", composition(middle).path("target").path("releaseId").asText());
            assertEquals(1, composition(middle).path("target").path("releaseVersion").asInt());
            JsonNode form = publication(connection, "entity_form_published", "form_id", "form-owner");
            assertEquals(newMiddleHash, composition(form).path("target").path("contentHash").asText());
            assertEquals(newFormHash, scalar(connection, "SELECT active_release_id FROM entity_form WHERE id='form-owner'"));
            assertEquals(json.valueToTree(business), form.get("business"));
            assertEquals(json.valueToTree(formSnapshot.get("legacyFields")), form.get("legacyFields"));
            JsonNode draft = json.readTree(scalar(connection,
                    "SELECT config_document FROM ui_view_composition WHERE id='dependent-draft'"));
            assertEquals(newMiddleHash, draft.path("target").path("contentHash").asText());
            assertEquals("5", scalar(connection, "SELECT revision FROM ui_view_composition WHERE id='dependent-draft'"));
            assertEquals("4", scalar(connection, "SELECT revision FROM entity_form WHERE id='draft-owner'"));
            assertEquals("3", scalar(connection, "SELECT revision FROM entity_form WHERE id='form-owner'"));
            assertEquals("7", scalar(connection, "SELECT revision FROM entity_list_config WHERE id='middle-owner'"));
            assertNull(scalar(connection, "SELECT draft_hash FROM entity_form WHERE id='form-owner'"));
            assertNull(scalar(connection, "SELECT draft_hash FROM entity_list_config WHERE id='leaf-owner'"));
            assertNull(scalar(connection, "SELECT draft_hash FROM entity_list_config WHERE id='middle-owner'"));
            Map<String, List<List<String>>> once = databaseState(connection);
            migrate(connection);
            assertEquals(once, databaseState(connection));
        }
    }

    @Test
    void leavesAlreadyCleanConfigurationsAndTheirMetadataUntouched() throws Exception {
        try (Connection connection = database()) {
            formOwner(connection, "clean");
            Map<String, Object> config = cleanConfig();
            insertDraft(connection, "draft", "FORM", "clean", config);
            insertFormPublication(connection, "clean", snapshot(config));
            Map<String, List<List<String>>> before = databaseState(connection);

            migrate(connection);

            assertEquals(before, databaseState(connection));
        }
    }

    @Test
    void rejectsWrongPublishedHashesBeforeWritingAnyDraftOrPublication() throws Exception {
        try (Connection connection = database()) {
            formOwner(connection, "good");
            insertDraft(connection, "dirty", "FORM", "good", retiredConfig());
            insertFormPublication(connection, "good", snapshot(retiredConfig()));
            listOwner(connection, "bad");
            insertRelease(connection, "bad-release", "bad", snapshot(retiredConfig()));
            execute(connection, "UPDATE ui_config_release SET content_hash=? WHERE id=?", "0".repeat(64), "bad-release");
            Map<String, List<List<String>>> before = databaseState(connection);

            assertThrows(Exception.class, () -> migrate(connection));

            assertEquals(before, databaseState(connection));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void rejectsMalformedDraftOrSnapshotBeforeAnyWrites() throws Exception {
        for (boolean malformedDraft : List.of(false, true)) {
            try (Connection connection = database()) {
                formOwner(connection, "owner");
                insertDraft(connection, "dirty", "FORM", "owner", retiredConfig());
                insertFormPublication(connection, "owner", snapshot(retiredConfig()));
                if (malformedDraft) {
                    execute(connection, "UPDATE ui_view_composition SET config_document=? WHERE id=?", "{broken", "dirty");
                } else {
                    execute(connection, "UPDATE entity_form_published SET snapshot_document=? WHERE form_id=?", "{broken", "owner");
                }
                Map<String, List<List<String>>> before = databaseState(connection);

                assertThrows(Exception.class, () -> migrate(connection));

                assertEquals(before, databaseState(connection));
            }
        }
    }

    @Test
    void rollsBackEarlierWritesWhenALaterDatabaseUpdateFails() throws Exception {
        try (Connection connection = database()) {
            formOwner(connection, "owner");
            insertDraft(connection, "dirty", "FORM", "owner", retiredConfig());
            insertFormPublication(connection, "owner", snapshot(retiredConfig()));
            Map<String, List<List<String>>> before = databaseState(connection);
            AtomicInteger writes = new AtomicInteger();
            Connection failing = failSecondPreparedUpdate(connection, writes);

            assertThrows(Exception.class, () -> migrate(failing));

            assertEquals(2, writes.get(), "必须在第一条真实写入成功之后模拟失败");
            assertEquals(before, databaseState(connection));
            assertTrue(connection.getAutoCommit(), "迁移管理自己的事务时必须恢复连接状态");
        }
    }

    /** JDBC 代理只在第二次写入注入异常，底层仍是真实 H2 事务，用于验证部分更新不会泄漏。 */
    private Connection failSecondPreparedUpdate(Connection delegate, AtomicInteger writes) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(delegate, args);
                        if (!method.getName().equals("prepareStatement")) return result;
                        PreparedStatement statement = (PreparedStatement) result;
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                new Class<?>[]{PreparedStatement.class}, (statementProxy, statementMethod, statementArgs) -> {
                                    if (statementMethod.getName().equals("executeUpdate") && writes.incrementAndGet() == 2) {
                                        throw new SQLException("simulated second update failure");
                                    }
                                    try { return statementMethod.invoke(statement, statementArgs); }
                                    catch (InvocationTargetException error) { throw error.getCause(); }
                                });
                    } catch (InvocationTargetException error) { throw error.getCause(); }
                });
    }

    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ui_view_composition(id VARCHAR(64) PRIMARY KEY, owner_type VARCHAR(20), owner_id VARCHAR(64), config_document CLOB, revision INT, update_time TIMESTAMP)");
            statement.execute("CREATE TABLE entity_form(id VARCHAR(64) PRIMARY KEY, revision INT, draft_hash VARCHAR(64), active_release_id VARCHAR(64))");
            statement.execute("CREATE TABLE entity_list_config(id VARCHAR(64) PRIMARY KEY, revision INT, draft_hash VARCHAR(64))");
            statement.execute("CREATE TABLE entity_form_published(form_id VARCHAR(64) PRIMARY KEY, snapshot_document CLOB, content_hash VARCHAR(64))");
            statement.execute("CREATE TABLE ui_config_release(id VARCHAR(64) PRIMARY KEY, config_id VARCHAR(64), config_type VARCHAR(20), snapshot_document CLOB, content_hash VARCHAR(64))");
        }
        return connection;
    }

    private void formOwner(Connection connection, String id) throws Exception {
        execute(connection, "INSERT INTO entity_form VALUES (?,3,'cached-draft',NULL)", id);
    }

    private void listOwner(Connection connection, String id) throws Exception {
        execute(connection, "INSERT INTO entity_list_config VALUES (?,7,'cached-draft')", id);
    }

    private void insertDraft(Connection connection, String id, String ownerType, String ownerId,
                             Map<String, Object> config) throws Exception {
        execute(connection, "INSERT INTO ui_view_composition VALUES (?,?,?,?,4,TIMESTAMP '2026-01-01 00:00:00')",
                id, ownerType, ownerId, canonical(config));
    }

    private String insertRelease(Connection connection, String id, String ownerId, Map<String, Object> snapshot) throws Exception {
        String document = canonical(snapshot);
        String hash = hash(document);
        execute(connection, "INSERT INTO ui_config_release VALUES (?,?,'LIST',?,?)", id, ownerId, document, hash);
        return hash;
    }

    private String insertFormPublication(Connection connection, String formId, Map<String, Object> snapshot) throws Exception {
        String document = canonical(snapshot);
        String hash = hash(document);
        execute(connection, "INSERT INTO entity_form_published VALUES (?,?,?)", formId, document, hash);
        execute(connection, "UPDATE entity_form SET active_release_id=? WHERE id=?", hash, formId);
        return hash;
    }

    private Map<String, Object> retiredConfig() {
        Map<String, Object> config = cleanConfig();
        config.put("actions", List.of("CREATE", "SELECT", "VIEW", "LINK", "EDIT", "UNLINK", "SAVE_WITH_FORM"));
        config.put("actionSettings", Map.of("select", Map.of("mode", "SINGLE", "result", "FILL_FIELDS", "mappings", List.of()),
                "create", Map.of("associateAfterCreate", false,
                        "initialMappings", List.of(Map.of("sourceField", "id", "targetField", "ownerId")))));
        config.put("presentation", Map.of("position", "INLINE", "loadMode", "ON_DEMAND", "actionBar", Map.of("buttons",
                Map.of("link", Map.of("label", "建立关联"), "open", Map.of("buttonAppearance", "LINK", "icon", "Link")))));
        return config;
    }

    private Map<String, Object> cleanConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("schemaVersion", 1);
        config.put("actions", List.of("VIEW"));
        config.put("relation", Map.of("type", "ENTITY_RELATION", "relationCode", "project_tasks", "direction", "FORWARD"));
        config.put("target", Map.of("contentType", "FORM", "contentId", "child"));
        config.put("actionSettings", Map.of("create", Map.of("initialMappings", List.of())));
        config.put("specialHandling", Map.of("mode", "CUSTOM_COMPONENT", "failurePolicy", "ERROR",
                "customComponent", Map.of("name", "custom", "props", Map.of("actionSettings", Map.of("select", "business value")))));
        return config;
    }

    private Map<String, Object> listReference(String releaseId, String contentId, String hash) {
        Map<String, Object> config = cleanConfig();
        config.put("target", Map.of("contentType", "LIST", "contentId", contentId,
                "releaseId", releaseId, "releaseVersion", 1, "contentHash", hash));
        return config;
    }

    private Map<String, Object> snapshot(Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewCompositions", List.of(Map.of("compositionKey", "related", "config", config)));
        return result;
    }

    private JsonNode composition(JsonNode snapshot) {
        return snapshot.path("viewCompositions").get(0).path("config");
    }

    private JsonNode publication(Connection connection, String table, String key, String id) throws Exception {
        return json.readTree(scalar(connection, "SELECT snapshot_document FROM " + table + " WHERE " + key + "='" + id + "'"));
    }

    private String verifiedHash(Connection connection, String table, String key, String id) throws Exception {
        String document = scalar(connection, "SELECT snapshot_document FROM " + table + " WHERE " + key + "='" + id + "'");
        String storedHash = scalar(connection, "SELECT content_hash FROM " + table + " WHERE " + key + "='" + id + "'");
        assertEquals(hash(codec.canonicalize(document, "migration test")), storedHash);
        return storedHash;
    }

    private String canonical(Object value) throws Exception {
        return codec.canonicalize(json.writeValueAsString(value), "migration test");
    }

    private String hash(String document) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8)));
    }

    private void migrate(Connection connection) throws Exception {
        new V120__remove_related_content_relation_actions().migrate(new Context() {
            public Configuration getConfiguration() { return null; }
            public Connection getConnection() { return connection; }
        });
    }

    private void execute(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    /** 比较所有业务表和元数据，不能只验证报错而漏掉已经发生的部分写入。 */
    private Map<String, List<List<String>>> databaseState(Connection connection) throws Exception {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        for (String table : List.of("ui_view_composition", "entity_form", "entity_list_config", "entity_form_published", "ui_config_release")) {
            List<List<String>> records = new ArrayList<>();
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
                while (rows.next()) {
                    List<String> values = new ArrayList<>();
                    for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) values.add(rows.getString(column));
                    records.add(values);
                }
            }
            result.put(table, records);
        }
        return result;
    }
}
