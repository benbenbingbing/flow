package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.*;
import com.workflow.integration.database.schema.AuditTimestampDdl;
import java.util.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Oracle 12.2+ 方言（63 字符标识符需要兼容级别 >= 12.2）。 */
public class OracleSchemaDdlDialect extends AbstractSchemaDdlDialect {
    @Override public DatabaseVendor vendor() { return DatabaseVendor.ORACLE; }
    @Override public boolean supportsCreateIfNotExists() { return false; }
    @Override protected boolean uppercaseIdentifiers() { return true; }
    @Override public String typeSql(SchemaType type) {
        return switch (type.kind()) {
            case STRING -> {
                if (type.length() > 4000) throw new IllegalArgumentException("Oracle VARCHAR2 长度不能超过 4000；长文本请使用 TEXT");
                yield "VARCHAR2(" + type.length() + " CHAR)";
            }
            case TEXT, LARGE_TEXT -> "CLOB";
            case INTEGER -> "NUMBER(10)";
            case LONG -> "NUMBER(19)";
            case DECIMAL -> {
                if (type.precision() > 38) throw new IllegalArgumentException("Oracle NUMBER 精度不能超过 38");
                yield "NUMBER(" + type.precision() + "," + type.scale() + ")";
            }
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP";
            case BOOLEAN -> "NUMBER(1)";
            case BYTE -> "NUMBER(3)";
        };
    }
    @Override protected String defaultLiteral(SchemaColumn column) {
        if (column.defaultValue().value() instanceof String value) {
            // Oracle 日期默认值不能依赖会话 NLS 格式。
            if (column.type().kind() == SchemaType.Kind.DATE) return "DATE " + literal(LocalDate.parse(value).toString());
            if (column.type().kind() == SchemaType.Kind.TIMESTAMP) {
                LocalDateTime time = LocalDateTime.parse(value.replace(' ', 'T'));
                return "TIMESTAMP " + literal(time.toLocalDate() + " " + time.toLocalTime().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS")));
            }
        }
        return super.defaultLiteral(column);
    }
    @Override public List<String> modifyColumn(String table, SchemaColumn column) {
        var result = new ArrayList<String>();
        // 动态业务字段统一可空；避免反复设置 NULL 导致 ORA-01451。非空列在建表时声明。
        result.add("ALTER TABLE " + quoteIdentifier(table) + " MODIFY (" + quoteIdentifier(column.name())
                + " " + typeSql(column.type()) + defaultClause(column) + ")");
        result.addAll(columnComment(table, column));
        result.addAll(auditTimestamp(table, column));
        return List.copyOf(result);
    }
    @Override protected List<String> auditTimestamp(String table, SchemaColumn column) {
        return column.refreshTimestampOnUpdate() ? AuditTimestampDdl.oracle(quoteIdentifier(table),
                quoteIdentifier(column.name()), quoteIdentifier(objectName("tr", table, column.name()))) : List.of();
    }
    @Override public boolean columnTypeMatches(SchemaType expected, String actual, Long length, Integer precision, Integer scale) {
        if (!super.columnTypeMatches(expected, actual, length, precision, scale)) return false;
        Integer expectedPrecision = switch (expected.kind()) {
            case INTEGER -> 10; case LONG -> 19; case BOOLEAN -> 1; case BYTE -> 3; default -> null;
        };
        return expectedPrecision == null || (Objects.equals(expectedPrecision, precision) && Objects.equals(0, scale));
    }
}
