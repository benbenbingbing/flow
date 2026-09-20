package com.workflow.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 只连接 /tmp 下显式指定的隔离实例，以真实 MySQL JSON 运算验证草稿迁移和历史快照不变。 */
@EnabledIfSystemProperty(named = "flow.list-query.mysql-socket", matches = "/(?:private/)?tmp/flow-list-load-[^/]+/mysql\\.sock")
class ListQueryEventMigrationTest {
    @Test
    void migratesEightInheritanceCasesWithoutChangingPublishedSnapshots() throws Exception {
        String database = "flow_list_query_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            String actual = mysql("CREATE DATABASE `" + database + "`; USE `" + database + "`;\n"
                    + resource("/list-query-event-fixture.sql")
                    + resource("/db/migration/V094__merge_list_query_into_load_event.sql")
                    + """
                    SELECT c.id, c.query_interface_extension_id IS NULL, c.revision, c.draft_hash IS NULL,
                           COALESCE(b.inheritance_mode,'NONE'), COALESCE(JSON_LENGTH(b.steps_document),0),
                           COALESCE(JSON_CONTAINS(b.steps_document, '{"legacyListQuery":true}'),0)
                    FROM entity_list_config c LEFT JOIN ui_event_binding b
                      ON b.owner_type='LIST' AND b.owner_id=c.id AND b.deleted=0 ORDER BY c.id;
                    SELECT id,snapshot_document,content_hash FROM ui_config_release;
                    """);
            assertEquals("""
                    append_inherited\t1\t2\t1\tINHERIT\t1\t0
                    before\t1\t2\t1\tINHERIT\t3\t1
                    disable\t1\t2\t1\tREPLACE\t1\t1
                    disabled\t1\t2\t1\tINHERIT\t1\t1
                    inherited\t1\t2\t1\tNONE\t0\t0
                    override\t1\t2\t1\tREPLACE\t2\t1
                    plain\t1\t2\t1\tINHERIT\t1\t1
                    replace\t1\t2\t1\tINHERIT\t1\t0
                    historical\tunchanged\tunchanged-hash
                    """, actual);
        } finally {
            mysql("DROP DATABASE IF EXISTS `" + database + "`;");
        }
    }

    private String mysql(String sql) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(System.getProperty("flow.list-query.mysql-bin", "mysql"),
                "--no-defaults", "--protocol=SOCKET", "--socket=" + System.getProperty("flow.list-query.mysql-socket"),
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
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
