package com.workflow.database;

import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentitySourceSnapshotMapper;
import com.workflow.core.database.jdbc.InitializedDriverDataSource;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import com.workflow.port.system.model.identity.IdentityObjectType;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlIdentitySourceSnapshotDatabaseTest {
    /** 覆盖 Flyway 建表与空库脚本的来源键定义；会话临时表隔离同名业务表。 */
    @ParameterizedTest
    @ValueSource(strings = {"ascii COLLATE ascii_bin", "utf8mb4 COLLATE utf8mb4_unicode_ci"})
    void readsSourceSnapshotsAcrossKeyCollations(String bindingCharset) throws Exception {
        var source = new InitializedDriverDataSource(System.getenv("FLOW_MYSQL_TEST_URL"),
                System.getenv("FLOW_MYSQL_TEST_USER"), System.getenv("FLOW_MYSQL_TEST_PASSWORD"), null,
                DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.MYSQL));
        try (var connection = source.getConnection()) {
            var isolated = new SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(isolated);
            jdbc.execute("""
                    CREATE TEMPORARY TABLE sys_identity_source_binding (
                        source_key CHAR(64) CHARACTER SET %s PRIMARY KEY,
                        source_code VARCHAR(64), external_id VARCHAR(255), source_version BIGINT,
                        object_type VARCHAR(20) NOT NULL, local_id VARCHAR(64) NOT NULL,
                        UNIQUE KEY uk_identity_source_local (object_type, local_id)
                    ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """.formatted(bindingCharset));
            jdbc.execute("""
                    CREATE TEMPORARY TABLE sys_identity_source_snapshot (
                        source_key CHAR(64) PRIMARY KEY, source_version BIGINT, payload_hash CHAR(64),
                        raw_json LONGTEXT, raw_hash CHAR(64), raw_bytes INTEGER, is_current SMALLINT,
                        recorded_at DATETIME(6), recorded_by VARCHAR(64)
                    ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
            jdbc.update("""
                    INSERT INTO sys_identity_source_binding VALUES
                    (SHA2('user', 256), 'erp', 'ext-user', 2, 'USER', '42'),
                    (SHA2('org', 256), 'erp', 'ext-org', 3, 'ORGANIZATION', '42')
                    """);
            String raw = "{\"机构\":\"研发🙂\", \"id\":9007199254740993}";
            jdbc.update("INSERT INTO sys_identity_source_snapshot (source_key, source_version, raw_json) VALUES (SHA2('user', 256), 1, ?)", raw);

            var config = new Configuration(new Environment("source-snapshot-mysql", new JdbcTransactionFactory(), isolated));
            config.setDatabaseId(DatabaseVendor.MYSQL.name());
            config.addMapper(IdentitySourceSnapshotMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
                var mapper = session.getMapper(IdentitySourceSnapshotMapper.class);
                var user = mapper.read(IdentityObjectType.USER, "42");
                assertEquals("ext-user", user.getExternalId());
                assertEquals(2L, user.getCurrentSourceVersion());
                assertEquals(1L, user.getSourceVersion());
                assertEquals(raw, user.getRawJson());

                var organization = mapper.read(IdentityObjectType.ORGANIZATION, "42");
                assertEquals("ext-org", organization.getExternalId());
                assertEquals(3L, organization.getCurrentSourceVersion());
                assertNull(organization.getRawJson());
                assertNull(mapper.read(IdentityObjectType.USER, "missing"));
            }
        }
    }
}
