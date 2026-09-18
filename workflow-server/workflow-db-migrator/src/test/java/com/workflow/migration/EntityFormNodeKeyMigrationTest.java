package com.workflow.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 在真实 MySQL 上验证 V093 移除 entity_form_node 旧唯一键并保留普通索引。
 * 旧唯一键 (form_id, node_key, deleted) 只允许同一节点编码保留一条删除记录，
 * 同一子表单删除后重新添加并再次删除会触发唯一冲突；唯一性已由
 * (form_id, active_node_key) 唯一键保证。
 * 只运行本次迁移，历史全量迁移重放由独立集成检查承担。
 * 可通过 formNodeKeyTestJdbcUrl 指向预建的空白回环测试库；不读取业务数据库配置，不清理外部库。
 */
class EntityFormNodeKeyMigrationTest {
    private static MySQLContainer<?> mysql;
    private static String url;
    private static String username;
    private static String password;

    @BeforeAll
    static void setup() {
        url = System.getProperty("formNodeKeyTestJdbcUrl", System.getenv("FORM_NODE_KEY_TEST_JDBC_URL"));
        if (url != null) {
            if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/workflow_form_node_key_test_[a-z0-9_]+(\\?.*)?")) {
                throw new IllegalArgumentException("仅允许显式指定回环地址的 workflow_form_node_key_test_ 测试库");
            }
            username = System.getProperty("formNodeKeyTestUsername", System.getenv().getOrDefault("FORM_NODE_KEY_TEST_USERNAME", "root"));
            password = System.getProperty("formNodeKeyTestPassword", System.getenv().getOrDefault("FORM_NODE_KEY_TEST_PASSWORD", ""));
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "真实 MySQL 测试需要 Docker 或显式空白测试库");
            mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("workflow_form_node_key_test_container");
            mysql.start();
            url = mysql.getJdbcUrl(); username = mysql.getUsername(); password = mysql.getPassword();
        }
    }

    @AfterAll
    static void cleanup() {
        if (mysql != null) mysql.stop();
    }

    @Test
    void legacyUniqueKeyDroppedAndRepeatedSoftDeleteAllowed() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration")
                .placeholderReplacement(false).cleanDisabled(true).load();
        flyway.migrate();
        flyway.validate();
        assertEquals(0, flyway.info().pending().length);

        // 旧唯一键已移除，普通索引保留，活动节点唯一键不受影响。
        assertEquals(0, count("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'entity_form_node' AND index_name = 'uk_entity_form_node_key'"));
        assertEquals(2, count("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'entity_form_node' AND index_name = 'idx_entity_form_node_form_key' AND non_unique = 1"));
        assertEquals(2, count("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'entity_form_node' AND index_name = 'uk_entity_form_node_active_key' AND non_unique = 0"));

        // 同一节点编码的软删除、重建、再软删除循环不再触发唯一冲突。
        insertNode("form-a", "node-a", "childForm");
        softDelete("node-a");
        insertNode("form-a", "node-b", "childForm");
        softDelete("node-b");
        assertEquals(2, count("SELECT COUNT(*) FROM entity_form_node WHERE form_id = 'form-a' AND node_key = 'childForm' AND deleted = 1"));
        assertEquals(0, count("SELECT COUNT(*) FROM entity_form_node WHERE form_id = 'form-a' AND node_key = 'childForm' AND deleted = 0"));

        // 活动节点编码唯一性仍由唯一键保证。
        insertNode("form-a", "node-c", "childForm");
        assertThrows(SQLException.class, () -> insertNode("form-a", "node-d", "childForm"));
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    private static void insertNode(String formId, String nodeId, String nodeKey) throws SQLException {
        execute("INSERT INTO entity_form_node (id, form_id, node_key, node_type, binding_type, order_key, revision, deleted) "
                + "VALUES ('" + nodeId + "', '" + formId + "', '" + nodeKey + "', 'SUB_FORM', 'NONE', 1000000, 1, 0)");
    }

    private static void softDelete(String nodeId) throws SQLException {
        execute("UPDATE entity_form_node SET deleted = 1, revision = revision + 1 WHERE id = '" + nodeId + "'");
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int count(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
