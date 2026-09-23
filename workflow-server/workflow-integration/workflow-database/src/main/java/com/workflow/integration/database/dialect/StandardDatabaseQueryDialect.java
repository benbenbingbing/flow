package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.sql.BoundSqlStatement;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.schema.SchemaDdlDialect;
import com.workflow.integration.database.api.query.DatabaseSort;
import com.workflow.integration.database.api.schema.SchemaType;
import java.util.Locale;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 七种产品共用经过产品分派的查询语法；物理标识符与 DDL 使用完全相同的规则。 */
public final class StandardDatabaseQueryDialect implements DatabaseQueryDialect {
    private static final Pattern BOUND_NUMBER = Pattern.compile("(?:[0-9]+|:[A-Za-z_][A-Za-z0-9_]*|#\\{[A-Za-z_][A-Za-z0-9_.]*})");
    private static final Pattern ALIAS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");
    private static final Pattern BOUND_TEXT = Pattern.compile(
            "(?:\\?|:[A-Za-z_][A-Za-z0-9_]*|#\\{[A-Za-z_][A-Za-z0-9_.]*(?:,jdbcType=VARCHAR)?})");
    private static final Set<String> COMPARISON_OPERATORS = Set.of("=", "<>", ">", ">=", "<", "<=");
    // 只开放标准绑定占位符及固定 JDBC 类型/框架大文本处理器，不允许借参数片段注入表达式或任意处理器。
    private static final Pattern BOUND_COMPARISON = Pattern.compile(
            "(?:\\?|:[A-Za-z_][A-Za-z0-9_]*|#\\{[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*"
                    + "(?:,jdbcType=(?:VARCHAR|INTEGER|BIGINT|DECIMAL|DATE|TIMESTAMP|CLOB))?"
                    + "(?:,typeHandler=org\\.apache\\.ibatis\\.type\\.ClobTypeHandler)?})");
    // Java 17 的 Character.isWhitespace 集合；不使用各数据库含义不同的 [:space:]，
    // 尤其 NBSP、窄 NBSP 和零宽空格在业务规则中并非空白。模式只含固定字符，不含用户值。
    private static final String NON_BLANK_PATTERN = "[^\t\n\u000b\f\r\u001c\u001d\u001e\u001f "
            + "\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200a"
            + "\u2028\u2029\u205f\u3000]";
    private final SchemaDdlDialect schema;

