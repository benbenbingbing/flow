package com.workflow.integration.database.schema.dialect;

import com.workflow.integration.database.api.schema.SchemaColumn;
import com.workflow.integration.database.api.schema.SchemaDefault;
import com.workflow.integration.database.api.schema.SchemaIndex;
import com.workflow.integration.database.api.schema.SchemaTable;
import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.integration.database.api.schema.SchemaDdlDialect;
import com.workflow.integration.database.schema.validation.DdlStatementGuard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/** 通用结构操作；产品特有的类型、ALTER 和审计列语法由各方言负责。 */
public abstract class AbstractSchemaDdlDialect implements SchemaDdlDialect {
    /**
     * 判断{@code mysql}条件是否成立，供调用方选择后续分支。
     *
     * @return {@code mysql}条件成立时为 true，否则为 false
     */
    protected boolean mysql() { return false; }
    /**
     * 判断{@code uppercase}{@code identifiers}条件是否成立，供调用方选择后续分支。
     *
     * @return {@code uppercase}{@code identifiers}条件成立时为 true，否则为 false
     */
    protected boolean uppercaseIdentifiers() { return false; }

    /**
     * 生成引用标识符文本，供后续匹配或展示。
     *
     * @param name 名称，后续用于处理引用标识符时匹配或展示
     * @return 处理后的引用标识符文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    public String quoteIdentifier(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("SQL 标识符不合法: " + name);
        }
        String delimiter = mysql() ? "`" : "\"";
        return delimiter + (uppercaseIdentifiers() ? name.toUpperCase(Locale.ROOT) : name) + delimiter;
    }

    /**
     * 仅用于无法使用占位符的 DDL 字面量；MySQL 额外处理反斜杠转义。
     *
     * @param value 待处理字面值的原始输入，结果供调用方继续使用
     * @return 处理后的字面值文本，供调用方比较或展示
     */
    protected String literal(String value) {
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("SQL 文本不能包含 NUL 字符");
        return "'" + (mysql() ? value.replace("\\", "\\\\") : value).replace("'", "''") + "'";
    }

    /**
     * 生成默认{@code clause}文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code defaultLiteral} 的输入影响后续处理
     * @return 处理后的默认{@code clause}文本，供调用方比较或展示
     */
    @Override
    public String defaultClause(SchemaColumn column) {
        if (!column.nullable() && column.defaultValue().kind() == SchemaDefault.Kind.NULL) return "";
        return " DEFAULT " + switch (column.defaultValue().kind()) {
            case NULL -> "NULL";
            case CURRENT_TIMESTAMP -> "CURRENT_TIMESTAMP";
            case LITERAL -> defaultLiteral(column);
        };
    }

    /**
     * 生成默认字面值文本，供后续匹配或展示。
     *
     * @param column 列，供本方法处理默认字面值时使用
     * @return 处理后的默认字面值文本，供调用方比较或展示
     */
    protected String defaultLiteral(SchemaColumn column) {
        Object value = column.defaultValue().value();
        if (value instanceof Boolean flag) return flag ? "1" : "0";
        return value instanceof String text ? literal(text) : value.toString();
    }

    /**
     * 生成列定义文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code quoteIdentifier} 的输入影响后续处理
     * @return 处理后的列定义文本，供调用方比较或展示
     */
    @Override
    public String columnDefinition(SchemaColumn column) {
        return quoteIdentifier(column.name()) + " " + typeSql(column.type())
                + defaultClause(column) + (column.nullable() ? "" : " NOT NULL");
    }

    /**
     * 生成列集合文本，供后续匹配或展示。
     *
     * @param columns 列集合，供本方法处理列集合时使用
     * @return 处理后的列集合文本，供调用方比较或展示
     */
    protected String columns(List<String> columns) {
        return columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
    }

    /**
     * 非 MySQL 的索引在 schema 内共享命名空间，长名称保留散列以免截断后冲突。
     *
     * @param prefix 前缀，供本方法处理对象名称时使用
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param suffix 后缀，供本方法处理对象名称时使用
     * @return 处理后的对象名称文本，供调用方比较或展示
     */
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

