package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 删除动态实体的 title/data_no 物理列和字段定义，名称、编号统一由 name/code 承载。
 *
 * <p>本次不迁移旧列值，也不为旧发布配置提供字段别名。通过实体登记表限定删除范围，
 * 包括软删除实体；系统表、未登记业务表及多值表不参与清理。使用 Java 逐表执行 DDL，
 * 避免引入存储过程权限或受 GROUP_CONCAT 长度限制而漏删部分表。</p>
 */
public class V096__remove_entity_title_and_data_no extends BaseJavaMigration {

    /** 只匹配退役列及其字段编码，不影响 name/code 和其他业务字段。 */
    private static final String RETIRED_FIELD = """
            (f.field_code IN ('title', 'dataNo', 'data_no')
             OR f.db_column_name IN ('title', 'data_no'))
            """;

    /**
     * 先完成全量表名校验，再按表删除已存在的列，最后清理字段元数据。
     * SQL 异常直接交给 Flyway，中途失败时不得把不完整的清理标记为成功。
     *
     * @param context 执行上下文，向后续迁移步骤传递身份、配置或状态
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        Map<String, List<String>> plan = columnRemovalPlan(connection);
        for (var table : plan.entrySet()) {
            // 同表两列合并为一次 ALTER，减少重建或持有元数据锁的次数。
            String actions = String.join(", ", table.getValue().stream()
                    .map(column -> "DROP COLUMN `" + column + "`")
                    .toList());
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE `" + table.getKey() + "` " + actions);
            }
        }
        removeFieldDefinitions(connection);
    }

    /**
     * MySQL DDL 隐式提交，不向调用方承诺跨表删除可整体回滚。
     *
     * @return {@code execute}事务条件成立时为 true，否则为 false
     */
    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    /**
     * 从当前库实际存在的列生成计划，空库、尚未发布的实体和已删除的列自然跳过。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @return 列{@code removal}方案键值结果，供调用方继续处理
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private Map<String, List<String>> columnRemovalPlan(Connection connection) throws SQLException {
        Map<String, List<String>> plan = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("""
                SELECT DISTINCT c.TABLE_NAME, c.COLUMN_NAME
                  FROM entity_definition e
                  JOIN information_schema.TABLES t
                    ON t.TABLE_SCHEMA = DATABASE()
                   AND t.TABLE_NAME = e.table_name
                   AND t.TABLE_TYPE = 'BASE TABLE'
                  JOIN information_schema.COLUMNS c
                    ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME
                 WHERE e.storage_mode = 'DYNAMIC'
                   AND c.COLUMN_NAME IN ('title', 'data_no')
                 ORDER BY c.TABLE_NAME, c.COLUMN_NAME
                """); var rows = statement.executeQuery()) {
            while (rows.next()) {
                String table = rows.getString("TABLE_NAME");
                // 与运行时动态表命名边界一致，错误登记不能把平台表纳入破坏性 DDL。
                if (!table.matches("biz_[a-z0-9_]+") || table.length() > 63) {
                    throw new SQLException("动态实体登记了非法业务表名: " + table);
                }
                plan.computeIfAbsent(table, ignored -> new ArrayList<>())
                        .add(rows.getString("COLUMN_NAME"));
            }
        }
        return plan;
    }

    /**
     * 删除字段及其专属配置，避免设计器继续显示已不存在的系统字段。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private void removeFieldDefinitions(Connection connection) throws SQLException {
        for (String table : List.of("entity_field_option", "entity_field_file_item", "entity_list_field")) {
            try (var statement = connection.createStatement()) {
                // 列表虚拟列的 field_id 可能不是数字；按文本比较，且不继承连接默认排序规则。
                statement.executeUpdate("DELETE child FROM " + table + " child "
                        + "JOIN entity_field f ON CAST(f.id AS CHAR CHARACTER SET utf8mb4) "
                        + "COLLATE utf8mb4_unicode_ci = child.field_id "
                        + "JOIN entity_definition e ON e.id = f.entity_id "
                        + "WHERE e.storage_mode = 'DYNAMIC' AND " + RETIRED_FIELD);
            }
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE f FROM entity_field f "
                    + "JOIN entity_definition e ON e.id = f.entity_id "
                    + "WHERE e.storage_mode = 'DYNAMIC' AND " + RETIRED_FIELD);
        }
    }
}
