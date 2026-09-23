package com.workflow.entity.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigMapper;
import com.workflow.integration.database.api.*;
import com.workflow.core.database.*;
import com.workflow.config.database.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 用真实 MyBatis 结果映射与默认方法，对照原 MySQL JSON 表达式；表名转为随机测试表，隔离业务数据。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlVersionConfigProjectionDatabaseTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void javaProjectionMatchesLegacyMysqlJsonSemantics() throws Exception {
        try (var fixture = new Fixture(); var session = fixture.sessions.openSession(true)) {
            var mapper = session.getMapper(EntityVersionConfigMapper.class);
            List<String> documents = Arrays.asList(null, "", " ", "{", "{} garbage", "{} {}", "null", "true", "123", "[]", "\"中文\"",
                    "{\"enabled\":true,\"schemaVersion\":99,\"status\":\"PUBLISHED\",\"migrationState\":\"DONE\",\"activeReleaseId\":\"r1\",\"activeReleaseVersion\":8,\"nested\":{\"status\":\"keep\"}}",
                    "[{\"status\":\"keep\"}]", "{\"schemaVersion\":1,\"schemaVersion\":8}",
                    "1e999");
            List<String> runtimeIds = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                String current = i % 3 == 0 ? null : " {\"source\":\"current\"} ";
                Integer version = i % 2 == 0 ? null : 2;
                fixture.insert("c" + i, current, "r" + i, "c" + i, documents.get(i), version);
                String expected = fixture.jdbc.queryForObject("""
                        SELECT CASE WHEN JSON_VALID(?) = 1
                          THEN JSON_REMOVE(JSON_SET(CAST(? AS JSON), '$.schemaVersion', COALESCE(?, 1)),
                            '$.status', '$.migrationState', '$.activeReleaseId', '$.activeReleaseVersion')
                          ELSE ? END
                        """, String.class, documents.get(i), documents.get(i), version, current);
                var actual = mapper.findByEntityCode("c" + i);
                assertNotNull(actual, "document index=" + i);
                assertEquals(7, actual.getRevision());
                assertEquals("entity-c" + i, actual.getEntityId());
                assertEquals(Boolean.FALSE, actual.getEnabled());
                if (expected == null) assertNull(actual.getConfigDocument(), "document index=" + i);
                else {
                    assertEquals(json.readTree(expected), json.readTree(actual.getConfigDocument()), "document index=" + i);
                    runtimeIds.add("c" + i);
                }
            }
            assertEquals(documents.size(), mapper.findAllForManagementList().size());
            assertEquals(new HashSet<>(runtimeIds), new HashSet<>(mapper.findAllCurrent().stream().map(r -> r.getId()).toList()));
        }
    }

    @Test
    void overdeepPublishedDocumentRemainsAnErrorInsteadOfSilentlyUsingOlderConfig() throws Exception {
        try (var fixture = new Fixture(); var session = fixture.sessions.openSession(true)) {
            String document = "[".repeat(101) + "0" + "]".repeat(101);
            fixture.insert("deep", "{}", "deep-release", "deep", document, 2);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> fixture.jdbc.queryForObject("SELECT JSON_VALID(?)", Integer.class, document));
            assertThrows(IllegalArgumentException.class,
                    () -> session.getMapper(EntityVersionConfigMapper.class).findByEntityCode("deep"));
        }
    }

    @Test
    void anotherConfigsReleaseCannotOverrideCurrentAndDeletedRowsStayHidden() throws Exception {
        try (var fixture = new Fixture(); var session = fixture.sessions.openSession(true)) {
            fixture.insert("own", "{\"source\":\"current\"}", "foreign", "another-config", "{\"source\":\"foreign\"}", 2);
            fixture.insert("deleted", "{}", "deleted-release", "deleted", "{}", 2);
            fixture.jdbc.update("UPDATE " + fixture.configTable + " SET deleted=1 WHERE id='deleted'");
            var mapper = session.getMapper(EntityVersionConfigMapper.class);
            assertEquals("current", json.readTree(mapper.findByEntityCode("own").getConfigDocument()).get("source").textValue());
            assertNull(mapper.findByEntityCode("deleted"));
            assertEquals(1, mapper.findAllForManagementList().size());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final InitializedDriverDataSource source = new InitializedDriverDataSource(System.getenv("FLOW_MYSQL_TEST_URL"), System.getenv("FLOW_MYSQL_TEST_USER"),
                System.getenv("FLOW_MYSQL_TEST_PASSWORD"), null, DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.MYSQL));
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        final String configTable = "biz_vcfg_" + suffix, releaseTable = "biz_vrel_" + suffix;
        final SqlSessionFactory sessions;
        Fixture() {
            jdbc.execute("CREATE TABLE " + configTable + " (id VARCHAR(64) PRIMARY KEY, entity_id VARCHAR(64), entity_code VARCHAR(100), enabled TINYINT,"
                    + " config_document LONGTEXT, active_release_id VARCHAR(64), revision INT, create_by VARCHAR(64), create_time DATETIME,"
                    + " update_by VARCHAR(64), update_time DATETIME, deleted INT, UNIQUE KEY uk_code (entity_code,deleted))"
                    + " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            jdbc.execute("CREATE TABLE " + releaseTable + " (id VARCHAR(64) PRIMARY KEY, config_id VARCHAR(64), config_document LONGTEXT, contract_version INT)"
                    + " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            var isolated = new DelegatingDataSource(source) {
                @Override public Connection getConnection() throws SQLException {
                    Connection actual = super.getConnection();
                    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql) {
                            args[0] = sql.replaceAll("\\bentity_version_config_release\\b", releaseTable)
                                    .replaceAll("\\bentity_version_config\\b", configTable);
                        }
                        try { return method.invoke(actual, args); }
                        catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
                    });
                }
            };
            var configuration = new Configuration(new Environment("mysql-isolated", new JdbcTransactionFactory(), isolated));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(EntityVersionConfigMapper.class);
            sessions = new SqlSessionFactoryBuilder().build(configuration);
        }
        void insert(String id, String current, String release, String owner, String document, Integer version) {
            jdbc.update("INSERT INTO " + configTable + " (id,entity_id,entity_code,enabled,config_document,active_release_id,revision,deleted) VALUES (?,?,?,0,?,?,7,0)",
                    id, "entity-" + id, id, current, release);
            jdbc.update("INSERT INTO " + releaseTable + " (id,config_id,config_document,contract_version) VALUES (?,?,?,?)", release, owner, document, version);
        }
        @Override public void close() {
            try { jdbc.execute("DROP TABLE IF EXISTS " + releaseTable); }
            finally { jdbc.execute("DROP TABLE IF EXISTS " + configTable); }
        }
    }
}
