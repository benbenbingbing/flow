package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.*;
import java.util.*;

/** MySQL 8 方言，保持现有实体表的字符集、可空性、索引和审计时间行为。 */
public class MySqlSchemaDdlDialect extends AbstractSchemaDdlDialect {
    @Override public DatabaseVendor vendor() { return DatabaseVendor.MYSQL; }
    @Override protected boolean mysql() { return true; }
    @Override public boolean supportsCreateIfNotExists() { return true; }

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

    @Override public List<String> addColumn(String table, SchemaColumn column) {
        return List.of("ALTER TABLE " + quoteIdentifier(table) + " ADD COLUMN " + columnDefinition(column));
    }
    @Override public List<String> modifyColumn(String table, SchemaColumn column) {
        return List.of("ALTER TABLE " + quoteIdentifier(table) + " MODIFY COLUMN " + columnDefinition(column));
    }
    @Override protected List<String> auditTimestamp(String table, SchemaColumn column) { return List.of(); }
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
