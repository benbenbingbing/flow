package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.*;
import com.workflow.integration.database.schema.AuditTimestampDdl;
import java.util.*;

/** PostgreSQL 方言，布尔存储沿用应用的 0/1 约定，避免现有 deleted = 0 查询失效。 */
public class PostgresSchemaDdlDialect extends AbstractSchemaDdlDialect {
    @Override public DatabaseVendor vendor() { return DatabaseVendor.POSTGRESQL; }
    @Override public boolean supportsCreateIfNotExists() { return true; }
    @Override public String typeSql(SchemaType type) {
        return switch (type.kind()) {
            case STRING -> "VARCHAR(" + type.length() + ")";
            case TEXT, LARGE_TEXT -> "TEXT";
            case INTEGER -> "INTEGER";
            case LONG -> "BIGINT";
            case DECIMAL -> "NUMERIC(" + type.precision() + "," + type.scale() + ")";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP";
            case BOOLEAN, BYTE -> "SMALLINT";
        };
    }
    @Override protected String literal(String text) {
        // E 字符串显式指定转义规则，不依赖 standard_conforming_strings 的连接配置。
        return text.contains("\\") ? "E" + super.literal(text.replace("\\", "\\\\")) : super.literal(text);
    }
    @Override public List<String> modifyColumn(String table, SchemaColumn column) {
        String prefix = "ALTER TABLE " + quoteIdentifier(table) + " ALTER COLUMN " + quoteIdentifier(column.name());
        var result = new ArrayList<String>();
        // 先移除旧默认值，避免默认值无法隐式转换阻止合法的类型变更；不静默进行有损 USING 转换。
        result.add(prefix + " DROP DEFAULT");
        result.add(prefix + " TYPE " + typeSql(column.type()));
        result.add(prefix + " SET" + defaultClause(column));
        result.add(prefix + (column.nullable() ? " DROP NOT NULL" : " SET NOT NULL"));
        result.addAll(columnComment(table, column));
        result.addAll(auditTimestamp(table, column));
        return List.copyOf(result);
    }
    @Override protected List<String> auditTimestamp(String table, SchemaColumn column) {
        return column.refreshTimestampOnUpdate() ? AuditTimestampDdl.postgres(quoteIdentifier(table),
                quoteIdentifier(column.name()), quoteIdentifier(objectName("fn", table, column.name())),
                quoteIdentifier(objectName("tr", table, column.name()))) : List.of();
    }
    @Override public List<String> dropTable(SchemaTable table) {
        var result = new ArrayList<>(super.dropTable(table));
        // PostgreSQL 删除表会删除触发器，但函数是独立对象，需要显式清理。
        table.columns().stream().filter(SchemaColumn::refreshTimestampOnUpdate).forEach(column ->
                result.add(AuditTimestampDdl.dropPostgresFunction(quoteIdentifier(objectName("fn", table.name(), column.name())))));
        return List.copyOf(result);
    }
    @Override public List<String> dropColumn(String table, String column) {
        return List.of(AuditTimestampDdl.dropPostgresTrigger(quoteIdentifier(table), quoteIdentifier(objectName("tr", table, column))),
                super.dropColumn(table, column).get(0),
                AuditTimestampDdl.dropPostgresFunction(quoteIdentifier(objectName("fn", table, column))));
    }
    @Override protected String normalizeType(String type) {
        return switch (super.normalizeType(type)) {
            case "character varying" -> "varchar";
            case "int4", "int" -> "integer";
            case "int8" -> "bigint";
            case "int2" -> "smallint";
            case "decimal" -> "numeric";
            case "timestamp without time zone" -> "timestamp";
            default -> super.normalizeType(type);
        };
    }
}
