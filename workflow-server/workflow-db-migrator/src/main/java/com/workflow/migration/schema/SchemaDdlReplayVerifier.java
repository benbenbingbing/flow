package com.workflow.migration.schema;

import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.integration.database.schema.*;

import com.workflow.integration.database.api.*;
import com.workflow.integration.database.api.SchemaDdlDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;

/**
 * 对可结构化识别的建表、加列、建索引及删除操作验证实际结果。
 * 只理解本项目方言生成的语法子集；未知语法、表达式或元数据不足一律不能证明重放成功。
 * 这不是 SQL 转换器，也不修改用户提交的 DDL。
 */
public final class SchemaDdlReplayVerifier {
    private final JdbcTemplate jdbc;
    private final SchemaDdlDialect dialect;
    private final JdbcSchemaMetadata metadata;
    private final boolean mysql;

    public SchemaDdlReplayVerifier(JdbcTemplate jdbc, SchemaDdlDialect dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
        this.metadata = new JdbcSchemaMetadata(jdbc, dialect);
        this.mysql = Set.of(DatabaseVendor.MYSQL, DatabaseVendor.OCEANBASE_MYSQL).contains(dialect.vendor());
    }

    /** IF NOT EXISTS 可能无声跳过错误对象，因此建表和建索引成功返回后也需要验收。 */
    public boolean requiresCreationVerification(String ddl) {
        return ddl.trim().matches("(?is)^CREATE\\s+(?:TABLE|(?:UNIQUE\\s+)?INDEX)\\b.*");
    }

    /** 不依赖错误文本或厂商错误码；查询失败应抛出，绝不能当作对象不存在。 */
    public boolean isApplied(String ddl) {
        Target target;
        try { target = parse(ddl); }
        catch (IllegalArgumentException unsupported) { return false; }
        if (target == null) return false;
        boolean exists = metadata.tableExists(target.table);
        if (target.operation.equals("DROP_TABLE")) return !exists;
        if (!exists) return false;
        var columns = metadata.columns(target.table);
        if (target.operation.equals("DROP_COLUMN")) return columns.stream().noneMatch(c -> c.name().equals(target.removed));
        if (target.operation.equals("CREATE_TABLE") && !columns.stream().map(SchemaColumnMetadata::name).toList()
                .equals(target.columns.stream().map(c -> c.name).toList())) return false;
        for (var expected : target.columns) {
            var actual = columns.stream().filter(c -> c.name().equals(expected.name)).findFirst();
            if (actual.isEmpty() || !columnMatches(target.table, expected, actual.get())) return false;
        }
        var indexes = metadata.indexes(target.table);
        for (var expected : target.indexes) {
            if (indexes.stream().noneMatch(actual -> (expected.primary ? actual.primaryKey() : actual.name().equals(expected.name))
                    && actual.unique() == expected.unique && actual.columns().equals(expected.columns))) return false;
        }
        if (target.operation.equals("CREATE_TABLE")) {
            // 额外的唯一约束会改变写入行为，不能视为与目标建表等价。
            if (indexes.stream().filter(SchemaIndexMetadata::unique).count() != target.indexes.stream().filter(i -> i.unique).count()) return false;
            if (target.comment != null && metadata.tables().stream().noneMatch(t -> t.name().equals(target.table)
                    && Objects.equals(target.comment, t.comment()))) return false;
            if (mysql && (target.engine != null || target.collation != null)) {
                Boolean options = jdbc.query("SELECT ENGINE, TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",
                        rows -> rows.next() && (target.engine == null || target.engine.equalsIgnoreCase(rows.getString(1)))
                                && (target.collation == null || target.collation.equalsIgnoreCase(rows.getString(2))), target.table);
                if (!Boolean.TRUE.equals(options)) return false;
            }
        }
        return true;
    }

