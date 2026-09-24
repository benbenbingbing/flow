package com.workflow.migration;

import db.migration.V105__task_inbox_read_model;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** 只在显式开启时创建独立 MySQL 临时库；绝不迁移或清理开发业务库。 */
@EnabledIfEnvironmentVariable(named="FLOW_INBOX_MYSQL_TEST", matches="true")
class TaskInboxMigrationTest {
    private Connection admin, connection;
    private String database, url, user, password;

    @BeforeEach void createDatabase() throws Exception {
        String base = System.getenv("FLOW_INBOX_MYSQL_ADMIN_URL");
        if (base == null || !base.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/\\?.*"))
            throw new IllegalArgumentException("仅允许显式指定本机 MySQL 管理连接且 URL 不得包含数据库名");
        user = System.getenv("FLOW_INBOX_MYSQL_USER"); password = System.getenv("FLOW_INBOX_MYSQL_PASSWORD");
        admin = DriverManager.getConnection(base,user,password);
        database = "flow_inbox_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var statement=admin.createStatement()) { statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
        url=base.replace("/?", "/"+database+"?"); connection=DriverManager.getConnection(url,user,password);
    }
    @AfterEach void cleanup() throws Exception {
        if(connection!=null) connection.close();
        if(admin!=null) {
            if(database!=null) try(var statement=admin.createStatement()) { statement.execute("DROP DATABASE " + database); }
            admin.close();
        }
    }
    @Test
    @EnabledIfEnvironmentVariable(named="FLOW_INBOX_FULL_MIGRATION_TEST", matches="true")
    void freshDatabaseMigratesThrough105AndValidates() {
        var flyway=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").load();
        flyway.migrate(); flyway.validate();
        assertEquals("105",flyway.info().current().getVersion().getVersion());
    }
    @Test void flywayExecutesAndValidates105AfterPredecessorSchema() {
        // 独立夹具模拟既有表；完整历史链另有显式测试，不能为了该测试改写不可变迁移。
        var predecessor = new org.flywaydb.core.api.migration.JavaMigration() {
            public Integer getChecksum() { return null; }
            public boolean canExecuteInTransaction() { return false; }
            public org.flywaydb.core.api.MigrationVersion getVersion() { return org.flywaydb.core.api.MigrationVersion.fromVersion("104"); }
            public String getDescription() { return "task inbox predecessor fixture"; }
            public void migrate(Context context) throws Exception { legacyTables(); }
        };
        var flyway = Flyway.configure().dataSource(url,user,password).locations("classpath:inbox-fixture-no-sql")
                .javaMigrations(predecessor, new V105__task_inbox_read_model()).load();
        flyway.migrate(); flyway.validate();
        assertEquals("105",flyway.info().current().getVersion().getVersion());
    }
    @ParameterizedTest @ValueSource(strings={"user","group"})
    void unknownLegacyRelationStopsBeforeAnyDdl(String kind) throws Exception {
        legacyTables();
        execute("INSERT INTO process_task_candidate_"+kind+" (id,task_instance_id,"+(kind.equals("user")?"user_id":"group_code")+") VALUES ('legacy','unknown','actor')");
        assertThrows(SQLException.class, () -> new V105__task_inbox_read_model().migrate(context()));
        assertEquals(2, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'process_task_candidate_%' AND column_name='task_instance_id'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='process_task' AND column_name='start_user_id'"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM process_task_candidate_"+kind));
    }
    @Test void partialDdlCanResumeAndDuplicateCandidatesRemainRejected() throws Exception {
        legacyTables();
        execute("ALTER TABLE process_task_candidate_user CHANGE COLUMN task_instance_id process_task_id BIGINT NOT NULL");
        var migration=new V105__task_inbox_read_model(); migration.migrate(context()); migration.migrate(context());
        assertEquals(2,scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'process_task_candidate_%' AND column_name='process_task_id' AND data_type='bigint'"));
        execute("INSERT INTO process_task_candidate_user (id,process_task_id,user_id) VALUES ('one',123,'alice')");
        assertThrows(SQLException.class, () -> execute("INSERT INTO process_task_candidate_user (id,process_task_id,user_id) VALUES ('two',123,'alice')"));
        assertEquals(2, scalar("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name IN ('idx_task_candidate_user_lookup','idx_task_candidate_group_lookup')"));
        assertEquals(8,scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='process_task' AND column_name IN ('start_user_id','business_name','business_code','business_data_name','business_current_task_name','business_status','inbox_summary_ready','inbox_identity_ready')"));
    }
    private Context context() { return new Context() {
        public Connection getConnection(){return connection;}
        public Configuration getConfiguration(){return Flyway.configure().dataSource(url,user,password);}
    }; }
    /** 从不可变 V001 提取真实旧表，避免人工重写夹具掩盖唯一索引迁移问题。 */
    private void legacyTables() throws Exception {
        String sql;
        try(var input=getClass().getResourceAsStream("/db/migration/V001__business_schema.sql")) { sql=new String(input.readAllBytes(),StandardCharsets.UTF_8); }
        for(String table : new String[]{"process_task","process_task_candidate_user","process_task_candidate_group"}) {
            var matcher=Pattern.compile("CREATE TABLE `"+table+"` \\([\\s\\S]*?;",Pattern.MULTILINE).matcher(sql);
            assertTrue(matcher.find()); execute(matcher.group());
        }
    }
    private void execute(String sql) throws SQLException { try(var statement=connection.createStatement()){statement.execute(sql);} }
    private int scalar(String sql) throws SQLException { try(var statement=connection.createStatement(); var rows=statement.executeQuery(sql)){rows.next();return rows.getInt(1);} }
}
