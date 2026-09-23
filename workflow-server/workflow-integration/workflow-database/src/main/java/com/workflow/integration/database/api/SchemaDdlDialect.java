package com.workflow.integration.database.api;

import com.workflow.integration.database.api.*;
import java.util.List;

/**
 * 将通用表结构转换为目标数据库 DDL。只生成语句，不执行、不查询业务元数据。
 * 每个返回元素是一次 JDBC 执行单位，调用方必须保持顺序并在失败时中止发布。
 */
public interface SchemaDdlDialect {
    DatabaseVendor vendor();
    String quoteIdentifier(String identifier);
    String typeSql(SchemaType type);
    String defaultClause(SchemaColumn column);
    String columnDefinition(SchemaColumn column);
    List<String> createTable(SchemaTable table);
    List<String> addColumn(String table, SchemaColumn column);
    List<String> modifyColumn(String table, SchemaColumn column);
    List<String> dropColumn(String table, String column);
    /** 删除表及其审计辅助对象，调用方需提供系统审计列定义。 */
    List<String> dropTable(SchemaTable table);
    boolean supportsCreateIfNotExists();

    /** 在专用 DDL 通道执行前校验；拒绝多语句及未由方言支持的存储程序。 */
    void validateStatement(String ddl);

    /** 对比 JDBC/数据库元数据中的类型及尺寸，避免同义类型触发反复改表。 */
    boolean columnTypeMatches(SchemaType expected, String actualType, Long length, Integer precision, Integer scale);
}