    private boolean columnMatches(String table, Column expected, SchemaColumnMetadata actual) {
        if (!dialect.columnTypeMatches(expected.type, actual.typeName(), actual.length(), actual.precision(), actual.scale())
                || expected.nullable != actual.nullable()) return false;
        if (expected.comment != null && !Objects.equals(expected.comment, actual.comment())) return false;
        if (mysql) {
            // Connector/J 的 getColumns 不总提供 DATETIME 小数精度，且 SHOW 路径会转义表达式默认值。
            // 这些 MySQL 扩展属性集中从 information_schema 读取，不能将“驱动未提供”当作相等。
            var details = jdbc.query("SELECT c.COLUMN_DEFAULT, c.EXTRA, c.DATETIME_PRECISION, c.COLLATION_NAME, t.TABLE_COLLATION"
                    + " FROM information_schema.COLUMNS c JOIN information_schema.TABLES t"
                    + " ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME"
                    + " WHERE c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=? AND c.COLUMN_NAME=?",
                    rows -> rows.next() ? new MySqlColumn(rows.getString(1), rows.getString(2).toLowerCase(Locale.ROOT),
                            (Number) rows.getObject(3), rows.getString(4), rows.getString(5)) : null, table, expected.name);
            if (details == null || details.extra.contains("auto_increment") || details.extra.contains("virtual generated")
                    || details.extra.contains("stored generated")) return false;
            if (details.extra.contains("on update current_timestamp") != expected.onUpdate) return false;
            if (expected.type.kind() == SchemaType.Kind.TIMESTAMP && (details.timePrecision == null || details.timePrecision.intValue() != 0)) return false;
            if (details.collation != null && !Objects.equals(details.collation, details.tableCollation)) return false;
            String actualDefault = details.defaultValue;
            if (actualDefault != null && details.extra.contains("default_generated") && isText(expected.type)) {
                try { actualDefault = mySqlExpressionDefault(table, expected.name, expected.type); }
                catch (IllegalArgumentException unsupported) { return false; }
            } else {
                actualDefault = normalizeDefault(actualDefault, expected.type);
            }
            if (!Objects.equals(expected.defaultValue, actualDefault)) return false;
        } else if (!Objects.equals(expected.defaultValue, normalizeDefault(actual.defaultValue(), expected.type))) {
            return false;
        }
        return true;
    }

    /**
     * MySQL 8 的 COLUMN_DEFAULT 可能将表达式的引号二次转义并错误呈现非 ASCII 字节。
     * SHOW CREATE 的 SQL 字面量保留正确字符；仅解析目标列的标量默认值，绝不执行该元数据表达式。
     */
    private String mySqlExpressionDefault(String table, String column, SchemaType type) {
        String create = jdbc.queryForObject("SHOW CREATE TABLE " + dialect.quoteIdentifier(table), (row, index) -> row.getString(2));
        var input = new Tokens(Objects.requireNonNull(create), true);
        input.require("CREATE"); input.require("TABLE"); input.identifier(); input.require("(");
        int depth = 1;
        boolean beginning = true, target = false;
        while (depth > 0) {
            Token token = input.next();
            if (beginning) { target = token.kind == 'i' && token.value.equalsIgnoreCase(column); beginning = false; }
            if (depth == 1 && target && token.kind == 'w' && token.value.equalsIgnoreCase("DEFAULT")) return defaultValue(input, type);
            if (token.kind == 'p') {
                if (token.value.equals("(")) depth++;
                else if (token.value.equals(")")) depth--;
                else if (token.value.equals(",") && depth == 1) beginning = true;
            }
        }
        throw new IllegalArgumentException("Generated default was not found in SHOW CREATE TABLE");
    }

    private static boolean isText(SchemaType type) {
        return Set.of(SchemaType.Kind.STRING, SchemaType.Kind.TEXT, SchemaType.Kind.LARGE_TEXT).contains(type.kind());
    }

    private record MySqlColumn(String defaultValue, String extra, Number timePrecision, String collation, String tableCollation) {}

    private Target parse(String sql) {
        var input = new Tokens(sql, mysql);
        if (input.take("DROP")) {
            input.require("TABLE");
            if (input.take("IF")) input.require("EXISTS");
            var target = new Target("DROP_TABLE", input.identifier());
            input.end();
            return target;
        }
        if (input.take("ALTER")) {
            input.require("TABLE");
            String table = input.identifier();
            if (input.take("DROP")) {
                input.require("COLUMN");
                var target = new Target("DROP_COLUMN", table);
                target.removed = input.identifier();
                input.end();
                return target;
            }
            if (!input.take("ADD")) return null;
            input.take("COLUMN");
            var target = new Target("ADD_COLUMN", table);
            target.columns.add(column(input));
            input.end();
            return target;
        }
        if (!input.take("CREATE")) return null;
        if (!input.take("TABLE")) {
            boolean unique = input.take("UNIQUE");
            input.require("INDEX");
            if (input.take("IF")) { input.require("NOT"); input.require("EXISTS"); }
            String name = input.identifier();
            input.require("ON");
            var target = new Target("CREATE_INDEX", input.identifier());
            target.indexes.add(new Index(name, identifiers(input), unique, false));
            input.end();
            return target;
        }
        if (input.take("IF")) { input.require("NOT"); input.require("EXISTS"); }
        var target = new Target("CREATE_TABLE", input.identifier());
        input.require("(");
        do {
            if (input.take("PRIMARY")) {
                input.require("KEY");
                target.indexes.add(new Index("", identifiers(input), true, true));
            } else if (input.peek("UNIQUE") || input.peek("KEY")) {
                boolean unique = input.take("UNIQUE");
                input.require("KEY");
                target.indexes.add(new Index(input.identifier(), identifiers(input), unique, false));
            } else {
                target.columns.add(column(input));
            }
        } while (input.take(","));
        input.require(")");
        for (var index : target.indexes) if (index.primary) {
            for (var column : target.columns) if (index.columns.contains(column.name)) column.nullable = false;
        }
        if (input.take("ENGINE")) { input.require("="); target.engine = input.word(); }
        if (input.take("DEFAULT")) {
            input.require("CHARSET"); input.require("=");
            // 本项目只渲染 utf8mb4；其他字符集需要完整元数据验证，不能仅忽略该选项。
            input.require("utf8mb4");
        }
        if (input.take("COLLATE")) { input.require("="); target.collation = input.word(); }
        if (input.take("COMMENT")) { input.require("="); target.comment = input.string(); }
        input.end();
        return target;
    }

