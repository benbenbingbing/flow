package com.workflow.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 仅在显式指定的隔离 MySQL 中验证候选退役，禁止连接开发或生产业务库。 */
@EnabledIfSystemProperty(named = "flow.release-candidate.mysql-socket",
        matches = "/(?:private/)?tmp/flow-release-candidate-[^/]+/mysql\\.sock")
class ReleaseCandidateRemovalMigrationTest {
    private static final List<String> PRESERVED_TABLES = List.of(
            "config_migration_asset", "config_migration_asset_dependency",
            "config_export_package", "config_export_package_item",
            "config_import_package", "config_import_item",
            "config_asset_baseline", "config_environment_mapping", "system_operation_log");

    @Test
    void removesCandidateHistoryAndGrantsButPreservesMigrationAndRollbackData() throws Exception {
        String database = "flow_candidate_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            // 使用 V053 的真实候选表结构；菜单夹具额外覆盖复制页面、无权限按钮及孤儿授权。
            String historical = resource("V053__release_candidate_orchestration.sql");
            StringBuilder fixture = new StringBuilder(historical.substring(0, historical.indexOf("INSERT INTO sys_menu")));
            fixture.append("""
                    CREATE TABLE sys_menu (
                      id VARCHAR(64) PRIMARY KEY, parent_id VARCHAR(64),
                      perm VARCHAR(100), path VARCHAR(200), component VARCHAR(200)
                    );
                    CREATE TABLE sys_role_menu (id VARCHAR(64) PRIMARY KEY, role_id VARCHAR(64), menu_id VARCHAR(64));
                    INSERT INTO sys_menu VALUES
                      ('release_candidate_menu_001','0','release-candidate:list','/system/release-candidates','system/ReleaseCandidateManagement'),
                      ('release_candidate_create_001','release_candidate_menu_001','release-candidate:create','',''),
                      ('release_candidate_recover_001','release_candidate_menu_001','release-candidate:recover','',''),
                      ('cloned-page','0','','/system/release-candidates',''),
                      ('cloned-child','cloned-page','','',''),
                      ('cloned-component','0','','','system/ReleaseCandidateManagement'),
                      ('cloned-permission','0','release-candidate:publish','',''),
                      ('migration-menu','0','config-migration:list','/system/config-migration','system/ConfigMigrationManagement'),
                      ('migration-publish','migration-menu','config-migration:publish','',''),
                      ('migration-rollback','migration-menu','config-migration:rollback','',''),
                      ('unrelated','0','other:list','/other','Other');
                    INSERT INTO sys_role_menu SELECT id,'operator',id FROM sys_menu;
                    INSERT INTO sys_role_menu VALUES ('orphan','operator','release_candidate_publish_001');
                    INSERT INTO release_candidate
                      (id,candidate_no,candidate_name,source_import_id,migration_tag,status,candidate_hash)
                    VALUES ('published','RC-1','Published','import-1','tag-1','PUBLISHED','hash-1'),
                           ('failed','RC-2','Failed','import-1','tag-1','FAILED','hash-2');
                    INSERT INTO release_candidate_report (id,candidate_id,report_no,report_json)
                      VALUES ('report-1','published','R-1','{"status":"PUBLISHED"}');
                    """);
            // 保留表放入不可丢失的哨兵记录，覆盖导入批次、回滚快照、基线及普通操作审计。
            for (String table : PRESERVED_TABLES) {
                fixture.append("CREATE TABLE ").append(table)
                        .append(" (id VARCHAR(64) PRIMARY KEY, payload LONGTEXT);\n")
                        .append("INSERT INTO ").append(table)
                        .append(" VALUES ('keep','{\"status\":\"PUBLISHED\",\"snapshot\":\"before\"}');\n");
            }
            String removal = resource("V099__remove_release_candidates.sql");
            // 重复执行还应安全，覆盖无候选表/菜单的环境和中断后的重试。
            mysql("CREATE DATABASE " + database + "; USE " + database + ";\n" + fixture + removal + removal);
            assertEquals("""
                    0
                    migration-menu
                    migration-publish
                    migration-rollback
                    unrelated
                    migration-menu
                    migration-publish
                    migration-rollback
                    unrelated
                    """, mysql("USE " + database + ";\n" + """
                    SELECT COUNT(*) FROM information_schema.tables
                      WHERE table_schema=DATABASE() AND table_name REGEXP '^release_candidate($|_)';
                    SELECT id FROM sys_menu ORDER BY id;
                    SELECT menu_id FROM sys_role_menu ORDER BY menu_id;
                    """));
            for (String table : PRESERVED_TABLES) {
                assertEquals("keep\t{\"status\":\"PUBLISHED\",\"snapshot\":\"before\"}\n",
                        mysql("SELECT id,payload FROM " + database + "." + table + ";"), table);
            }
        } finally {
            mysql("DROP DATABASE IF EXISTS " + database + ";");
        }
    }

    /** 忽略用户默认连接配置，只使用受类级条件约束的临时 socket。 */
    private String mysql(String sql) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                System.getProperty("flow.release-candidate.mysql-bin", "mysql"),
                "--no-defaults", "--protocol=SOCKET",
                "--socket=" + System.getProperty("flow.release-candidate.mysql-socket"),
                "--user=root", "--batch", "--skip-column-names").redirectErrorStream(true);
        builder.environment().remove("MYSQL_PWD");
        Process process = builder.start();
        try (var input = process.getOutputStream()) {
            input.write(sql.getBytes(StandardCharsets.UTF_8));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }

    private String resource(String name) throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/" + name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
