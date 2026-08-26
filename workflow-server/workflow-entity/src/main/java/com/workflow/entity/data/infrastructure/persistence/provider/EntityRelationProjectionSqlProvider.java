package com.workflow.entity.data.infrastructure.persistence.provider;

import org.springframework.util.StringUtils;

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

    /** 查询一页 id + 标量链接列。 */
    public String selectPage(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("SELECT id AS record_id");
        for (ColumnProjection projection : columns(parameters)) {
            sql.append(", ")
                    .append(identifier(projection.column()))
                    .append(" AS ")
                    .append(identifier(projection.alias()));
        }
        sql.append(" FROM ").append(table(parameters));
        appendWhere(sql, parameters);
        return sql.append(" ORDER BY id")
                .append(" LIMIT #{offset}, #{pageSize}")
                .toString();
    }

    /** 统计应用相同关系条件和数据范围后的记录数。 */
    public String count(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ")
                .append(table(parameters));
        appendWhere(sql, parameters);
        return sql.toString();
    }

    /**
     * 批量读取多值表中的链接值。limitPlusOne 用于在进入内存前发现超限。
     */
    public String selectMultiValues(Map<String, Object> parameters) {
        List<?> recordIds = values(parameters, "recordIds");
        List<?> fieldCodes = values(parameters, "multiFieldCodes");
        StringBuilder sql = new StringBuilder()
                .append("SELECT record_id, field_code, target_entity_id, ")
                .append("target_record_id FROM ")
                .append(identifier(text(parameters, "multiTable")))
                .append(" WHERE deleted = 0 AND record_id IN (")
                .append(placeholders("recordIds", recordIds.size()))
                .append(") AND field_code IN (")
                .append(placeholders("multiFieldCodes", fieldCodes.size()))
                .append(") ORDER BY record_id, field_code, sort_order")
                .append(" LIMIT #{limitPlusOne}");
        return sql.toString();
    }

    private void appendWhere(
            StringBuilder sql,
            Map<String, Object> parameters) {
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
                    .append(identifier(text(parameters, "predicateColumn")))
                    .append(" IN (")
                    .append(placeholders(
                            "predicateValues", predicateValues.size()))
                    .append(")");
            return;
        }
        if ("MULTI_LINK_IN".equals(predicateType)) {
            String businessTable = table(parameters);
            sql.append(" AND EXISTS (SELECT 1 FROM ")
                    .append(identifier(text(parameters, "multiTable")))
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

    private String table(Map<String, Object> parameters) {
        return identifier(text(parameters, "tableName"));
    }

    @SuppressWarnings("unchecked")
    private List<ColumnProjection> columns(Map<String, Object> parameters) {
        Object raw = parameters.get("columns");
        return raw instanceof List<?> values
                ? (List<ColumnProjection>) values : List.of();
    }

    private List<?> values(
            Map<String, Object> parameters,
            String key) {
        Object raw = parameters.get(key);
        if (!(raw instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("关系图查询参数缺少 " + key);
        }
        return List.copyOf(collection);
    }

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

    private String text(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String identifier(String value) {
        if (!StringUtils.hasText(value)
                || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("非法关系图数据库标识符");
        }
        return value;
    }

    /** 查询列与固定返回别名。 */
    public record ColumnProjection(String column, String alias) {
    }
}
