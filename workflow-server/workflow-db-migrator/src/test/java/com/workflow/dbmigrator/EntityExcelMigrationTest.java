package com.workflow.dbmigrator;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 对实际 V115 脚本执行建表及唯一约束验证，隔离内存库不会触及用户业务库。 */
class EntityExcelMigrationTest {
    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:excel_" + UUID.randomUUID() + ";MODE=MySQL");
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/db/migration/V115__entity_excel_configuration_and_jobs.sql")), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
        return connection;
    }
    @Test void hasOneDefaultRowAndUniqueSchemeCodeWithinEntity() throws Exception {
        try (Connection connection = database(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO entity_excel_settings(entity_id,draft_json,updated_at) VALUES('e1','{}',CURRENT_TIMESTAMP)");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO entity_excel_settings(entity_id,draft_json,updated_at) VALUES('e1','{}',CURRENT_TIMESTAMP)"));
            statement.execute("INSERT INTO entity_excel_scheme(id,entity_id,scheme_code,name,draft_json,created_at,updated_at) VALUES('s1','e1','basic','基础','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO entity_excel_scheme(id,entity_id,scheme_code,name,draft_json,created_at,updated_at) VALUES('s2','e1','basic','另一套','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
            statement.execute("INSERT INTO entity_excel_scheme(id,entity_id,scheme_code,name,draft_json,created_at,updated_at) VALUES('s2','e2','basic','其他实体','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            statement.execute("INSERT INTO entity_excel_scheme_published(scheme_id,document_json,content_hash,published_at) VALUES('s1','{}','hash',CURRENT_TIMESTAMP)");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO entity_excel_scheme_published(scheme_id,document_json,content_hash,published_at) VALUES('s1','{}','changed',CURRENT_TIMESTAMP)"));
        }
    }
    @Test void jobIdempotencyIsScopedByUserOriginAndRequestId() throws Exception {
        try (Connection connection = database()) {
            job(connection, "job1", "alice", "button1", "request1");
            assertThrows(SQLException.class, () -> job(connection, "job2", "alice", "button1", "request1"));
            job(connection, "job2", "bob", "button1", "request1");
            job(connection, "job3", "alice", "button2", "request1");
            job(connection, "job4", "alice", "button1", "request2");
        }
    }
    @Test void originalCoordinatesRemainUniquePerJobAndSheet() throws Exception {
        try (Connection connection = database(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO entity_excel_job_row(id,job_id,sheet_no,excel_row_number) VALUES('r1','job1',0,3)");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO entity_excel_job_row(id,job_id,sheet_no,excel_row_number) VALUES('r2','job1',0,3)"));
            statement.execute("INSERT INTO entity_excel_job_row(id,job_id,sheet_no,excel_row_number) VALUES('r2','job1',1,3)");
        }
    }
    private void job(Connection connection, String id, String user, String origin, String request) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO entity_excel_job(id,entity_id,entity_code,operation,status,user_id,origin_key,request_id,payload_hash,config_json,created_at,updated_at) VALUES(?,'e1','employee','IMPORT','RUNNING',?,?,?,'hash','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            statement.setString(1,id); statement.setString(2,user); statement.setString(3,origin); statement.setString(4,request);
            statement.executeUpdate();
        }
    }
}