    private Column column(Tokens input) {
        var column = new Column();
        column.name = input.identifier();
        String name = input.word().toUpperCase(Locale.ROOT);
        Integer size = null, scale = null;
        if (input.take("(")) {
            size = Integer.valueOf(input.word());
            if (input.take(",")) scale = Integer.valueOf(input.word());
            input.take("CHAR");
            input.require(")");
        }
        column.type = switch (name) {
            case "VARCHAR", "VARCHAR2" -> SchemaType.string(Objects.requireNonNull(size));
            case "TEXT", "CLOB" -> SchemaType.of(SchemaType.Kind.TEXT);
            case "LONGTEXT" -> SchemaType.of(SchemaType.Kind.LARGE_TEXT);
            case "INT", "INTEGER" -> SchemaType.of(SchemaType.Kind.INTEGER);
            case "BIGINT" -> SchemaType.of(SchemaType.Kind.LONG);
            case "SMALLINT" -> SchemaType.of(SchemaType.Kind.BOOLEAN);
            case "TINYINT" -> SchemaType.of(Objects.equals(size, 1) ? SchemaType.Kind.BOOLEAN : SchemaType.Kind.BYTE);
            case "DECIMAL", "NUMERIC", "NUMBER" -> SchemaType.decimal(Objects.requireNonNull(size), scale == null ? 0 : scale);
            case "DATE" -> SchemaType.of(SchemaType.Kind.DATE);
            case "DATETIME", "TIMESTAMP" -> {
                if (size != null) throw new IllegalArgumentException("Explicit time precision requires a separate comparison");
                yield SchemaType.of(SchemaType.Kind.TIMESTAMP);
            }
            default -> throw new IllegalArgumentException("Unknown generated column type");
        };
        while (true) {
            if (input.take("NOT")) { input.require("NULL"); column.nullable = false; }
            else if (input.take("NULL")) column.nullable = true;
            else if (input.take("DEFAULT")) {
                column.defaultValue = defaultValue(input, column.type);
            } else if (input.take("ON")) {
                input.require("UPDATE"); input.require("CURRENT_TIMESTAMP"); column.onUpdate = true;
            } else if (input.take("COMMENT")) column.comment = input.string();
            else if (input.take("PRIMARY")) throw new IllegalArgumentException("Inline primary key is not generated by the dialect");
            else break;
        }
        return column;
    }

    private String defaultValue(Tokens input, SchemaType type) {
        boolean parenthesized = input.take("(");
        // Oracle 带类型的日期字面量、PostgreSQL E 字符串及 MySQL 元数据的字符集 introducer。
        if (input.peek("DATE") || input.peek("TIMESTAMP") || input.peek("E") || input.peek("_utf8mb4")) input.word();
        Token value = input.next();
        String result = value.kind == 's' ? (isText(type) ? value.value : normalizeDefault(value.value, type))
                : value.value.equalsIgnoreCase("NULL") ? null : normalizeDefault(value.value, type);
        if (parenthesized) input.require(")");
        return result;
    }

    private static List<String> identifiers(Tokens input) {
        input.require("(");
        List<String> result = new ArrayList<>();
        do { result.add(input.identifier()); } while (input.take(","));
        input.require(")");
        return List.copyOf(result);
    }