    /**
     * 初始化标准数据库查询方言，保存构造参数供后续方法使用。
     *
     * @param schema 结构依赖，保存到当前对象供后续业务方法调用
     */
    public StandardDatabaseQueryDialect(SchemaDdlDialect schema) { this.schema = schema; }

    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return schema.vendor(); }
    /**
     * 生成引用标识符文本，供后续匹配或展示。
     *
     * @param identifier 标识符，供本方法处理引用标识符时使用
     * @return 处理后的引用标识符文本，供调用方比较或展示
     */
    @Override public String quoteIdentifier(String identifier) { return schema.quoteIdentifier(identifier); }

    /**
     * 生成整数标识符文本文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code quoteColumn} 的输入影响后续处理
     * @return 处理后的整数标识符文本文本，供调用方比较或展示
     */
    @Override public String integerIdentifierText(String column) {
        String quoted = quoteColumn(column);
        String target = switch (vendor()) {
            case MYSQL, OCEANBASE_MYSQL -> "CHAR";
            case ORACLE, OCEANBASE_ORACLE -> "VARCHAR2(64)";
            case POSTGRESQL, KINGBASE, DM -> "VARCHAR(64)";
        };
        // 64 字符覆盖有符号 BIGINT 的完整十进制表示；只允许整数，不承担任意数值格式化。
        return "CAST(" + quoted + " AS " + target + ")";
    }

    /**
     * 生成{@code pattern}值表达式文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code quoteColumn} 的输入影响后续处理
     * @param type 类型标识，决定后续{@code pattern}值表达式采用的处理分支
     * @return 处理后的{@code pattern}值表达式文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override public String patternValueExpression(String column, SchemaType type) {
        String quoted = quoteColumn(column);
        if (type == null) throw new IllegalArgumentException("文本匹配必须提供列类型");
        if (type.kind() == SchemaType.Kind.STRING || type.kind() == SchemaType.Kind.TEXT
                || type.kind() == SchemaType.Kind.LARGE_TEXT) return quoted;
        // MySQL 原 LIKE 的隐式转换就是完整字符表示；不指定长度，保留大整数、定点尾零及小数秒。
        if (vendor() == DatabaseVendor.MYSQL || vendor() == DatabaseVendor.OCEANBASE_MYSQL)
            return "CAST(" + quoted + " AS CHAR)";
        boolean postgres = vendor() == DatabaseVendor.POSTGRESQL || vendor() == DatabaseVendor.KINGBASE;
        return switch (type.kind()) {
            case DATE -> "TO_CHAR(" + quoted + ", 'YYYY-MM-DD')";
            // 不使用默认日期格式，避免 DateStyle/NLS_DATE_FORMAT 改变年月日顺序。
            // PostgreSQL 时间最多微秒；Oracle 系列 FF 按时间类型输出小数秒，不先截成 DATE。
            case TIMESTAMP -> "TO_CHAR(" + quoted + ", 'YYYY-MM-DD HH24:MI:SS." + (postgres ? "US" : "FF") + "')";
            case INTEGER, LONG, DECIMAL, BOOLEAN, BYTE -> postgres
                    ? "CAST(" + quoted + " AS TEXT)"
                    : "TO_CHAR(" + quoted + ", '" + decimalPattern(type) + "', 'NLS_NUMERIC_CHARACTERS=''.,''')";
            default -> throw new IllegalArgumentException("不支持文本匹配的列类型");
        };
    }

    /**
     * 生成比较判断条件文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code renderComparison} 的输入影响后续处理
     * @param kind 类型，供本方法处理比较判断条件时使用
     * @param operator 操作人，供本方法处理比较判断条件时使用
     * @param boundParameter 绑定参数，供本方法处理比较判断条件时使用
     * @return 处理后的比较判断条件文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override public String comparisonPredicate(String column, SchemaType.Kind kind, String operator,
                                                String boundParameter) {
        if (boundParameter == null || !BOUND_COMPARISON.matcher(boundParameter).matches()) {
            throw new IllegalArgumentException("比较值必须使用标准绑定参数");
        }
        return renderComparison(quoteColumn(column), kind, operator, boundParameter);
    }

    /**
     * 生成列比较判断条件文本，供后续匹配或展示。
     *
     * @param leftColumn 左侧列，作为 {@code renderComparison} 的输入影响后续处理
     * @param kind 类型，供本方法处理列比较判断条件时使用
     * @param operator 操作人，供本方法处理列比较判断条件时使用
     * @param rightColumn 右侧列，供本方法处理列比较判断条件时使用
     * @return 处理后的列比较判断条件文本，供调用方比较或展示
     */
    @Override public String columnComparisonPredicate(String leftColumn, SchemaType.Kind kind, String operator,
                                                      String rightColumn) {
        return renderComparison(quoteColumn(leftColumn), kind, operator, quoteColumn(rightColumn));
    }

    /**
     * 生成比较JDBC类型文本，供后续匹配或展示。
     *
     * @param kind 类型，供本方法处理比较JDBC类型时使用
     * @return 处理后的比较JDBC类型文本，供调用方比较或展示
     */
    @Override public String comparisonJdbcType(SchemaType.Kind kind) {
        if (usesClobComparison(kind)) return "CLOB";
        return switch (kind) {
            case STRING, TEXT, LARGE_TEXT -> "VARCHAR";
            case INTEGER, BYTE, BOOLEAN -> "INTEGER";
            case LONG -> "BIGINT";
            case DECIMAL -> "DECIMAL";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP";
        };
    }

    /**
     * COMPARE 默认覆盖完整 LOB，返回负/零/正值；比较结果与 0 比较可保留六种关系及 NULL 三值逻辑。
     * 不使用 SUBSTR、TO_CHAR 或有限长度 VARCHAR，避免内容仅在长前缀之后不同却被误判为相等。
     *
     * @param left 左侧，供本方法处理{@code render}比较时使用
     * @param kind 类型，作为 {@code usesClobComparison} 的输入影响后续处理
     * @param operator 操作人，供本方法处理{@code render}比较时使用
     * @param right 右侧，供本方法处理{@code render}比较时使用
     * @return 处理后的{@code render}比较文本，供调用方比较或展示
     */
    private String renderComparison(String left, SchemaType.Kind kind, String operator, String right) {
        if (operator == null || !COMPARISON_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("仅支持标准标量比较操作符");
        }
        return usesClobComparison(kind)
                ? "DBMS_LOB.COMPARE(" + left + ", " + right + ") " + operator + " 0"
                : left + " " + operator + " " + right;
    }

    /**
     * 判断使用{@code clob}比较条件是否成立，供调用方选择后续分支。
     *
     * @param kind 类型，供本方法处理使用{@code clob}比较时使用
     * @return 使用{@code clob}比较条件成立时为 true，否则为 false
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private boolean usesClobComparison(SchemaType.Kind kind) {
        if (kind == null) throw new IllegalArgumentException("比较字段缺少存储类型");
        return (kind == SchemaType.Kind.TEXT || kind == SchemaType.Kind.LARGE_TEXT)
                && (vendor() == DatabaseVendor.ORACLE || vendor() == DatabaseVendor.DM
                        || vendor() == DatabaseVendor.OCEANBASE_ORACLE);
    }

    /**
     * 固定全部有效位及小数尾零，避免 TM9 的科学计数法、NLS 分隔符或 VARCHAR 长度截断。
     *
     * @param type 类型标识，决定后续{@code decimal}{@code pattern}采用的处理分支
     * @return 处理后的{@code decimal}{@code pattern}文本，供调用方比较或展示
     */
    private String decimalPattern(SchemaType type) {
        int precision = switch (type.kind()) {
            case INTEGER -> 10;
            case LONG -> 19;
            case BYTE -> 3;
            case BOOLEAN -> 1;
            case DECIMAL -> type.precision();
            default -> throw new IllegalArgumentException("仅数值类型使用数值格式");
        };
        int scale = type.kind() == SchemaType.Kind.DECIMAL ? type.scale() : 0;
        return "FM" + "9".repeat(Math.max(precision - scale - 1, 0)) + "0"
                + (scale == 0 ? "" : "." + "0".repeat(scale));
    }

    /**
     * 生成空值判断条件文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code quoteColumn} 的输入影响后续处理
     * @param kind 类型，供本方法处理空值判断条件时使用
     * @param empty 空，供本方法处理空值判断条件时使用
     * @return 处理后的空值判断条件文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override public String emptyValuePredicate(String column, SchemaType.Kind kind, boolean empty) {
        String quoted = quoteColumn(column);
        if (kind == null) throw new IllegalArgumentException("判空必须提供列类型");
        boolean text = kind == SchemaType.Kind.STRING || kind == SchemaType.Kind.TEXT || kind == SchemaType.Kind.LARGE_TEXT;
        if (!text) return quoted + (empty ? " IS NULL" : " IS NOT NULL");
        String nonBlank = switch (vendor()) {
            case POSTGRESQL, KINGBASE -> quoted + " ~ '" + NON_BLANK_PATTERN + "'";
            case MYSQL, OCEANBASE_MYSQL, ORACLE, DM, OCEANBASE_ORACLE ->
                    "REGEXP_LIKE(" + quoted + ", '" + NON_BLANK_PATTERN + "', 'c')";
        };
        // 部分产品的正则函数把零长度文本视为 NULL，因此零长度与 NULL 分别显式处理。
        // LENGTH 仅比较是否为零，不依赖各产品按字节还是字符计数，也不把 CLOB 转成 VARCHAR。
        return empty
                ? "(" + quoted + " IS NULL OR LENGTH(" + quoted + ") = 0 OR NOT (" + nonBlank + "))"
                : "(" + quoted + " IS NOT NULL AND LENGTH(" + quoted + ") > 0 AND (" + nonBlank + "))";
    }

    /**
     * 生成布尔值值查询文本，供后续匹配或展示。
     *
     * @param predicate 判断条件，作为 {@code WHEN} 的输入影响后续处理
     * @return 处理后的布尔值值查询文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override public String booleanValueQuery(String predicate) {
        if (predicate == null || predicate.isBlank()) throw new IllegalArgumentException("条件不能为空");
        String query = "SELECT CASE WHEN (" + predicate + ") THEN 1 ELSE 0 END";
        return switch (vendor()) {
            case ORACLE, DM, OCEANBASE_ORACLE -> query + " FROM DUAL";
            default -> query;
        };
    }

    /**
     * 生成{@code regular}表达式判断条件文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code quoteColumn} 的输入影响后续处理
     * @param patternParameter {@code pattern}参数，作为 {@code REGEXP_LIKE} 的输入影响后续处理
     * @return 处理后的{@code regular}表达式判断条件文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override public String regularExpressionPredicate(String column, String patternParameter) {
        String quoted = quoteColumn(column);
        if (patternParameter == null || !BOUND_TEXT.matcher(patternParameter).matches()) {
            throw new IllegalArgumentException("正则模式必须使用文本绑定参数");
        }
        return switch (vendor()) {
            case POSTGRESQL, KINGBASE -> quoted + " ~ " + patternParameter;
            // Connector/J 在 NO_BACKSLASH_ESCAPES 下可能把客户端预编译文本绑定编码为 binary。
            // MySQL 正则拒绝 binary 模式，只转换绑定参数，不转换/截断被比较的列。
            // 显式 Unicode 二进制比较保证本接口的区分字符语义，并避免 CAST 与列同为 IMPLICIT
            // 而触发混合排序规则错误；此产品规则仅留在 MySQL 方言，不进入业务 SQL。
            case MYSQL, OCEANBASE_MYSQL ->
                    "REGEXP_LIKE(" + quoted + ", CAST(" + patternParameter
                            + " AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin, 'c')";
            case ORACLE, DM, OCEANBASE_ORACLE ->
                    "REGEXP_LIKE(" + quoted + ", " + patternParameter + ", 'c')";
        };
    }

    /**
     * 对固定列引用逐段引用，禁止调用方把表达式或未校验 SQL 放入类型转换与判空入口。
     *
     * @param column 列，供本方法处理引用列时使用
     * @return 处理后的引用列文本，供调用方比较或展示
     */
    private String quoteColumn(String column) {
        if (column == null) throw new IllegalArgumentException("列名不能为空");
        String[] parts = column.split("\\.", -1);
        if (parts.length < 1 || parts.length > 2) throw new IllegalArgumentException("仅支持列名或 alias.column");
        var quoted = new ArrayList<String>();
        for (String part : parts) quoted.add(quoteIdentifier(part));
        return String.join(".", quoted);
    }

    /**
     * 生成引用{@code alias}文本，供后续匹配或展示。
     *
     * @param alias {@code alias}，供本方法处理引用{@code alias}时使用
     * @return 处理后的引用{@code alias}文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override public String quoteAlias(String alias) {
        if (alias == null || !ALIAS.matcher(alias).matches()) throw new IllegalArgumentException("非法查询别名");
        String delimiter = switch (vendor()) {
            case MYSQL, OCEANBASE_MYSQL -> "`";
            default -> "\"";
        };
        return delimiter + alias + delimiter;
    }

    /**
     * 只拼接受控分页模板。MyBatis / NamedParameterJdbcTemplate 在模板生成后才绑定
     * 参数，因而 PostgreSQL 的 limit/offset 顺序变化不会交换值。拒绝匿名问号，
     * 避免 JDBC 调用方仍按原顺序绑定而交换偏移和条数。
     *
     * @param sql SQL，供本方法处理{@code paginate}时使用
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code paginate}文本，供调用方比较或展示
     */
    @Override public String paginate(String sql, String offset, String limit) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("分页查询不能为空");
        return sql + paginationClause(offset, limit);
    }

    /**
     * 生成{@code pagination}{@code clause}文本，供后续匹配或展示。
     *
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code pagination}{@code clause}文本，供调用方比较或展示
     */
    @Override public String paginationClause(String offset, String limit) {
        requireBoundNumber(offset);
        requireBoundNumber(limit);
        return pageSql("", offset, limit);
    }

    /**
     * JDBC 的匿名参数必须随分页模板一起排序，原查询的条件参数顺序保持不变。
     *
     * @param sql SQL，作为 {@code BoundSqlStatement} 的输入影响后续处理
     * @param parameters 参数集合，供本方法处理{@code paginate}时使用
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code paginate}结果，供调用方继续处理
     */
    @Override public BoundSqlStatement paginate(String sql, List<?> parameters, long offset, long limit) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("分页查询不能为空");
        if (offset < 0 || limit < 0) throw new IllegalArgumentException("分页偏移和条数不能为负数");
        List<Object> bound = new ArrayList<>(parameters);
        if (vendor() == DatabaseVendor.POSTGRESQL || vendor() == DatabaseVendor.KINGBASE) {
            bound.add(limit); bound.add(offset);
        } else {
            bound.add(offset); bound.add(limit);
        }
        return new BoundSqlStatement(pageSql(sql, "?", "?"), bound);
    }

    /**
     * 生成首个更新文本，供后续匹配或展示。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param predicate 判断条件，作为 {@code WHERE} 的输入影响后续处理
     * @param orderBy 顺序，供本方法处理首个更新时使用
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     * @return 处理后的首个更新文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override public String firstForUpdate(String table, String predicate, List<DatabaseSort> orderBy, String primaryKey) {
        String target = quoteIdentifier(table);
        String key = quoteIdentifier(primaryKey);
        if (predicate == null || predicate.isBlank() || predicate.contains("?")) {
            throw new IllegalArgumentException("首行锁定查询必须提供固定条件并使用命名绑定");
        }
        if (orderBy == null || orderBy.isEmpty()) throw new IllegalArgumentException("首行锁定查询必须提供稳定排序");
        var seen = new HashSet<String>();
        var order = new ArrayList<String>();
        for (var item : orderBy) {
            String column = quoteIdentifier(item.column());
            if (!seen.add(item.column().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("排序列不能重复");
            order.add(column + (item.descending() ? " DESC" : " ASC"));
        }
        if (!seen.contains(primaryKey.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("首行排序必须包含唯一键");
        String from = " FROM " + target + " WHERE (" + predicate + ")";
        String ordered = from + " ORDER BY " + String.join(", ", order);
        return switch (vendor()) {
            case MYSQL, OCEANBASE_MYSQL -> "SELECT *" + ordered + " LIMIT 1 FOR UPDATE";
            case POSTGRESQL, KINGBASE -> "SELECT *" + ordered + " LIMIT 1 FOR UPDATE";
            case DM -> "SELECT TOP 1 *" + ordered + " FOR UPDATE";
            // 行数限制只出现在候选键子查询，外层始终锁基表，避免对不可更新视图加锁。
            // 重复外层条件以排除等待锁期间变为不符合条件的行；本接口不承担队列抢占。
            case ORACLE, OCEANBASE_ORACLE -> "SELECT *" + from + " AND " + key
                    + " = (SELECT " + key + " FROM (SELECT " + key + ordered + ") WHERE ROWNUM = 1) FOR UPDATE";
        };
    }

    /**
     * 分页查询SQL；查询结果供调用方展示或继续处理。
     *
     * @param sql SQL，供本方法分页查询SQL时使用
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 分页查询后的SQL文本，供调用方比较或展示
     */
    private String pageSql(String sql, String offset, String limit) {
        return switch (vendor()) {
            case MYSQL, OCEANBASE_MYSQL -> sql + " LIMIT " + offset + ", " + limit;
            case POSTGRESQL, KINGBASE -> sql + " LIMIT " + limit + " OFFSET " + offset;
            case ORACLE, DM, OCEANBASE_ORACLE -> sql + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
        };
    }

    /**
     * 读取保护{@code clause}；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的保护{@code clause}文本，供调用方比较或展示
     */
    @Override public String readGuardClause() {
        return switch (vendor()) {
            case MYSQL, POSTGRESQL, KINGBASE -> " FOR SHARE";
            // OB MySQL 官方支持此语法，但部分版本以写锁模拟，共享读守卫也可能相互等待。
            case OCEANBASE_MYSQL -> " LOCK IN SHARE MODE";
            // Oracle/DM 不借助表锁模拟行共享锁，避免放大到整张业务定义表。
            case ORACLE, DM, OCEANBASE_ORACLE -> " FOR UPDATE";
        };
    }

    /**
     * 校验并获取绑定数值；不满足约束时阻止后续处理。
     *
     * @param expression 表达式，供本方法校验并获取绑定数值时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireBoundNumber(String expression) {
        if (expression == null || !BOUND_NUMBER.matcher(expression).matches()) {
            throw new IllegalArgumentException("分页仅接受绑定参数或非负整数");
        }
    }
}
