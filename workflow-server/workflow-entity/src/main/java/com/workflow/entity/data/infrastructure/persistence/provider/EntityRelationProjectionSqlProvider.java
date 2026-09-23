package com.workflow.entity.data.infrastructure.persistence.provider;

import org.springframework.util.StringUtils;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 关系图最小投影 SQL 提供者。
 *
 * <p>表名、列名和别名全部先做标识符白名单校验；业务值始终使用 MyBatis
 * 参数绑定。数据范围 SQL 只接受平台权限引擎签发的片段。</p>
 */
public class EntityRelationProjectionSqlProvider {

    private static final Pattern IDENTIFIER = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * 查询 id + 标量链接列及稳定顺序，行范围交由 MP 分页插件。
     *
     * @param parameters 参数集合，作为 {@code appendWhere} 的输入影响后续处理
     * @param context 执行上下文，向后续实体关系投影SQL提供者分页步骤传递身份、配置或状态
     * @return 查询后的实体关系投影SQL提供者分页文本，供调用方比较或展示
     */
    public String selectPage(Map<String, Object> parameters, ProviderContext context) {
        StringBuilder sql = new StringBuilder("SELECT id AS ")
                .append(DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).quoteAlias("record_id"));
        for (ColumnProjection projection : columns(parameters)) {
            sql.append(", ")
                    .append(identifier(projection.column(), context))
                    .append(" AS ")
                    .append(DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).quoteAlias(projection.alias()));
        }
        sql.append(" FROM ").append(table(parameters, context));
        appendWhere(sql, parameters, context);
        sql.append(" ORDER BY id");
        return sql.toString();
    }

    /**
     * 统计应用相同关系条件和数据范围后的记录数。
     *
     * @param parameters 参数集合，作为 {@code appendWhere} 的输入影响后续处理
     * @param context 执行上下文，向后续实体关系投影SQL提供者步骤传递身份、配置或状态
     * @return 统计后的实体关系投影SQL提供者文本，供调用方比较或展示
     */
    public String count(Map<String, Object> parameters, ProviderContext context) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ")
                .append(table(parameters, context));
        appendWhere(sql, parameters, context);
        return sql.toString();
    }

    /**
     * 批量读取多值表中的链接值。Mapper 的 MP Page 以 limitPlusOne 在进入内存前发现超限。
     *
     * @param parameters 参数集合，作为 {@code values} 的输入影响后续处理
     * @param context 执行上下文，向后续多实例值集合步骤传递身份、配置或状态
     * @return 查询后的多实例值集合文本，供调用方比较或展示
     */
    public String selectMultiValues(Map<String, Object> parameters, ProviderContext context) {
        List<?> recordIds = values(parameters, "recordIds");
        List<?> fieldCodes = values(parameters, "multiFieldCodes");
        var dialect = DatabaseQueryDialects.forDatabaseId(context.getDatabaseId());
        // Oracle 系列物理列为大写；显式保留返回别名，避免关系投影丢失 Map 中的链接键。
        String projection = List.of("record_id", "field_code", "target_entity_id", "target_record_id").stream()
                .map(column -> dialect.quoteIdentifier(column) + " AS " + dialect.quoteAlias(column))
                .collect(java.util.stream.Collectors.joining(", "));
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(projection).append(" FROM ")
                .append(identifier(text(parameters, "multiTable"), context))
                .append(" WHERE deleted = 0 AND record_id IN (")
                .append(placeholders("recordIds", recordIds.size()))
                .append(") AND field_code IN (")
                .append(placeholders("multiFieldCodes", fieldCodes.size()))
                .append(") ORDER BY record_id, field_code, sort_order, id");
        return sql.toString();
    }

    /**
     * 追加{@code where}；结果供后续流程传递或持久化。
     *
     * @param sql SQL，供本方法追加{@code where}时使用
     * @param parameters 参数集合，作为 {@code text} 的输入影响后续处理
     * @param context 执行上下文，向后续{@code where}步骤传递身份、配置或状态
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void appendWhere(
            StringBuilder sql,
            Map<String, Object> parameters, ProviderContext context) {
        sql.append(" WHERE deleted = 0");
        String permissionSql = text(parameters, "permissionSql");
        if (!StringUtils.hasText(permissionSql)) {
            throw new IllegalArgumentException("关系图数据权限条件不能为空");
        }
        sql.append(" AND (").append(permissionSql).append(")");

        String predicateType = text(parameters, "predicateType");
        List<?> predicateValues = values(parameters, "predicateValues");
        if (predicateValues.isEmpty()) {
            sql.append(" AND 1=0");
            return;
        }
        if ("ID_IN".equals(predicateType)) {
            sql.append(" AND id IN (")
                    .append(placeholders(
                            "predicateValues", predicateValues.size()))
                    .append(")");
            return;
        }
        if ("SCALAR_LINK_IN".equals(predicateType)) {
            sql.append(" AND ")
                    .append(identifier(text(parameters, "predicateColumn"), context))
                    .append(" IN (")
                    .append(placeholders(
                            "predicateValues", predicateValues.size()))
                    .append(")");
            return;
        }
        if ("MULTI_LINK_IN".equals(predicateType)) {
            String businessTable = table(parameters, context);
            sql.append(" AND EXISTS (SELECT 1 FROM ")
                    .append(identifier(text(parameters, "multiTable"), context))
                    .append(" relation_mv WHERE relation_mv.record_id = ")
                    .append(businessTable).append(".id")
                    .append(" AND relation_mv.deleted = 0")
                    .append(" AND relation_mv.field_code = #{predicateFieldCode}")
                    .append(" AND relation_mv.target_entity_id = #{predicateTargetEntityId}")
                    .append(" AND relation_mv.target_record_id IN (")
                    .append(placeholders(
                            "predicateValues", predicateValues.size()))
                    .append("))");
            return;
        }
        throw new IllegalArgumentException("关系图查询谓词无效");
    }

    /**
     * 生成表文本，供后续匹配或展示。
     *
     * @param parameters 参数集合，作为 {@code identifier} 的输入影响后续处理
     * @param context 执行上下文，向后续表步骤传递身份、配置或状态
     * @return 处理后的表文本，供调用方比较或展示
     */
    private String table(Map<String, Object> parameters, ProviderContext context) {
        return identifier(text(parameters, "tableName"), context);
    }

    /**
     * 整理列集合数据，供调用方遍历或继续处理。
     *
     * @param parameters 参数集合，供本方法处理列集合时使用
     * @return 列投影集合，供调用方遍历或展示
     */
    @SuppressWarnings("unchecked")
    private List<ColumnProjection> columns(Map<String, Object> parameters) {
        Object raw = parameters.get("columns");
        return raw instanceof List<?> values
                ? (List<ColumnProjection>) values : List.of();
    }

    /**
     * 整理值集合数据，供调用方遍历或继续处理。
     *
     * @param parameters 参数集合，供本方法处理值集合时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return {@code list<?>}集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private List<?> values(
            Map<String, Object> parameters,
            String key) {
        Object raw = parameters.get(key);
        if (!(raw instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("关系图查询参数缺少 " + key);
        }
        return List.copyOf(collection);
    }

    /**
     * 生成{@code placeholders}文本，供后续匹配或展示。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param size 大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code placeholders}文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String placeholders(String key, int size) {
        if (size < 1 || size > 10000) {
            throw new IllegalArgumentException("关系图查询值数量无效");
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append("#{").append(key).append('[')
                    .append(index).append("]}");
        }
        return result.toString();
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param parameters 参数集合，供本方法处理文本时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 生成标识符文本，供后续匹配或展示。
     *
     * @param value 待处理标识符的原始输入，结果供调用方继续使用
     * @param context 执行上下文，向后续标识符步骤传递身份、配置或状态
     * @return 处理后的标识符文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String identifier(String value, ProviderContext context) {
        if (!StringUtils.hasText(value)
                || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("非法关系图数据库标识符");
        }
        return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).quoteIdentifier(value);
    }

    /**
     * 查询列与固定返回别名。
     *
     * @param column 列，保存在对象中供后续校验、查询或展示
     * @param alias {@code alias}，保存在对象中供后续校验、查询或展示
     */
    public record ColumnProjection(String column, String alias) {
    }
}