    /**
     * 创建索引；结果供后续流程传递或持久化。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param index 索引，供本方法创建索引时使用
     * @param ifNotExists 条件非存在，供本方法创建索引时使用
     * @return 创建后的索引文本，供调用方比较或展示
     */
    protected String createIndex(String table, SchemaIndex index, boolean ifNotExists) {
        return "CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX "
                + (ifNotExists && supportsCreateIfNotExists() ? "IF NOT EXISTS " : "")
                + quoteIdentifier(objectName("idx", table, index.name()))
                + " ON " + quoteIdentifier(table) + " (" + columns(index.columns()) + ")";
    }

    /**
     * 创建表；结果供后续流程传递或持久化。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return {@code abstract}结构DDL方言集合，供调用方遍历或展示
     */
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

    /**
     * 整理列注释数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理列注释时使用
     * @return {@code abstract}结构DDL方言集合，供调用方遍历或展示
     */
    protected List<String> columnComment(String table, SchemaColumn column) {
        return column.comment() == null ? List.of() : List.of("COMMENT ON COLUMN " + quoteIdentifier(table)
                + "." + quoteIdentifier(column.name()) + " IS " + literal(column.comment()));
    }

    /**
     * 审计时间戳；供后续追溯或审计使用。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法审计时间戳时使用
     * @return {@code abstract}结构DDL方言集合，供调用方遍历或展示
     */
    protected abstract List<String> auditTimestamp(String table, SchemaColumn column);

    /**
     * 添加列；结果供后续流程传递或持久化。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，作为 {@code result.addAll} 的输入影响后续处理
     * @return {@code abstract}结构DDL方言集合，供调用方遍历或展示
     */
    @Override
    public List<String> addColumn(String table, SchemaColumn column) {
        var result = new ArrayList<String>();
        result.add("ALTER TABLE " + quoteIdentifier(table) + " ADD " + columnDefinition(column));
        result.addAll(columnComment(table, column));
        result.addAll(auditTimestamp(table, column));
        return List.copyOf(result);
    }

    /**
     * 整理{@code drop}列数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理{@code drop}列时使用
     * @return {@code abstract}结构DDL方言集合，供调用方遍历或展示
     */
    @Override
    public List<String> dropColumn(String table, String column) {
        return List.of("ALTER TABLE " + quoteIdentifier(table) + " DROP COLUMN " + quoteIdentifier(column));
    }

    /**
     * 整理{@code drop}表数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return {@code abstract}结构DDL方言集合，供调用方遍历或展示
     */
    @Override
    public List<String> dropTable(SchemaTable table) { return List.of("DROP TABLE " + quoteIdentifier(table.name())); }

    /**
     * 校验{@code statement}；不满足约束时阻止后续处理。
     *
     * @param ddl DDL，作为 {@code DdlStatementGuard.requireSafe} 的输入影响后续处理
     */
    @Override
    public void validateStatement(String ddl) { DdlStatementGuard.requireSafe(ddl, mysql()); }

    /**
     * 判断列类型匹配条件是否成立，供调用方选择后续分支。
     *
     * @param expected 预期，作为 {@code normalizeType} 的输入影响后续处理
     * @param actualType 实际类型标识，决定后续列类型匹配采用的处理分支
     * @param length 长度，供本方法处理列类型匹配时使用
     * @param precision {@code precision}，供本方法处理列类型匹配时使用
     * @param scale {@code scale}，供本方法处理列类型匹配时使用
     * @return 列类型匹配条件成立时为 true，否则为 false
     */
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

    /**
     * 规范化类型；输出作为后续校验或处理的输入。
     *
     * @param type 类型标识，决定后续类型采用的处理分支
     * @return 规范化后的类型文本，供调用方比较或展示
     */
    protected String normalizeType(String type) {
        return type.toLowerCase(Locale.ROOT).replaceAll("\\(.*\\)", "").trim();
    }
}
