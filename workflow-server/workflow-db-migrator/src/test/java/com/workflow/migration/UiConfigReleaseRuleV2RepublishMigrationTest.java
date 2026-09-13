package com.workflow.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V086 ACTIVE 列表发布快照 v1 到不可变 v2 后继版本的 MySQL 集成测试。 */
class UiConfigReleaseRuleV2RepublishMigrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper CANONICAL_JSON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private static MySQLContainer<?> container;
    private static String url;
    private static String user;
    private static String password;

    @BeforeAll
    static void connect() {
        String externalUrl = System.getProperty(
                "flow.ui.release.mysql.url");
        if (externalUrl != null) {
            if (!externalUrl.matches(
                    "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):\\d+/"
                            + "flow_ui_release_v086_test"
                            + "(?:_[a-zA-Z0-9]+)?(?:\\?.*)?")) {
                throw new IllegalArgumentException(
                        "外部迁移测试只允许使用专用 "
                                + "flow_ui_release_v086_test 数据库");
            }
            url = externalUrl;
            user = System.getProperty(
                    "flow.ui.release.mysql.user", "root");
            password = System.getProperty(
                    "flow.ui.release.mysql.password", "");
            return;
        }
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "需要 Docker 运行 MySQL 迁移验证");
        container = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("flow_ui_release_v086_test");
        container.start();
        url = container.getJdbcUrl();
        user = container.getUsername();
        password = container.getPassword();
    }

    @AfterAll
    static void close() {
        if (container != null) {
            container.stop();
        }
    }

    /**
     * 只建立 V086 依赖的 V085 表形态，使测试聚焦发布版本派生和原子切换。
     */
    @BeforeEach
    void createPreviousVersionSchema() throws Exception {
        flyway().clean();
        execute("""
                CREATE TABLE entity_list_config (
                    id varchar(64) NOT NULL,
                    active_release_id varchar(64),
                    published_version int NOT NULL DEFAULT 0,
                    deleted tinyint NOT NULL DEFAULT 0,
                    update_time datetime DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE ui_config_release (
                    id varchar(64) NOT NULL,
                    config_type varchar(20) NOT NULL,
                    config_id varchar(64) NOT NULL,
                    version int NOT NULL,
                    snapshot_document longtext NOT NULL,
                    content_hash varchar(64) NOT NULL,
                    status varchar(20) NOT NULL DEFAULT 'INACTIVE',
                    active_slot tinyint GENERATED ALWAYS AS (
                        CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END
                    ) STORED,
                    description varchar(500),
                    published_by varchar(64),
                    published_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    release_mode varchar(20) NOT NULL DEFAULT 'STANDARD',
                    base_release_id varchar(64),
                    risk_level varchar(20) NOT NULL DEFAULT 'SAFE',
                    rollout_scope varchar(30),
                    patch_document longtext,
                    override_risk tinyint NOT NULL DEFAULT 0,
                    override_reason varchar(1000),
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_ui_config_release_version
                        (config_type,config_id,version),
                    UNIQUE KEY uk_ui_config_release_active
                        (config_type,config_id,active_slot)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COLLATE=utf8mb4_unicode_ci
                """);
    }

    @Test
    void createsImmutableV2SuccessorAndSwitchesActiveOwner()
            throws Exception {
        String legacySnapshot = canonical("""
                {
                  "schemaVersion": 1,
                  "configType": "LIST",
                  "list": {
                    "id": "legacy-config",
                    "toolbarConfig": [
                      {
                        "key": "batchDelete",
                        "availabilityRule": {
                          "version": 1,
                          "unavailableBehavior": "DISABLE",
                          "message": "  仅草稿可批量删除  ",
                          "root": {"type":"FIELD","field":"status"}
                        }
                      },
                      {
                        "key": "create",
                        "availabilityRule": {"version": 1}
                      },
                      {
                        "key": "v2-kept",
                        "availabilityRule": {
                          "version": 2,
                          "visibleWhen": null,
                          "enabledWhen": null,
                          "disabledMessage": ""
                        }
                      }
                    ],
                    "rowActionConfig": [
                      {
                        "key": "delete",
                        "availabilityRule": {
                          "unavailableBehavior": "HIDE",
                          "root": {"type":"RELATION","relation":"CREATOR"}
                        }
                      }
                    ]
                  }
                }
                """);
        String v2Snapshot = canonical("""
                {
                  "schemaVersion": 1,
                  "configType": "LIST",
                  "list": {
                    "id": "v2-config",
                    "toolbarConfig": [{
                      "key": "kept",
                      "availabilityRule": {
                        "version": 2,
                        "visibleWhen": null,
                        "enabledWhen": null,
                        "disabledMessage": ""
                      }
                    }],
                    "rowActionConfig": []
                  }
                }
                """);

        insertRelease(
                "legacy-release", "legacy-config", 4,
                legacySnapshot, "ACTIVE");
        insertRelease(
                "historical-release", "legacy-config", 6,
                legacySnapshot, "INACTIVE");
        insertConfig("legacy-config", "legacy-release", 4);
        insertRelease(
                "v2-release", "v2-config", 2,
                v2Snapshot, "ACTIVE");
        insertConfig("v2-config", "v2-release", 2);

        Flyway migration = flyway();
        migration.migrate();
        migration.validate();

        assertEquals(
                "086",
                migration.info().current().getVersion().getVersion());
        String successorId = scalar("""
                SELECT active_release_id
                  FROM entity_list_config
                 WHERE id='legacy-config'
                """);
        assertNotEquals("legacy-release", successorId);
        assertTrue(successorId.matches("[0-9a-f]{32}"));
        // 发布版本沿用应用层语义：从历史最大版本递增，不能复用回滚后的版本号。
        assertEquals("7", scalar("""
                SELECT published_version
                  FROM entity_list_config
                 WHERE id='legacy-config'
                """));
        assertEquals("INACTIVE", scalar("""
                SELECT status FROM ui_config_release
                 WHERE id='legacy-release'
                """));
        assertEquals("ACTIVE", scalar("""
                SELECT status FROM ui_config_release
                 WHERE id=?
                """, successorId));
        assertEquals("7", scalar("""
                SELECT version FROM ui_config_release
                 WHERE id=?
                """, successorId));

        // 不可变源快照和其哈希必须逐字保留；只有 status 发生生命周期切换。
        assertEquals(legacySnapshot, scalar("""
                SELECT snapshot_document FROM ui_config_release
                 WHERE id='legacy-release'
                """));
        assertEquals(sha256(legacySnapshot), scalar("""
                SELECT content_hash FROM ui_config_release
                 WHERE id='legacy-release'
                """));

        String migratedSnapshot = scalar("""
                SELECT snapshot_document FROM ui_config_release
                 WHERE id=?
                """, successorId);
        assertEquals(migratedSnapshot, canonical(migratedSnapshot));
        assertEquals(sha256(migratedSnapshot), scalar("""
                SELECT content_hash FROM ui_config_release
                 WHERE id=?
                """, successorId));
        assertNull(scalar("""
                SELECT published_by FROM ui_config_release
                 WHERE id=?
                """, successorId));
        assertEquals(0, legacyRuleCount(migratedSnapshot));

        Map<String, Object> migrated = object(migratedSnapshot);
        assertEquals(
                "legacy-config",
                ((Map<?, ?>) migrated.get("list")).get("id"));
        List<Map<String, Object>> toolbar = buttons(
                migrated, "toolbarConfig");
        List<Map<String, Object>> rows = buttons(
                migrated, "rowActionConfig");
        Map<String, Object> disabled = rule(toolbar.get(0));
        assertEquals(2, disabled.get("version"));
        assertNull(disabled.get("visibleWhen"));
        assertEquals(
                "FIELD",
                ruleNode(disabled, "enabledWhen").get("type"));
        assertEquals(
                "仅草稿可批量删除",
                disabled.get("disabledMessage"));
        assertFalse(toolbar.get(1).containsKey("availabilityRule"));
        assertEquals(2, rule(toolbar.get(2)).get("version"));
        Map<String, Object> hidden = rule(rows.get(0));
        assertEquals(2, hidden.get("version"));
        assertEquals(
                "RELATION",
                ruleNode(hidden, "visibleWhen").get("type"));
        assertNull(hidden.get("enabledWhen"));

        // 已经是 v2 的 ACTIVE 发布不能产生无意义的新版本。
        assertEquals("v2-release", scalar("""
                SELECT active_release_id FROM entity_list_config
                 WHERE id='v2-config'
                """));
        assertEquals("2", scalar("""
                SELECT published_version FROM entity_list_config
                 WHERE id='v2-config'
                """));
        assertEquals("1", scalar("""
                SELECT COUNT(*) FROM ui_config_release
                 WHERE config_type='LIST' AND config_id='v2-config'
                """));

        assertEquals(0, migration.migrate().migrationsExecuted);
        assertEquals("3", scalar("""
                SELECT COUNT(*) FROM ui_config_release
                 WHERE config_type='LIST' AND config_id='legacy-config'
                """));
    }

    private void insertConfig(
            String id,
            String activeReleaseId,
            int publishedVersion) throws Exception {
        execute("""
                INSERT INTO entity_list_config
                    (id,active_release_id,published_version)
                VALUES (?,?,?)
                """, id, activeReleaseId, publishedVersion);
    }

    private void insertRelease(
            String id,
            String configId,
            int version,
            String snapshot,
            String status) throws Exception {
        execute("""
                INSERT INTO ui_config_release
                    (id,config_type,config_id,version,
                     snapshot_document,content_hash,status,
                     description,published_by,release_mode,risk_level)
                VALUES (?, 'LIST', ?, ?, ?, ?, ?,
                        '原发布','user-1','STANDARD','SAFE')
                """, id, configId, version, snapshot,
                sha256(snapshot), status);
    }

    private int legacyRuleCount(String document) throws Exception {
        Map<String, Object> snapshot = object(document);
        int count = 0;
        for (String array : List.of("toolbarConfig", "rowActionConfig")) {
            for (Map<String, Object> button : buttons(snapshot, array)) {
                Object value = button.get("availabilityRule");
                if (!(value instanceof Map<?, ?> rule)) {
                    continue;
                }
                Object version = rule.get("version");
                if (version == null
                        || version instanceof Number number
                        && number.doubleValue() == 1D
                        || "1".equals(String.valueOf(version))) {
                    count++;
                }
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buttons(
            Map<String, Object> snapshot,
            String key) {
        Map<String, Object> list =
                (Map<String, Object>) snapshot.get("list");
        return (List<Map<String, Object>>) list.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rule(Map<String, Object> button) {
        return (Map<String, Object>) button.get("availabilityRule");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ruleNode(
            Map<String, Object> rule,
            String key) {
        return (Map<String, Object>) rule.get(key);
    }

    private Map<String, Object> object(String document)
            throws Exception {
        return JSON.readValue(
                document,
                new TypeReference<Map<String, Object>>() { });
    }

    private String canonical(String document) throws Exception {
        Object value = JSON.readValue(document, Object.class);
        return CANONICAL_JSON.writeValueAsString(value);
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void execute(String sql, Object... values)
            throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private String scalar(String sql, Object... values)
            throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private void bind(PreparedStatement statement, Object[] values)
            throws Exception {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(url, user, password);
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(url, user, password)
                .cleanDisabled(false)
                .placeholderReplacement(false)
                .baselineOnMigrate(true)
                .baselineVersion("085")
                .target("086")
                .locations("classpath:db/migration")
                .load();
    }
}
