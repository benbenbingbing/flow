package com.workflow.integration.database.api.schema;

import com.workflow.integration.database.api.DatabaseVendor;

import java.util.List;

/**
 * 将通用表结构转换为目标数据库 DDL。只生成语句，不执行、不查询业务元数据。
 * 每个返回元素是一次 JDBC 执行单位，调用方必须保持顺序并在失败时中止发布。
 */
public interface SchemaDdlDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    DatabaseVendor vendor();
    /**
     * 生成引用标识符文本，供后续匹配或展示。
     *
     * @param identifier 标识符，供本方法处理引用标识符时使用
     * @return 处理后的引用标识符文本，供调用方比较或展示
     */
    String quoteIdentifier(String identifier);
    /**
     * 生成类型SQL文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续类型SQL采用的处理分支
     * @return 处理后的类型SQL文本，供调用方比较或展示
     */
    String typeSql(SchemaType type);
    /**
     * 生成默认{@code clause}文本，供后续匹配或展示。
     *
     * @param column 列，供本方法处理默认{@code clause}时使用
     * @return 处理后的默认{@code clause}文本，供调用方比较或展示
     */
    String defaultClause(SchemaColumn column);
    /**
     * 生成列定义文本，供后续匹配或展示。
     *
     * @param column 列，供本方法处理列定义时使用
     * @return 处理后的列定义文本，供调用方比较或展示
     */
    String columnDefinition(SchemaColumn column);
    /**
     * 创建表；结果供后续流程传递或持久化。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 结构DDL方言集合，供调用方遍历或展示
     */
    List<String> createTable(SchemaTable table);
    /**
     * 添加列；结果供后续流程传递或持久化。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法添加列时使用
     * @return 结构DDL方言集合，供调用方遍历或展示
     */
    List<String> addColumn(String table, SchemaColumn column);
    /**
     * 整理{@code modify}列数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理{@code modify}列时使用
     * @return 结构DDL方言集合，供调用方遍历或展示
     */
    List<String> modifyColumn(String table, SchemaColumn column);
    /**
     * 整理{@code drop}列数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理{@code drop}列时使用
     * @return 结构DDL方言集合，供调用方遍历或展示
     */
    List<String> dropColumn(String table, String column);
    /**
     * 删除表及其审计辅助对象，调用方需提供系统审计列定义。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 结构DDL方言集合，供调用方遍历或展示
     */
    List<String> dropTable(SchemaTable table);
    /**
     * 判断是否支持创建条件非存在；判断结果决定调用方的后续分支。
     *
     * @return 创建条件非存在条件成立时为 true，否则为 false
     */
    boolean supportsCreateIfNotExists();

    /**
     * 在专用 DDL 通道执行前校验；拒绝多语句及未由方言支持的存储程序。
     *
     * @param ddl DDL，供本方法校验{@code statement}时使用
     */
    void validateStatement(String ddl);

    /**
     * 对比 JDBC/数据库元数据中的类型及尺寸，避免同义类型触发反复改表。
     *
     * @param expected 预期，供本方法处理列类型匹配时使用
     * @param actualType 实际类型标识，决定后续列类型匹配采用的处理分支
     * @param length 长度，供本方法处理列类型匹配时使用
     * @param precision {@code precision}，供本方法处理列类型匹配时使用
     * @param scale {@code scale}，供本方法处理列类型匹配时使用
     * @return 列类型匹配条件成立时为 true，否则为 false
     */
    boolean columnTypeMatches(SchemaType expected, String actualType, Long length, Integer precision, Integer scale);
}
