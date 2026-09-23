package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.*;
import com.workflow.integration.database.api.SchemaDdlDialect;
import com.workflow.integration.database.schema.DdlStatementGuard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/** 通用结构操作；产品特有的类型、ALTER 和审计列语法由各方言负责。 */
public abstract class AbstractSchemaDdlDialect implements SchemaDdlDialect {
    protected boolean mysql() { return false; }
    protected boolean uppercaseIdentifiers() { return false; }

    @Override
    public String quoteIdentifier(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("SQL 标识符不合法: " + name);
        }
        String delimiter = mysql() ? "`" : "\"";
        return delimiter + (uppercaseIdentifiers() ? name.toUpperCase(Locale.ROOT) : name) + delimiter;
    }

    /** 仅用于无法使用占位符的 DDL 字面量；MySQL 额外处理反斜杠转义。 */
    protected String literal(String value) {
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("SQL 文本不能包含 NUL 字符");
        return "'" + (mysql() ? value.replace("\\", "\\\\") : value).replace("'", "''") + "'";
    }

    @Override
    public String defaultClause(SchemaColumn column) {
        if (!column.nullable() && column.defaultValue().kind() == SchemaDefault.Kind.NULL) return "";
        return " DEFAULT " + switch (column.defaultValue().kind()) {
            case NULL -> "NULL";
            case CURRENT_TIMESTAMP -> "CURRENT_TIMESTAMP";
            case LITERAL -> defaultLiteral(column);
        };
    }

    protected String defaultLiteral(SchemaColumn column) {
        Object value = column.defaultValue().value();
        if (value instanceof Boolean flag) return flag ? "1" : "0";
        return value instanceof String text ? literal(text) : value.toString();
    }

    @Override
    public String columnDefinition(SchemaColumn column) {
        return quoteIdentifier(column.name()) + " " + typeSql(column.type())
                + defaultClause(column) + (column.nullable() ? "" : " NOT NULL");
    }

    protected String columns(List<String> columns) {
        return columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
    }

    /** 非 MySQL 的索引在 schema 内共享命名空间，长名称保留散列以免截断后冲突。 */
    protected String objectName(String prefix, String table, String suffix) {
        String candidate = prefix + "_" + table + "_" + suffix;
        if (candidate.length() <= 63) return candidate;
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(candidate.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
            return candidate.substring(0, 50) + "_" + hash;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    protected String createIndex(String table, SchemaIndex index, boolean ifNotExists) {
        return "CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX "
                + (ifNotExists && supportsCreateIfNotExists() ? "IF NOT EXISTS " : "")
                + quoteIdentifier(objectName("idx", table, index.name()))
                + " ON " + quoteIdentifier(table) + " (" + columns(index.columns()) + ")";
    }

    @Override
    public List<String> createTable(SchemaTable table) {
        var definitions = new ArrayList<String>();
        table.columns().forEach(column -> definitions.add("  " + columnDefinition(column)));
        if (!table.primaryKey().isEmpty()) definitions.add("  PRIMARY KEY (" + columns(table.primaryKey()) + ")");
        var statements = new ArrayList<String>();
        statements.add("CREATE TABLE " + (table.ifNotExists() && supportsCreateIfNotExists() ? "IF NOT EXISTS " : "")
                + quoteIdentifier(table.name()) + " (\n" + String.join(",\n", definitions) + "\n)");
        table.indexes().forEach(index -> statements.add(createIndex(table.name(), index, table.ifNotExists())));
        if (table.comment() != null) statements.add("COMMENT ON TABLE " + quoteIdentifier(table.name())
                + " IS " + literal(table.comment()));
        for (var column : table.columns()) {
            statements.addAll(columnComment(table.name(), column));
            statements.addAll(auditTimestamp(table.name(), column));
        }
        return List.copyOf(statements);
    }

    protected List<String> columnComment(String table, SchemaColumn column) {
        return column.comment() == null ? List.of() : List.of("COMMENT ON COLUMN " + quoteIdentifier(table)
                + "." + quoteIdentifier(column.name()) + " IS " + literal(column.comment()));
    }

    protected abstract List<String> auditTimestamp(String table, SchemaColumn column);

    @Override
    public List<String> addColumn(String table, SchemaColumn column) {
        var result = new ArrayList<String>();
        result.add("ALTER TABLE " + quoteIdentifier(table) + " ADD " + columnDefinition(column));
        result.addAll(columnComment(table, column));
        result.addAll(auditTimestamp(table, column));
        return List.copyOf(result);
    }

    @Override
    public List<String> dropColumn(String table, String column) {
        return List.of("ALTER TABLE " + quoteIdentifier(table) + " DROP COLUMN " + quoteIdentifier(column));
    }

    @Override
    public List<String> dropTable(SchemaTable table) { return List.of("DROP TABLE " + quoteIdentifier(table.name())); }

    @Override
    public void validateStatement(String ddl) { DdlStatementGuard.requireSafe(ddl, mysql()); }

    @Override
    public boolean columnTypeMatches(SchemaType expected, String actualType, Long length, Integer precision, Integer scale) {
        if (actualType == null) return false;
        String expectedBase = normalizeType(typeSql(expected));
        if (!expectedBase.equals(normalizeType(actualType))) return false;
        if (expected.kind() == SchemaType.Kind.STRING) return Objects.equals(Long.valueOf(expected.length()), length);
        if (expected.kind() == SchemaType.Kind.DECIMAL) {
            return Objects.equals(expected.precision(), precision) && Objects.equals(expected.scale(), scale);
        }
        return true;
    }

    protected String normalizeType(String type) {
        return type.toLowerCase(Locale.ROOT).replaceAll("\\(.*\\)", "").trim();
    }
}
