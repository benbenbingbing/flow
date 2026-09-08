package com.workflow.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.*;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import org.flywaydb.core.api.migration.Context;
import db.migration.V080__remove_compatibility_configuration_tables;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 可使用独立的本机测试库，也可在 CI 中使用 MySQL 容器；绝不清理项目数据库。 */
class CompatibilityTableRemovalMigrationTest {
    private static MySQLContainer<?> container;
    private static String url, user, password;
    private static final boolean SHADOW = Boolean.getBoolean("flow.compat.mysql.shadow");
    private static final String PREFIX = "__v080_test_" + UUID.randomUUID().toString().substring(0, 6) + "_";
    private static final List<String> TABLES = List.of("entity_form", "entity_form_field", "entity_form_node",
            "entity_field", "entity_definition", "entity_version_config", "entity_version_scenario",
            "entity_version_step", "entity_change_target_binding", "entity_version_config_release",
            "entity_mutation_policy_config", "entity_mutation_policy_release");
    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void connect() {
        url = System.getProperty("flow.compat.mysql.url");
        if (url == null) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
            container = new MySQLContainer<>("mysql:8.4").withDatabaseName("flow_compat_test");
            container.start();
            url = container.getJdbcUrl(); user = container.getUsername(); password = container.getPassword();
        } else {
            if (!SHADOW && !url.matches("jdbc:mysql://[^/]+/flow_compat_test(?:_[a-zA-Z0-9]+)?(?:\\?.*)?")) {
                throw new IllegalArgumentException("仅允许在 flow_compat_test 前缀的独立测试库运行");
            }
            user = System.getenv().getOrDefault(SHADOW ? "SCHEMA_DB_USERNAME" : "DB_USERNAME", "root");
            password = System.getenv().getOrDefault(SHADOW ? "SCHEMA_DB_PASSWORD" : "DB_PASSWORD", "");
        }
    }
    @AfterAll
    static void close() { if (container != null) container.stop(); }
    @BeforeEach
    void baseline() throws Exception {
        if (SHADOW) {
            // 仅克隆表结构；所有测试 SQL 都经同一映射，只能读写随机前缀的测试表。
            try (Connection connection = realConnection(); Statement statement = connection.createStatement()) {
                for (String table : TABLES) statement.execute("CREATE TABLE `" + PREFIX + table + "` LIKE `" + table + "`");
            }
        } else {
            flyway("079", null).clean();
            flyway("079", null).migrate();
        }
    }
    @AfterEach
    void removeShadowTables() throws Exception {
        if (!SHADOW) return;
        try (Connection connection = realConnection(); Statement statement = connection.createStatement()) {
            for (String table : TABLES) statement.execute("DROP TABLE IF EXISTS `" + PREFIX + table + "`");
        }
    }

    @Test
    void freshDatabaseDropsAllFourTablesAndCanValidateAndMigrateAgain() throws Exception {
        if (SHADOW) { migrate(); assertRemoved(); return; }
        Flyway current = flyway(null, null);
        current.migrate();
        assertRemoved();
        current.validate();
        assertEquals(0, current.migrate().migrationsExecuted);
        assertEquals("080", current.info().current().getVersion().getVersion());
    }

    @Test
    void convertsFieldOnlyFormAndPreservesExplicitNodeClears() throws Exception {
        sql("INSERT INTO entity_form_field(id,form_id,field_id,field_code,field_name,field_label,field_type,is_required,placeholder,component_props,validation_rules) VALUES "
                + "('old-1','form-1','1','amount','金额','金额','DECIMAL',1,'旧提示','{\"precision\":2}','[{\"min\":1}]'),"
                + "('old-2','form-2','2','items','明细','明细','SUB_FORM',1,'旧提示','{\"childEntityId\":\"child\"}','[]')");
        sql("INSERT INTO entity_form_node(id,form_id,node_key,node_type,props_document,rules_document) VALUES ('node-1','form-1','amount','FIELD','{\"fieldCode\":\"amount\",\"required\":false,\"placeholder\":null}','{\"validation\":null}')");
        migrate();

        Map<String,Object> props = document("SELECT props_document FROM entity_form_node WHERE id='node-1'");
        assertEquals(false, props.get("required"));
        assertNull(props.get("placeholder"));
        assertEquals(2, ((Map<?,?>)props.get("componentProps")).get("precision"));
        assertNull(document("SELECT rules_document FROM entity_form_node WHERE id='node-1'").get("validation"));
        assertEquals("SUB_FORM", scalar("SELECT node_type FROM entity_form_node WHERE id='old-2'"));
        assertEquals(true, document("SELECT props_document FROM entity_form_node WHERE id='old-2'").get("required"));
        assertRemoved();
    }

    @Test
    void separatesDraftRulesAndPublishedRuntimeWithoutOverwritingNativeDraft() throws Exception {
        sql("INSERT INTO entity_version_config(id,entity_id,entity_code,enabled,active_release_id) VALUES ('version-1','1','asset',1,'old-release')");
        sql("INSERT INTO entity_version_scenario(id,config_id,scenario_code,scenario_name) VALUES ('scenario-1','version-1','UPDATE','更新')");
        sql("INSERT INTO entity_version_step(id,config_id,scenario_id,step_type,step_name,config_document) VALUES ('step-1','version-1','scenario-1','BUILT_IN_RULE','草稿校验','{}')");
        sql("INSERT INTO entity_change_target_binding(id,config_id,binding_code,binding_name,source_entity_code,target_entity_code,resolver_type,mapping_document) VALUES ('target-1','version-1','APPLY','变更','request','asset','FIELD','{\"amount\":\"requestedAmount\"}')");
        String published = "{\"enabled\":true,\"scenarios\":[],\"steps\":[{\"stepName\":\"运行校验\",\"stepType\":\"BUILT_IN_RULE\"}],\"targetBindings\":[]}";
        execute("INSERT INTO entity_version_config_release(id,config_id,version,config_document) VALUES ('old-release','version-1',1,?)", published);
        String nativeDraft = "{\"schemaVersion\":1,\"enabled\":false,\"steps\":[{\"stepName\":\"未发布的新校验\"}]}";
        execute("INSERT INTO entity_mutation_policy_config(id,entity_id,entity_code,draft_document) VALUES ('native-1','1','asset',?)", nativeDraft);
        migrate();

        assertEquals(nativeDraft, scalar("SELECT draft_document FROM entity_mutation_policy_config WHERE id='native-1'"));
        Map<String,Object> runtime = document("SELECT config_document FROM entity_mutation_policy_release WHERE config_id='native-1'");
        assertEquals("运行校验", ((Map<?,?>)((List<?>)runtime.get("steps")).get(0)).get("stepName"));
        assertEquals(published, scalar("SELECT config_document FROM entity_version_config_release WHERE id='old-release'"));
        Map<String,Object> version = document("SELECT draft_document FROM entity_version_config WHERE id='version-1'");
        assertEquals(2, version.get("schemaVersion"));
        assertEquals(List.of(), version.get("steps"));
        assertEquals("UPDATE", ((Map<?,?>)((List<?>)version.get("triggers")).get(0)).get("triggerCode"));
        assertRemoved();
    }

    @Test
    void draftOnlyTargetMappingIsMigratedWithoutPublishingIt() throws Exception {
        sql("INSERT INTO entity_version_config(id,entity_id,entity_code,enabled) VALUES ('version-1','1','asset',1)");
        sql("INSERT INTO entity_version_scenario(id,config_id,scenario_code,scenario_name) VALUES ('capture-only','version-1','CAPTURE_ONLY','采集场景')");
        sql("INSERT INTO entity_change_target_binding(id,config_id,binding_code,binding_name,source_entity_code,target_entity_code,resolver_type,mapping_document) VALUES ('target-1','version-1','APPLY','变更','request','asset','FIELD','{\"amount\":\"requestedAmount\"}')");
        migrate();
        Map<String,Object> policy = document("SELECT draft_document FROM entity_mutation_policy_config WHERE entity_code='asset'");
        Map<?,?> target = (Map<?,?>)((List<?>)policy.get("targetBindings")).get(0);
        assertEquals(Map.of("amount","requestedAmount"), target.get("fieldMapping"));
        assertEquals(List.of(), policy.get("scenarios"));
        assertEquals(1, ((List<?>)document("SELECT draft_document FROM entity_version_config WHERE id='version-1'").get("triggers")).size());
        assertNull(scalar("SELECT active_release_id FROM entity_mutation_policy_config WHERE entity_code='asset'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM entity_mutation_policy_release"));
    }

    @Test
    void invalidOldJsonStopsBeforeAnyTableIsDropped() throws Exception {
        sql("INSERT INTO entity_form_field(id,form_id,field_id,field_code,field_name,field_label,field_type,component_props) VALUES ('bad','form-1','1','amount','金额','金额','DECIMAL','{broken')");
        assertThrows(Exception.class, this::migrate);
        assertEquals("4", tableCount());
        assertEquals("0", scalar("SELECT COUNT(*) FROM entity_form_node"));
    }

    @Test
    void localConfigurationCopyMigratesWithoutChangingPublishedDocuments() throws Exception {
        String fixture = System.getProperty("flow.compat.fixture");
        Assumptions.assumeTrue(SHADOW || fixture != null);
        if (SHADOW) copyLocalConfiguration(); else flyway("079.1", fixture).migrate();
        String oldVersions = scalar("SELECT COALESCE(SUM(CRC32(config_document)),0) FROM entity_version_config_release");
        String oldNative = scalar("SELECT COALESCE(SUM(CRC32(config_document)),0) FROM entity_mutation_policy_release");
        String activeNative = scalar("SELECT COUNT(*) FROM entity_mutation_policy_config WHERE active_release_id IS NOT NULL");
        if (SHADOW) migrate(); else flyway(null, fixture).migrate();
        assertRemoved();
        assertEquals(oldVersions, scalar("SELECT COALESCE(SUM(CRC32(config_document)),0) FROM entity_version_config_release"));
        // 已有策略发布原文均保留；新增的旧发布转换允许增加独立发布数量。
        assertTrue(Long.parseLong(scalar("SELECT COUNT(*) FROM entity_mutation_policy_config WHERE active_release_id IS NOT NULL")) >= Long.parseLong(activeNative));
        assertEquals("0", scalar("SELECT COUNT(*) FROM entity_version_config WHERE deleted=0 AND (draft_document IS NULL OR contract_version<>2)"));
        System.out.println("Local configuration copy: V080 passed; version snapshots unchanged; original native checksum=" + oldNative);
    }

    /** 在同一实例中验证实际 SQL，仅将本次使用的十二张表映射到专用测试副本。 */
    private Connection testConnection() throws Exception {
        Connection real = realConnection();
        if (!SHADOW) return real;
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    try {
                        if (method.getName().equals("prepareStatement")) {
                            return real.prepareStatement(shadowSql((String)args[0]));
                        }
                        if (method.getName().equals("createStatement")) {
                            Statement statement = real.createStatement();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class},
                                    (p,m,a) -> {
                                        if (a != null && a.length > 0 && a[0] instanceof String sql) a[0] = shadowSql(sql);
                                        try { return m.invoke(statement,a); }
                                        catch (InvocationTargetException exception) { throw exception.getCause(); }
                                    });
                        }
                        return method.invoke(real,args);
                    } catch (InvocationTargetException exception) { throw exception.getCause(); }
                });
    }
    private Connection realConnection() throws SQLException { return DriverManager.getConnection(url,user,password); }
    private String shadowSql(String sql) {
        if (!SHADOW) return sql;
        for (String table : TABLES) sql = sql.replaceAll("(?i)\\b"+table+"\\b", PREFIX+table);
        return sql;
    }
    private void migrate() throws Exception {
        if (!SHADOW) { flyway(null, null).migrate(); return; }
        try (Connection connection = testConnection()) {
            Context context = (Context) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Context.class},
                    (p,m,a) -> {
                        if (m.getName().equals("getConnection")) return connection;
                        throw new UnsupportedOperationException(m.getName());
                    });
            new V080__remove_compatibility_configuration_tables().migrate(context);
        }
    }
    /** 复制现有配置只写入随机前缀表，生成列由 MySQL 自行计算。 */
    private void copyLocalConfiguration() throws Exception {
        try (Connection connection=realConnection()) {
            for (String table : TABLES) {
                List<String> columns = new ArrayList<>();
                try (PreparedStatement query=connection.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND COALESCE(generation_expression,'')=''  ORDER BY ordinal_position")) {
                    query.setString(1, table);
                    try (ResultSet rs=query.executeQuery()) {
                        while(rs.next()) columns.add("`"+rs.getString(1).replace("`","``")+"`");
                    }
                }
                String names = String.join(",",columns);
                try (Statement statement=connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO `"+PREFIX+table+"` ("+names+") SELECT "+names+" FROM `"+table+"`");
                }
            }
        }
    }

    private Flyway flyway(String target, String fixture) {
        var config = Flyway.configure().dataSource(url,user,password).cleanDisabled(false)
                .placeholderReplacement(false).locations(fixture == null ? new String[]{"classpath:db/migration"}
                        : new String[]{"classpath:db/migration", "filesystem:"+fixture});
        if (target != null) config.target(target);
        return config.load();
    }
    private void assertRemoved() throws Exception { assertEquals("0", tableCount()); }
    private String tableCount() throws Exception {
        return scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('entity_form_field','entity_version_scenario','entity_version_step','entity_change_target_binding')");
    }
    private void sql(String sql) throws Exception { execute(sql); }
    private void execute(String sql, Object... args) throws Exception {
        try (Connection connection=testConnection(); PreparedStatement statement=connection.prepareStatement(sql)) {
            for (int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            statement.execute();
        }
    }
    private String scalar(String sql) throws Exception {
        try (Connection connection=testConnection(); Statement statement=connection.createStatement(); ResultSet rs=statement.executeQuery(sql)) {
            assertTrue(rs.next()); return rs.getString(1);
        }
    }
    private Map<String,Object> document(String sql) throws Exception { return json.readValue(scalar(sql),new TypeReference<>() {}); }
}
