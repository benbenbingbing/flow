package com.workflow.dbmigrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 只使用显式指定的隔离 MySQL，验证删除数据列、保留查询配置及历史发布。 */
@EnabledIfSystemProperty(named = "flow.list-context.mysql-socket", matches = "/(?:private/)?tmp/flow-list-context-[^/]+/mysql\\.sock")
class ListContextBindingRemovalMigrationTest {
    @Test
    void removesRetiredDataFromActiveAndDeletedListsWithoutChangingReleases() throws Exception {
        String database = "flow_list_context_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            String output = mysql("CREATE DATABASE `" + database + "`; USE `" + database + "`;\n" + """
                    CREATE TABLE entity_list_config (
                      id VARCHAR(30) PRIMARY KEY, revision INT NOT NULL DEFAULT 1,
                      draft_hash VARCHAR(64), update_time DATETIME,
                      deleted TINYINT NOT NULL DEFAULT 0, context_binding_config LONGTEXT,
                      fixed_filter_config LONGTEXT
                    );
                    CREATE TABLE ui_config_release (snapshot_document LONGTEXT, content_hash VARCHAR(64));
                    INSERT INTO entity_list_config (id, revision, deleted, context_binding_config,
                        draft_hash, fixed_filter_config) VALUES
                      ('active',3,0,'{"parentField":"project_id"}','old','{"status":"APPROVED"}'),
                      ('deleted',4,1,'{"parentField":"project_id"}','old','{}'),
                      ('empty',5,0,'{}','old','{}'),
                      ('null',6,0,NULL,'old','{}');
                    INSERT INTO ui_config_release VALUES
                      ('{"list":{"contextBindingConfig":{"parentField":"project_id"}}}', 'original-hash');
                    """ + migration() + """
                    SELECT COUNT(*) FROM information_schema.columns
                      WHERE table_schema=DATABASE() AND table_name='entity_list_config'
                        AND column_name='context_binding_config';
                    SELECT id,revision,deleted,draft_hash IS NULL,fixed_filter_config
                      FROM entity_list_config ORDER BY id;
                    SELECT snapshot_document,content_hash FROM ui_config_release;
                    """);
            assertEquals("""
                    0
                    active\t4\t0\t1\t{"status":"APPROVED"}
                    deleted\t5\t1\t1\t{}
                    empty\t6\t0\t1\t{}
                    null\t7\t0\t1\t{}
                    {"list":{"contextBindingConfig":{"parentField":"project_id"}}}\toriginal-hash
                    """, output);
        } finally {
            mysql("DROP DATABASE IF EXISTS `" + database + "`;");
        }
    }

    /** 不读取用户默认连接配置，防止测试误连业务库；测试数据由 finally 删除。 */
    private String mysql(String sql) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(System.getProperty("flow.list-context.mysql-bin", "mysql"),
                "--no-defaults", "--protocol=SOCKET", "--socket=" + System.getProperty("flow.list-context.mysql-socket"),
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

    private String migration() throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/db/migration/V095__remove_list_context_binding_config.sql"))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
