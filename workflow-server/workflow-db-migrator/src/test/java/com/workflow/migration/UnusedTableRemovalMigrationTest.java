package com.workflow.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 在指定 MySQL 库中使用随机前缀建表，验证 V081，不操作该库的实际业务表。 */
@EnabledIfSystemProperty(named = "flow.unused.mysql.url", matches = "jdbc:mysql:.*")
class UnusedTableRemovalMigrationTest {
    private static final List<String> RETIRED = List.of(
            "entity_status_history", "process_common_opinion", "process_draft",
            "process_task_instance", "workbench_config", "workbench_shortcut");

    @Test
    void removesHistoricalTablesWithPresetDataAndPreservesCurrentTaskTable() throws Exception {
        String prefix = "__v081_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "_";
        String baseline = resource("V001__business_schema.sql");
        String migration = resource("V081__remove_unused_workflow_tables.sql");
        try (Connection connection = DriverManager.getConnection(
                System.getProperty("flow.unused.mysql.url"),
                System.getenv("SCHEMA_DB_USERNAME"), System.getenv("SCHEMA_DB_PASSWORD"));
             Statement statement = connection.createStatement()) {
            try {
                // 从不可变建表迁移还原结构，使测试在正式库已经删除这些表之后仍可重复运行。
                for (String table : RETIRED) {
                    var ddl = Pattern.compile("(?ms)^CREATE TABLE `" + Pattern.quote(table)
                            + "` \\(.*?^\\).*?;").matcher(baseline);
                    assertTrue(ddl.find(), "缺少历史表结构: " + table);
                    statement.execute(ddl.group().replace("`" + table + "`", "`" + prefix + table + "`"));
                    migration = migration.replaceAll("\\b" + Pattern.quote(table) + "\\b", prefix + table);
                }
                statement.execute("CREATE TABLE `" + prefix + "process_task` (id INT PRIMARY KEY, marker VARCHAR(30))");
                statement.execute("INSERT INTO `" + prefix + "process_task` VALUES (1,'keep-current-task')");
                statement.execute("INSERT INTO `" + prefix + "workbench_config` "
                        + "(id,config_name,config_code,layout_config,is_default,is_system) "
                        + "VALUES ('preset','默认工作台','DEFAULT','[]',1,1)");

                assertEquals(7, tableCount(connection, prefix));
                statement.execute(migration);
                assertEquals(1, tableCount(connection, prefix), "只能删除六张退役表");
                // IF EXISTS 支持清理已不存在的历史表；重复执行不能波及现行任务表。
                statement.execute(migration);
                try (var rows = statement.executeQuery("SELECT marker FROM `" + prefix + "process_task` WHERE id=1")) {
                    assertTrue(rows.next());
                    assertEquals("keep-current-task", rows.getString(1));
                }
            } finally {
                for (String table : RETIRED) statement.execute("DROP TABLE IF EXISTS `" + prefix + table + "`");
                statement.execute("DROP TABLE IF EXISTS `" + prefix + "process_task`");
            }
            assertEquals(0, tableCount(connection, prefix), "测试不得遗留临时表");
        }
    }

    private int tableCount(Connection connection, String prefix) throws Exception {
        try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND LEFT(table_name,?)=?")) {
            query.setInt(1, prefix.length());
            query.setString(2, prefix);
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private String resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/" + name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
