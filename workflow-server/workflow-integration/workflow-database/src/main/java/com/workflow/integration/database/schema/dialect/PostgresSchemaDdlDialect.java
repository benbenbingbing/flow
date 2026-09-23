package com.workflow.integration.database.schema.dialect;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.schema.SchemaColumn;
import com.workflow.integration.database.api.schema.SchemaTable;
import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.integration.database.schema.template.AuditTimestampDdl;
import java.util.*;

/** PostgreSQL 方言，布尔存储沿用应用的 0/1 约定，避免现有 deleted = 0 查询失效。 */
public class PostgresSchemaDdlDialect extends AbstractSchemaDdlDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return DatabaseVendor.POSTGRESQL; }
    /**
     * 判断是否支持创建条件非存在；判断结果决定调用方的后续分支。
     *
     * @return 创建条件非存在条件成立时为 true，否则为 false
     */
    @Override public boolean supportsCreateIfNotExists() { return true; }
    /**
     * 生成类型SQL文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续类型SQL采用的处理分支
     * @return 处理后的类型SQL文本，供调用方比较或展示
     */
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
    /**
     * 生成字面值文本，供后续匹配或展示。
     *
     * @param text 待处理字面值的原始输入，结果供调用方继续使用
     * @return 处理后的字面值文本，供调用方比较或展示
     */
    @Override protected String literal(String text) {
        // E 字符串显式指定转义规则，不依赖 standard_conforming_strings 的连接配置。
        return text.contains("\\") ? "E" + super.literal(text.replace("\\", "\\\\")) : super.literal(text);
    }
    /**
     * 整理{@code modify}列数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，作为 {@code result.add} 的输入影响后续处理
     * @return {@code postgres}结构DDL方言集合，供调用方遍历或展示
     */
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
    /**
     * 审计时间戳；供后续追溯或审计使用。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，作为 {@code quoteIdentifier} 的输入影响后续处理
     * @return {@code postgres}结构DDL方言集合，供调用方遍历或展示
     */
    @Override protected List<String> auditTimestamp(String table, SchemaColumn column) {
        return column.refreshTimestampOnUpdate() ? AuditTimestampDdl.postgres(quoteIdentifier(table),
                quoteIdentifier(column.name()), quoteIdentifier(objectName("fn", table, column.name())),
                quoteIdentifier(objectName("tr", table, column.name()))) : List.of();
    }
    /**
     * 整理{@code drop}表数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return {@code postgres}结构DDL方言集合，供调用方遍历或展示
     */
    @Override public List<String> dropTable(SchemaTable table) {
        var result = new ArrayList<>(super.dropTable(table));
        // PostgreSQL 删除表会删除触发器，但函数是独立对象，需要显式清理。
        table.columns().stream().filter(SchemaColumn::refreshTimestampOnUpdate).forEach(column ->
                result.add(AuditTimestampDdl.dropPostgresFunction(quoteIdentifier(objectName("fn", table.name(), column.name())))));
        return List.copyOf(result);
    }
    /**
     * 整理{@code drop}列数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理{@code drop}列时使用
     * @return {@code postgres}结构DDL方言集合，供调用方遍历或展示
     */
    @Override public List<String> dropColumn(String table, String column) {
        return List.of(AuditTimestampDdl.dropPostgresTrigger(quoteIdentifier(table), quoteIdentifier(objectName("tr", table, column))),
                super.dropColumn(table, column).get(0),
                AuditTimestampDdl.dropPostgresFunction(quoteIdentifier(objectName("fn", table, column))));
    }
    /**
     * 规范化类型；输出作为后续校验或处理的输入。
     *
     * @param type 类型标识，决定后续类型采用的处理分支
     * @return 规范化后的类型文本，供调用方比较或展示
     */
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
