package com.workflow.integration.database.schema.dialect;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.schema.SchemaColumn;
import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.integration.database.schema.template.AuditTimestampDdl;
import java.util.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Oracle 12.2+ 方言（63 字符标识符需要兼容级别 >= 12.2）。 */
public class OracleSchemaDdlDialect extends AbstractSchemaDdlDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return DatabaseVendor.ORACLE; }
    /**
     * 判断是否支持创建条件非存在；判断结果决定调用方的后续分支。
     *
     * @return 创建条件非存在条件成立时为 true，否则为 false
     */
    @Override public boolean supportsCreateIfNotExists() { return false; }
    /**
     * 判断{@code uppercase}{@code identifiers}条件是否成立，供调用方选择后续分支。
     *
     * @return {@code uppercase}{@code identifiers}条件成立时为 true，否则为 false
     */
    @Override protected boolean uppercaseIdentifiers() { return true; }
    /**
     * 生成类型SQL文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续类型SQL采用的处理分支
     * @return 处理后的类型SQL文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
    /**
     * 生成默认字面值文本，供后续匹配或展示。
     *
     * @param column 列，供本方法处理默认字面值时使用
     * @return 处理后的默认字面值文本，供调用方比较或展示
     */
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
    /**
     * 整理{@code modify}列数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，作为 {@code result.addAll} 的输入影响后续处理
     * @return {@code oracle}结构DDL方言集合，供调用方遍历或展示
     */
    @Override public List<String> modifyColumn(String table, SchemaColumn column) {
        var result = new ArrayList<String>();
        // 动态业务字段统一可空；避免反复设置 NULL 导致 ORA-01451。非空列在建表时声明。
        result.add("ALTER TABLE " + quoteIdentifier(table) + " MODIFY (" + quoteIdentifier(column.name())
                + " " + typeSql(column.type()) + defaultClause(column) + ")");
        result.addAll(columnComment(table, column));
        result.addAll(auditTimestamp(table, column));
        return List.copyOf(result);
    }
    /**
     * 审计时间戳；供后续追溯或审计使用。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法审计时间戳时使用
     * @return {@code oracle}结构DDL方言集合，供调用方遍历或展示
     */
    @Override protected List<String> auditTimestamp(String table, SchemaColumn column) {
        return column.refreshTimestampOnUpdate() ? AuditTimestampDdl.oracle(quoteIdentifier(table),
                quoteIdentifier(column.name()), quoteIdentifier(objectName("tr", table, column.name()))) : List.of();
    }
    /**
     * 判断列类型匹配条件是否成立，供调用方选择后续分支。
     *
     * @param expected 预期，供本方法处理列类型匹配时使用
     * @param actual 实际，供本方法处理列类型匹配时使用
     * @param length 长度，供本方法处理列类型匹配时使用
     * @param precision {@code precision}，供本方法处理列类型匹配时使用
     * @param scale {@code scale}，供本方法处理列类型匹配时使用
     * @return 列类型匹配条件成立时为 true，否则为 false
     */
    @Override public boolean columnTypeMatches(SchemaType expected, String actual, Long length, Integer precision, Integer scale) {
        if (!super.columnTypeMatches(expected, actual, length, precision, scale)) return false;
        Integer expectedPrecision = switch (expected.kind()) {
            case INTEGER -> 10; case LONG -> 19; case BOOLEAN -> 1; case BYTE -> 3; default -> null;
        };
        return expectedPrecision == null || (Objects.equals(expectedPrecision, precision) && Objects.equals(0, scale));
    }
}
