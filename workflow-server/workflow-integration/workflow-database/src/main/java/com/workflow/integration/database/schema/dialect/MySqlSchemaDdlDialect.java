package com.workflow.integration.database.schema.dialect;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.schema.SchemaColumn;
import com.workflow.integration.database.api.schema.SchemaDefault;
import com.workflow.integration.database.api.schema.SchemaTable;
import com.workflow.integration.database.api.schema.SchemaType;

import java.util.*;

/** MySQL 8 方言，保持现有实体表的字符集、可空性、索引和审计时间行为。 */
public class MySqlSchemaDdlDialect extends AbstractSchemaDdlDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return DatabaseVendor.MYSQL; }
    /**
     * 判断{@code mysql}条件是否成立，供调用方选择后续分支。
     *
     * @return {@code mysql}条件成立时为 true，否则为 false
     */
    @Override protected boolean mysql() { return true; }
    /**
     * 判断是否支持创建条件非存在；判断结果决定调用方的后续分支。
     *
     * @return 创建条件非存在条件成立时为 true，否则为 false
     */
    @Override public boolean supportsCreateIfNotExists() { return true; }

    /**
     * 生成类型SQL文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续类型SQL采用的处理分支
     * @return 处理后的类型SQL文本，供调用方比较或展示
     */
    @Override
    public String typeSql(SchemaType type) {
        return switch (type.kind()) {
            case STRING -> "VARCHAR(" + type.length() + ")";
            case TEXT -> "TEXT";
            case LARGE_TEXT -> "LONGTEXT";
            case INTEGER -> "INT";
            case LONG -> "BIGINT";
            case DECIMAL -> "DECIMAL(" + type.precision() + "," + type.scale() + ")";
            case DATE -> "DATE";
            case TIMESTAMP -> "DATETIME";
            case BOOLEAN -> "TINYINT(1)";
            case BYTE -> "TINYINT";
        };
    }

    /**
     * 生成列定义文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code defaultClause} 的输入影响后续处理
     * @return 处理后的列定义文本，供调用方比较或展示
     */
    @Override
    public String columnDefinition(SchemaColumn column) {
        // MySQL TEXT 默认值需表达式形式；NULL 沿用旧建表行为。
        String defaults = defaultClause(column);
        if ((column.type().kind() == SchemaType.Kind.TEXT || column.type().kind() == SchemaType.Kind.LARGE_TEXT)
                && column.defaultValue().kind() == SchemaDefault.Kind.LITERAL) {
            defaults = " DEFAULT (" + defaultLiteral(column) + ")";
        }
        return quoteIdentifier(column.name()) + " " + typeSql(column.type())
                + (column.nullable() ? "" : " NOT NULL") + defaults
                + (column.refreshTimestampOnUpdate() ? " ON UPDATE CURRENT_TIMESTAMP" : "")
                + (column.comment() == null ? "" : " COMMENT " + literal(column.comment()));
    }

    /**
     * 创建表；结果供后续流程传递或持久化。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 我的SQL结构DDL方言集合，供调用方遍历或展示
     */
    @Override
    public List<String> createTable(SchemaTable table) {
        var definitions = new ArrayList<String>();
        table.columns().forEach(column -> definitions.add("  " + columnDefinition(column)));
        // MySQL 索引可随表原子创建，避免表已经可见但索引尚未落地。
        table.indexes().forEach(index -> definitions.add("  " + (index.unique() ? "UNIQUE KEY " : "KEY ")
                + quoteIdentifier(index.name()) + " (" + columns(index.columns()) + ")"));
        if (!table.primaryKey().isEmpty()) definitions.add("  PRIMARY KEY (" + columns(table.primaryKey()) + ")");
        return List.of("CREATE TABLE " + (table.ifNotExists() ? "IF NOT EXISTS " : "")
                + quoteIdentifier(table.name()) + " (\n" + String.join(",\n", definitions) + "\n)"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                + (table.comment() == null ? "" : " COMMENT=" + literal(table.comment())));
    }

    /**
     * 添加列；结果供后续流程传递或持久化。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法添加列时使用
     * @return 我的SQL结构DDL方言集合，供调用方遍历或展示
     */
    @Override public List<String> addColumn(String table, SchemaColumn column) {
        return List.of("ALTER TABLE " + quoteIdentifier(table) + " ADD COLUMN " + columnDefinition(column));
    }
    /**
     * 整理{@code modify}列数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理{@code modify}列时使用
     * @return 我的SQL结构DDL方言集合，供调用方遍历或展示
     */
    @Override public List<String> modifyColumn(String table, SchemaColumn column) {
        return List.of("ALTER TABLE " + quoteIdentifier(table) + " MODIFY COLUMN " + columnDefinition(column));
    }
    /**
     * 审计时间戳；供后续追溯或审计使用。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法审计时间戳时使用
     * @return 我的SQL结构DDL方言集合，供调用方遍历或展示
     */
    @Override protected List<String> auditTimestamp(String table, SchemaColumn column) { return List.of(); }
    /**
     * 规范化类型；输出作为后续校验或处理的输入。
     *
     * @param type 类型标识，决定后续类型采用的处理分支
     * @return 规范化后的类型文本，供调用方比较或展示
     */
    @Override protected String normalizeType(String type) {
        return switch (super.normalizeType(type)) {
            case "integer" -> "int";
            case "character varying" -> "varchar";
            // Connector/J 的 tinyInt1isBit 默认会将 TINYINT(1) 报告为 BIT。
            case "bit", "boolean" -> "tinyint";
            default -> super.normalizeType(type);
        };
    }
}