    private String normalizeDefault(String raw, SchemaType type) {
        if (raw == null) return null;
        String value = raw.trim();
        // JDBC 的 MySQL 字符串默认值已经不带引号，不能把文本 'NULL' 误判为 SQL NULL。
        boolean text = isText(type);
        if (mysql && text) return raw;
        value = value.replaceFirst("(?is)::[a-z][a-z0-9_ ]*(?:\\([0-9, ]+\\))?$", "").trim();
        while (value.startsWith("(") && value.endsWith(")")) value = value.substring(1, value.length() - 1).trim();
        if (value.equalsIgnoreCase("NULL")) return null;
        if (value.matches("(?i)CURRENT_TIMESTAMP(?:\\(0?\\))?")) return "CURRENT_TIMESTAMP";
        value = value.replaceFirst("(?i)^(DATE|TIMESTAMP)\\s+(?=')", "");
        if (value.startsWith("'") || value.startsWith("E'")) {
            var tokens = new Tokens(value, mysql);
            tokens.take("E");
            String literal = tokens.string();
            tokens.end();
            return text ? literal : normalizeDefault(literal, type);
        }
        if (!text) {
            try { return new BigDecimal(value).stripTrailingZeros().toPlainString(); }
            catch (NumberFormatException ignored) { /* 日期和函数值按原文比较，未知表达式不能猜测等价。 */ }
        }
        try {
            if (type.kind() == SchemaType.Kind.TIMESTAMP) return java.time.LocalDateTime.parse(value.replace(' ', 'T')).toString();
            if (type.kind() == SchemaType.Kind.DATE) return java.time.LocalDate.parse(value).toString();
        } catch (java.time.format.DateTimeParseException ignored) { /* 保留无法识别的表达式，不能放宽为任意日期等价。 */ }
        return value;
    }

    private static final class Target {
        final String operation, table;
        final List<Column> columns = new ArrayList<>();
        final List<Index> indexes = new ArrayList<>();
        String removed, engine, collation, comment;
        Target(String operation, String table) { this.operation = operation; this.table = table; }
    }
    private static final class Column {
        String name, defaultValue, comment;
        SchemaType type;
        boolean nullable = true, onUpdate;
    }
    private record Index(String name, List<String> columns, boolean unique, boolean primary) {}
    private record Token(char kind, String value) {}

    /** 分词保留字面量与标识符的区别，括号/逗号/关键字出现在注释或默认值文本中不会改变语法。 */
    private static final class Tokens {
        private final List<Token> tokens = new ArrayList<>();
        private int position;
        Tokens(String sql, boolean mysql) {
            for (int i = 0; i < sql.length();) {
                char c = sql.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == '\'' || c == '`' || c == '"') {
                    char quote = c;
                    boolean escape = mysql || (i > 0 && (sql.charAt(i - 1) == 'E' || sql.charAt(i - 1) == 'e'));
                    StringBuilder value = new StringBuilder();
                    boolean closed = false;
                    for (i++; i < sql.length(); i++) {
                        c = sql.charAt(i);
                        if (quote == '\'' && escape && c == '\\' && i + 1 < sql.length()) {
                            char escaped = sql.charAt(++i);
                            value.append(switch (escaped) {
                                case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case 'b' -> '\b';
                                case '0' -> '\0'; case 'Z' -> (char) 26; default -> escaped;
                            });
                            continue;
                        }
                        if (c == quote) {
                            if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) { value.append(c); i++; continue; }
                            i++; closed = true; break;
                        }
                        value.append(c);
                    }
                    if (!closed) throw new IllegalArgumentException("Unclosed DDL token");
                    tokens.add(new Token(quote == '\'' ? 's' : 'i', value.toString()));
                } else if ("(),=;".indexOf(c) >= 0) {
                    tokens.add(new Token('p', String.valueOf(c))); i++;
                } else {
                    int begin = i++;
                    while (i < sql.length() && !Character.isWhitespace(sql.charAt(i)) && "(),=;'`\"".indexOf(sql.charAt(i)) < 0) i++;
                    tokens.add(new Token('w', sql.substring(begin, i)));
                }
            }
        }
        Token next() { if (position >= tokens.size()) throw new IllegalArgumentException("Incomplete DDL"); return tokens.get(position++); }
        boolean peek(String value) { return position < tokens.size() && tokens.get(position).kind != 's'
                && tokens.get(position).kind != 'i' && tokens.get(position).value.equalsIgnoreCase(value); }
        boolean take(String value) { if (!peek(value)) return false; position++; return true; }
        void require(String value) { if (!take(value)) throw new IllegalArgumentException("Expected " + value); }
        String word() { Token token = next(); if (token.kind != 'w') throw new IllegalArgumentException("Expected keyword"); return token.value; }
        String identifier() { Token token = next(); if (token.kind != 'i' || !token.value.matches("[A-Za-z][A-Za-z0-9_]{0,62}")) throw new IllegalArgumentException("Expected quoted identifier"); return token.value.toLowerCase(Locale.ROOT); }
        String string() { Token token = next(); if (token.kind != 's') throw new IllegalArgumentException("Expected literal"); return token.value; }
        void end() { take(";"); if (position != tokens.size()) throw new IllegalArgumentException("Unrecognized DDL suffix"); }
    }
}
