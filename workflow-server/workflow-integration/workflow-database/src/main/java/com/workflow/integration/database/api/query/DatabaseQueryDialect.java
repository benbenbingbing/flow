package com.workflow.integration.database.api.query;

import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.sql.BoundSqlStatement;
import java.util.List;

/** 运行时查询的产品语法；业务模块负责条件、权限与排序，适配器只渲染语法。 */
public interface DatabaseQueryDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    DatabaseVendor vendor();

    /**
     * 引用来自已发布元数据的单个表名或列名；非法标识符必须拒绝，不能作为 SQL 执行。
     *
     * @param identifier 标识符，供本方法处理引用标识符时使用
     * @return 处理后的引用标识符文本，供调用方比较或展示
     */
    String quoteIdentifier(String identifier);

    /**
     * 引用结果别名并保持其大小写，避免 Oracle 大写物理名规则改变 Map 的键。
     *
     * @param alias {@code alias}，供本方法处理引用{@code alias}时使用
     * @return 处理后的引用{@code alias}文本，供调用方比较或展示
     */
    String quoteAlias(String alias);

    /**
     * 把有符号 64 位整数标识列转为不带填充的十进制文本，用于与字符串外键关联。
     * 仅接受单个列名或 alias.column，逐段校验并引用；不得传入任意表达式、用户 SQL、浮点数或 LOB 列。
     * 转换整数侧可保留字符外键的匹配规则，避免隐式数值比较误匹配前导零或丢失大整数精度。
     *
     * @param column 列，供本方法处理整数标识符文本时使用
     * @return 处理后的整数标识符文本文本，供调用方比较或展示
     */
    String integerIdentifierText(String column);

    /**
     * 为包含/前缀等文本匹配返回字段表达式，值与 LIKE 模式仍由调用方绑定。
     * 字符和 LOB 列保持原列；整数、数字布尔和定点数显示完整十进制，定点数使用可信
     * 元数据的小数位数。日期采用年-月-日，时间采用年-月-日 时:分:秒及产品时间类型的
     * 小数秒表示；不承担展示格式、时区转换或尾零归一化。NULL 保持 NULL。
     * column 只接受列名或 alias.column；type 必须与已发布的物理类型一致，禁止任意表达式。
     *
     * @param column 列，供本方法处理{@code pattern}值表达式时使用
     * @param type 类型标识，决定后续{@code pattern}值表达式采用的处理分支
     * @return 处理后的{@code pattern}值表达式文本，供调用方比较或展示
     */
    String patternValueExpression(String column, SchemaType type);

    /**
     * 按可信物理类型比较列与绑定参数，仅接受 =、<>、>、>=、<、<= 六种操作符。
     * column 仅接受列名或 alias.column；boundParameter 仅接受 ?、:name 或 MyBatis 属性绑定，
     * 不能传 SQL 表达式或字面量。TEXT/LARGE_TEXT 在 CLOB 产品上比较完整 LOB，不截断前缀。
     * 调用方按 comparisonJdbcType 绑定值；CLOB 保持 Java String 并使用框架 ClobTypeHandler。
     * NULL 不替换为空字符串或空 LOB，保留 SQL 未知结果；判空仍由调用方显式选择 IS NULL。
     *
     * @param column 列，供本方法处理比较判断条件时使用
     * @param kind 类型，供本方法处理比较判断条件时使用
     * @param operator 操作人，供本方法处理比较判断条件时使用
     * @param boundParameter 绑定参数，供本方法处理比较判断条件时使用
     * @return 处理后的比较判断条件文本，供调用方比较或展示
     */
    String comparisonPredicate(String column, SchemaType.Kind kind, String operator, String boundParameter);

    /**
     * 比较两列的完整值，kind 必须是两列共同的可信物理类型，不能用于隐式跨类型转换。
     * 列与操作符约束同 comparisonPredicate；允许分别使用不同表别名，供关联冲突检查使用。
     *
     * @param leftColumn 左侧列，供本方法处理列比较判断条件时使用
     * @param kind 类型，供本方法处理列比较判断条件时使用
     * @param operator 操作人，供本方法处理列比较判断条件时使用
     * @param rightColumn 右侧列，供本方法处理列比较判断条件时使用
     * @return 处理后的列比较判断条件文本，供调用方比较或展示
     */
    String columnComparisonPredicate(String leftColumn, SchemaType.Kind kind, String operator, String rightColumn);

    /**
     * 返回标量比较参数的 JDBC 类型名称，不改变 Java 业务类型、不执行绑定。
     * CLOB 产品的 TEXT/LARGE_TEXT 返回 CLOB，供框架以完整字符流绑定 String；普通文本保持 VARCHAR。
     * 数字布尔仍绑定转换后的 0/1 整数；NULL 也应按本类型绑定，不能生成 EMPTY_CLOB()。
     *
     * @param kind 类型，供本方法处理比较JDBC类型时使用
     * @return 处理后的比较JDBC类型文本，供调用方比较或展示
     */
    String comparisonJdbcType(SchemaType.Kind kind);

    /**
     * 按列的逻辑存储类型生成判空条件；empty=false 返回非空条件。
     * 数值、布尔、日期只以 SQL NULL 为空；字符及大文本以 NULL、零长度或仅含 Java
     * Character.isWhitespace 定义的字符为空。不得截取 LOB 前缀后判断，以免丢失尾部内容。
     * column 仅接受列名或 alias.column；kind 必须来自可信发布元数据，不接收任意 SQL。
     *
     * @param column 列，供本方法处理空值判断条件时使用
     * @param kind 类型，供本方法处理空值判断条件时使用
     * @param empty 空，供本方法处理空值判断条件时使用
     * @return 处理后的空值判断条件文本，供调用方比较或展示
     */
    String emptyValuePredicate(String column, SchemaType.Kind kind, boolean empty);

    /**
     * 对可信字符列生成区分大小写的正则谓词，模式必须通过参数绑定传入。
     * patternParameter 只接受 ?、命名参数或带可选 jdbcType=VARCHAR 的 MyBatis 参数；
     * 调用方负责使用各产品共同支持的 POSIX 子集及模式长度上限，不接收用户正则。
     * 此入口不进行业务归一化，也不保证各产品的 Unicode 大小写或排序规则一致。
     *
     * @param column 列，供本方法处理{@code regular}表达式判断条件时使用
     * @param patternParameter {@code pattern}参数，供本方法处理{@code regular}表达式判断条件时使用
     * @return 处理后的{@code regular}表达式判断条件文本，供调用方比较或展示
     */
    String regularExpressionPredicate(String column, String patternParameter);

    /**
     * 对已校验的条件片段生成单行 0/1 查询；值仍由调用方绑定。
     * 只补齐产品所需的虚表，不解析或校验业务条件；不得传入未经校验的请求 SQL。
     *
     * @param predicate 判断条件，供本方法处理布尔值值查询时使用
     * @return 处理后的布尔值值查询文本，供调用方比较或展示
     */
    String booleanValueQuery(String predicate);

    /**
     * 为已排序、尚未分页的普通 SELECT 追加分页，不重写业务 SQL。
     * offset/limit 只能为非负整数字面量、命名参数或 MyBatis 绑定占位符；不得传用户 SQL。
     * 调用方须提供稳定排序并校验分页数值；锁定查询不使用此接口。
     *
     * @param sql SQL，供本方法处理{@code paginate}时使用
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code paginate}文本，供调用方比较或展示
     */
    String paginate(String sql, String offset, String limit);

    /**
     * 生成带前导空格的普通 SELECT 分页子句，供 MyBatis 注解/XML 在明确位置插入。
     * 参数约束同 paginate；不得用于 UPDATE/DELETE 或带 FOR UPDATE 的锁定查询。
     *
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code pagination}{@code clause}文本，供调用方比较或展示
     */
    String paginationClause(String offset, String limit);

    /**
     * JDBC 分页：保留原有绑定值，由方言追加并排序 offset/limit 参数；数值必须非负。
     *
     * @param sql SQL，供本方法处理{@code paginate}时使用
     * @param parameters 参数集合，供本方法处理{@code paginate}时使用
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code paginate}结果，供调用方继续处理
     */
    BoundSqlStatement paginate(String sql, List<?> parameters, long offset, long limit);

    /**
     * 生成单表取首行并锁定的查询，返回所有物理列。primaryKey 为单列非空唯一键，
     * orderBy 必须包含该键以打破平局；predicate 为服务端固定条件，值使用命名/MyBatis 绑定。
     * Oracle 模板会重复绑定条件，禁止匿名问号；不支持聚合、JOIN 或 SKIP LOCKED 队列领取。
     * 调用方负责事务，且不能依赖缺失行的 gap lock 阻止跨产品并发插入。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param predicate 判断条件，供本方法处理首个更新时使用
     * @param orderBy 顺序，供本方法处理首个更新时使用
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     * @return 处理后的首个更新文本，供调用方比较或展示
     */
    String firstForUpdate(String table, String predicate, List<DatabaseSort> orderBy, String primaryKey);

    /**
     * 保护已读行直到事务结束，阻止其他事务修改。优先使用行共享锁；缺少对应能力的产品
     * 使用 FOR UPDATE 保证互斥，可能串行化读守卫。只适用于普通基表查询，不保证防止幻行。
     *
     * @return 读取后的保护{@code clause}文本，供调用方比较或展示
     */
    String readGuardClause();
}
