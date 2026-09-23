package com.workflow.entity.data.infrastructure.persistence.provider;

import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.entity.data.application.EntityQueryConditions;
import com.workflow.entity.data.application.EntityQueryScalarValues;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 实体数据动态 SQL 提供者
 * 支持动态表名和动态字段。MyBatis 为每次调用传入所属工厂的数据库标识，
 * 方言只处理产品语法；权限片段及其参数仍由业务权限引擎提供。
 */
public class EntityDataSqlProvider {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /**
     * 根据 ID 查询
     */
    public String selectById(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        
        return new SQL() {{
            SELECT("*");
            FROM(tableName);
            WHERE("id = #{id}");
            WHERE("deleted = 0");
        }}.toString();
    }

    /**
     * 在当前事务中锁定并读取目标记录。
     */
    public String selectByIdForUpdate(Map<String, Object> params, ProviderContext context) {
        return selectById(params, context) + " FOR UPDATE";
    }

    /**
     * 根据 ID 查询（带数据权限过滤）。
     */
    public String selectByIdWithPermission(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        String permissionSql = (String) params.get("permissionSql");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName)
                .append(" WHERE id = #{id} AND deleted = 0");
        if (permissionSql != null && !permissionSql.isBlank()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        return sql.toString();
    }

    /** 历史审计读取：包含逻辑删除行，但仍由调用方附加数据权限。 */
    public String selectByIdIncludingDeleted(Map<String, Object> params, ProviderContext context) {
        return "SELECT * FROM " + tableName(params, context)
                + " WHERE id = #{id}";
    }

    /** 历史审计读取：只移除 deleted 条件，不移除行级权限条件。 */
    public String selectByIdIncludingDeletedWithPermission(
            Map<String, Object> params, ProviderContext context) {
        String permissionSql = (String) params.get("permissionSql");
        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(tableName(params, context))
                .append(" WHERE id = #{id}");
        if (permissionSql != null && !permissionSql.isBlank()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        return sql.toString();
    }

    /**
     * 根据流程实例ID查询
     */
    public String selectByProcessInstanceId(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        
        return new SQL() {{
            SELECT("*");
            FROM(tableName);
            WHERE("process_instance_id = #{processInstanceId}");
            WHERE("deleted = 0");
        }}.toString();
    }

    /**
     * 查询列表（支持排序）
     */
    public String selectList(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        
        SQL sql = new SQL() {{
            SELECT("*");
            FROM(tableName);
            WHERE("deleted = 0");
        }};
        
        // 添加排序
        sql.ORDER_BY("create_time DESC");
        
        return sql.toString();
    }

    /**
     * 为唯一性权威写前终检生成全量 exclusive locking read。
     *
     * <p>MySQL 的 {@code FOR UPDATE} 是 current read：即使调用方事务使用
     * REPEATABLE READ，也会在 gate 等待结束后读取最新已提交版本。这里不用
     * FOR SHARE，避免两个预写扫描随后更新各自记录时形成 S 到 X 的升级死锁。</p>
     */
    public String selectListForUpdate(
            Map<String, Object> params, ProviderContext context) {
        return selectList(params, context) + " FOR UPDATE";
    }

    /**
     * 对普通字符列预筛唯一值候选；返回可能冲突的超集，最终比较仍由表单规则策略完成。
     *
     * <p>只对可打印 ASCII 证明 trim/大小写等价性：显式字母对避免数据库 locale 影响；
     * Unicode 和控制字符行全部保留，防止 Java trim/ROOT 小写与数据库函数差异漏报。
     * 模式和值均参数绑定，NULL/零长度只在查空值时纳入。LOB、数值与日期由适配器全量读取。</p>
     */
    public String selectFormUniqueCandidates(
            Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        String column = (String) params.get("columnName");
        String quotedColumn = requireIdentifier(column, "字段名", context);
        var dialect = DatabaseQueryDialects.forDatabaseId(context.getDatabaseId());
        String normalizedValue = (String) params.get("normalizedValue");
        String excludeRecordId = (String) params.get("excludeRecordId");

        params.put("_uniqueNonAsciiPattern", FormUniqueTextPrefilter.NON_PRINTABLE_ASCII);
        var predicates = new ArrayList<String>();
        predicates.add(dialect.regularExpressionPredicate(column,
                "#{_uniqueNonAsciiPattern,jdbcType=VARCHAR}"));
        String asciiPattern = FormUniqueTextPrefilter.asciiPattern(normalizedValue);
        if (asciiPattern != null) {
            params.put("_uniqueAsciiPattern", asciiPattern);
            predicates.add(dialect.regularExpressionPredicate(column,
                    "#{_uniqueAsciiPattern,jdbcType=VARCHAR}"));
        }
        if (normalizedValue == null || normalizedValue.isEmpty()) {
            predicates.add(quotedColumn + " IS NULL");
            predicates.add("LENGTH(" + quotedColumn + ") = 0");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(tableName).append(" WHERE deleted = 0 AND (")
                .append(String.join(" OR ", predicates)).append(")");
        if (excludeRecordId != null && !excludeRecordId.isBlank()) {
            sql.append(" AND id <> #{excludeRecordId,jdbcType=VARCHAR}");
        }
        return sql.append(" ORDER BY create_time DESC, id ASC").toString();
    }

    /**
     * 为唯一性权威写前终检生成按值预筛的 exclusive locking read。
     * 普通预检继续调用无锁版本，避免用户输入阶段占用行锁。
     */
    public String selectFormUniqueCandidatesForUpdate(
            Map<String, Object> params, ProviderContext context) {
        return selectFormUniqueCandidates(params, context) + " FOR UPDATE";
    }

    /**
     * 条件查询（支持 LIKE 模糊查询和 BETWEEN 范围查询）
     */
    public String selectByCondition(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName);
        sql.append(" WHERE deleted = 0");

        appendConditionSql(sql, params, condition, context);

        sql.append(" ORDER BY create_time DESC");
        return sql.toString();
    }

    /**
     * 插入数据（动态字段）
     */
    public String insert(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) params.get("data");
        
        SQL sql = new SQL();
        sql.INSERT_INTO(tableName);
        
        // 添加所有非空字段（驼峰命名转换为下划线命名）
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() != null) {
                String columnName = columnName(entry.getKey(), context);
                sql.VALUES(columnName, "#{data." + entry.getKey() + "}");
            }
        }
        
        return sql.toString();
    }

    /**
     * 更新数据（动态字段）
     */
    public String update(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) params.get("data");
        
        SQL sql = new SQL();
        sql.UPDATE(tableName);
        
        // 添加所有非空字段（排除 id，驼峰命名转换为下划线命名）
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (!"id".equals(key)) {
                String columnName = columnName(key, context);
                if (entry.getValue() == null) {
                    sql.SET(columnName + " = NULL");
                } else {
                    sql.SET(columnName + " = #{data." + key + "}");
                }
            }
        }
        
        sql.WHERE("id = #{data.id}");
        
        return sql.toString();
    }

    /**
     * 更新当前任务信息，允许显式置空任务字段
     */
    public String updateCurrentTask(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);

        return new SQL() {{
            UPDATE(tableName);
            SET("current_task_id = #{currentTaskId,jdbcType=VARCHAR}");
            SET("current_task_name = #{currentTaskName,jdbcType=VARCHAR}");
            SET("current_task_assignee = #{currentTaskAssignee,jdbcType=VARCHAR}");
            SET("update_time = CURRENT_TIMESTAMP");
            WHERE("id = #{id}");
        }}.toString();
    }

    /**
     * 逻辑删除
     */
    public String deleteById(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        
        return new SQL() {{
            UPDATE(tableName);
            SET("deleted = 1");
            SET("update_time = CURRENT_TIMESTAMP");
            WHERE("id = #{id}");
        }}.toString();
    }

    /**
     * 物理删除
     */
    public String physicalDeleteById(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        
        return new SQL() {{
            DELETE_FROM(tableName);
            WHERE("id = #{id}");
        }}.toString();
    }

    /**
     * 查询列表（带数据权限过滤）
     */
    public String selectListWithPermission(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        String permissionSql = (String) params.get("permissionSql");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName);
        sql.append(" WHERE deleted = 0");
        if (permissionSql != null && !permissionSql.isEmpty()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        sql.append(" ORDER BY create_time DESC");
        return sql.toString();
    }

    /**
     * 分页查询（不带条件），按创建时间、主键倒序，确保同一时间的数据分页顺序稳定。
     *
     * @param params 参数 Map，需含 tableName；行范围由 Mapper 的 MP Page 提供
     * @return 过滤及排序 SQL，由 MyBatis-Plus 追加分页
     */
    public String selectPage(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        return "SELECT * FROM " + tableName
                + " WHERE deleted = 0 ORDER BY create_time DESC, id DESC";
    }

    /**
     * 分页查询（带数据权限过滤），按创建时间、主键倒序，确保同一时间的数据分页顺序稳定。
     *
     * @param params 参数 Map，需含 tableName、permissionSql；行范围由 Mapper 的 MP Page 提供
     * @return 过滤及排序 SQL，由 MyBatis-Plus 追加分页
     */
    public String selectPageWithPermission(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        String permissionSql = (String) params.get("permissionSql");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName)
                .append(" WHERE deleted = 0");
        if (permissionSql != null && !permissionSql.isBlank()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        sql.append(" ORDER BY create_time DESC, id DESC");
        return sql.toString();
    }

    /**
     * 条件查询（带数据权限过滤）
     */
    public String selectByConditionWithPermission(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");
        String permissionSql = (String) params.get("permissionSql");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName);
        sql.append(" WHERE deleted = 0");

        // 添加权限条件
        if (permissionSql != null && !permissionSql.isEmpty()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }

        // 添加查询条件
        appendConditionSql(sql, params, condition, context);

        sql.append(" ORDER BY create_time DESC");
        return sql.toString();
    }

    /**
     * 分页条件查询（不带权限过滤），按创建时间、主键倒序，确保同一时间的数据分页顺序稳定。
     *
     * @param params 参数 Map，需含 tableName、condition；行范围由 Mapper 的 MP Page 提供
     * @return 条件及排序 SQL，由 MyBatis-Plus 追加分页
     */
    public String selectPageByCondition(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName)
                .append(" WHERE deleted = 0");
        appendConditionSql(sql, params, condition, context);
        sql.append(" ORDER BY create_time DESC, id DESC");
        return sql.toString();
    }

    /**
     * 分页条件查询（带数据权限过滤），按创建时间、主键倒序，确保同一时间的数据分页顺序稳定。
     *
     * @param params 参数 Map，需含 tableName、condition、permissionSql；行范围由 Mapper 的 MP Page 提供
     * @return 条件及排序 SQL，由 MyBatis-Plus 追加分页
     */
    public String selectPageByConditionWithPermission(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");
        String permissionSql = (String) params.get("permissionSql");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName)
                .append(" WHERE deleted = 0");
        if (permissionSql != null && !permissionSql.isBlank()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        appendConditionSql(sql, params, condition, context);
        sql.append(" ORDER BY create_time DESC, id DESC");
        return sql.toString();
    }

    /**
     * 统计查询
     */
    public String count(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);

        return new SQL() {{
            SELECT("COUNT(*)");
            FROM(tableName);
            WHERE("deleted = 0");
        }}.toString();
    }

    /**
     * 统计查询（根据条件）
     */
    public String countByCondition(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(tableName);
        sql.append(" WHERE deleted = 0");

        appendConditionSql(sql, params, condition, context);

        return sql.toString();
    }

    /**
     * 统计查询（带数据权限过滤）
     */
    public String countWithPermission(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        String permissionSql = (String) params.get("permissionSql");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(tableName);
        sql.append(" WHERE deleted = 0");
        if (permissionSql != null && !permissionSql.isEmpty()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        return sql.toString();
    }

    /**
     * 统计查询（根据条件并带数据权限过滤）。
     *
     * @param params 参数 Map，需含 tableName、condition、permissionSql
     * @return 拼接后的统计 SQL
     */
    public String countByConditionWithPermission(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");
        String permissionSql = (String) params.get("permissionSql");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(tableName)
                .append(" WHERE deleted = 0");
        if (permissionSql != null && !permissionSql.isBlank()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        appendConditionSql(sql, params, condition, context);
        return sql.toString();
    }

    /**
     * 统计已关联流程实例（process_instance_id 非空）的记录数量。
     * 字符列通过 NULLIF 统一空串和 NULL，避免 Oracle 的空串比较丢失已启动记录。
     *
     * @param params 参数 Map，需含 tableName
     * @return 拼接后的统计 SQL
     */
    public String countProcessInstances(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        return "SELECT COUNT(*) FROM " + tableName
                + " WHERE deleted = 0"
                + " AND NULLIF(process_instance_id, '') IS NOT NULL";
    }

    // ============ 私有辅助方法 ============

    /**
     * 追加查询条件到 SQL
     * 支持查询方式：EQ(等于)、NE(不等于)、LIKE(包含)、GT(大于)、LT(小于)、
     * BETWEEN(范围)、IN(包含于)、NOT_IN(不包含于)、IS_NULL(为空)
     * 通过 _op 后缀参数指定查询方式，例如：name=xxx&name_op=EQ
     */
    private void appendConditionSql(
            StringBuilder sql,
            Map<String, Object> params,
            Map<String, Object> condition, ProviderContext context) {
        if (condition == null) {
            return;
        }
        if (condition instanceof EntityQueryConditions) {
            // 与原始条件、LIKE 和权限绑定分别命名，避免字段名或 IN 下标互相覆盖。
            // 每次渲染重建，count/page 共用不可变条件时不会修改调用方的值或累积绑定。
            params.put("__conditionScalars", new java.util.LinkedHashMap<String, Object>());
        }

        // 分离 start/end/op 和普通条件
        Map<String, Object> startMap = new java.util.HashMap<>();
        Map<String, Object> endMap = new java.util.HashMap<>();
        Map<String, String> opMap = new java.util.HashMap<>();
        Map<String, Object> normalConditions = new java.util.HashMap<>();

        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key.startsWith("__multi_")) {
                continue;
            }
            // 跳过 null 和空字符串
            if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                continue;
            }
            if (key.endsWith("_start")) {
                startMap.put(key.substring(0, key.length() - 6), value);
            } else if (key.endsWith("_end")) {
                endMap.put(key.substring(0, key.length() - 4), value);
            } else if (key.endsWith("_op")) {
                String fieldKey = key.substring(0, key.length() - 3);
                columnName(fieldKey, context);
                opMap.put(fieldKey, ((String) value).toUpperCase());
            } else {
                normalConditions.put(key, value);
            }
        }

        // 处理 BETWEEN 条件（同时有 start 和 end）
        java.util.Set<String> betweenKeys = new java.util.HashSet<>(startMap.keySet());
        betweenKeys.retainAll(endMap.keySet());
        for (String fieldKey : betweenKeys) {
            sql.append(" AND ").append(conditionComparison(params, condition, fieldKey, ">=", startMap.get(fieldKey),
                            "condition." + fieldKey + "_start", context))
                    .append(" AND ").append(conditionComparison(params, condition, fieldKey, "<=", endMap.get(fieldKey),
                            "condition." + fieldKey + "_end", context));
        }
        // 只有 start 没有 end
        for (Map.Entry<String, Object> entry : startMap.entrySet()) {
            if (!endMap.containsKey(entry.getKey())) {
                sql.append(" AND ").append(conditionComparison(params, condition, entry.getKey(), ">=", entry.getValue(),
                        "condition." + entry.getKey() + "_start", context));
            }
        }
        // 只有 end 没有 start
        for (Map.Entry<String, Object> entry : endMap.entrySet()) {
            if (!startMap.containsKey(entry.getKey())) {
                sql.append(" AND ").append(conditionComparison(params, condition, entry.getKey(), "<=", entry.getValue(),
                        "condition." + entry.getKey() + "_end", context));
            }
        }

        // 处理普通条件（根据查询方式生成对应 SQL）
        for (Map.Entry<String, Object> entry : normalConditions.entrySet()) {
            String fieldKey = entry.getKey();
            String columnName = conditionColumnName(fieldKey, condition, context);
            Object value = entry.getValue();
            String op = opMap.getOrDefault(fieldKey, "");

            if ("IS_NULL".equals(op)) {
                // IS_NULL 只接受由服务端可信配置生成的操作符。调用方仍需提供
                // 非空占位值，使该字段进入 normalConditions；值本身不会拼入 SQL。
                sql.append(" AND ").append(columnName).append(" IS NULL");
            } else if ("EQ".equals(op)) {
                sql.append(" AND ").append(conditionComparison(params, condition, fieldKey, "=", value, "condition." + fieldKey, context));
            } else if ("NE".equals(op)) {
                sql.append(" AND ").append(conditionComparison(params, condition, fieldKey, "<>", value, "condition." + fieldKey, context));
            } else if ("GT".equals(op)) {
                sql.append(" AND ").append(conditionComparison(params, condition, fieldKey, ">", value, "condition." + fieldKey, context));
            } else if ("LT".equals(op)) {
                sql.append(" AND ").append(conditionComparison(params, condition, fieldKey, "<", value, "condition." + fieldKey, context));
            } else if ("IN".equals(op) || "NOT_IN".equals(op)) {
                appendInCondition(
                        sql,
                        params,
                        condition,
                        columnName,
                        fieldKey,
                        value,
                        "NOT_IN".equals(op), context);
            } else if ("LIKE".equals(op) || (op.isEmpty() && value instanceof String)) {
                // 在 Java 中构造同样的包含模式再绑定，避免 Oracle 不支持三参数 CONCAT；
                // 用户输入中的引号和 SQL 标记仍然只是参数值，不能进入 SQL 文本。
                String parameterKey = "__condition_" + fieldKey + "_like";
                params.put(parameterKey, "%" + value + "%");
                String expression = columnName;
                if (condition instanceof EntityQueryConditions typed) {
                    var column = typed.column(fieldKey);
                    expression = DatabaseQueryDialects.forDatabaseId(context.getDatabaseId())
                            .patternValueExpression(column.name(), column.type());
                }
                sql.append(" AND ").append(expression).append(" LIKE #{").append(parameterKey).append(",jdbcType=VARCHAR}");
            } else {
                sql.append(" AND ").append(conditionComparison(params, condition, fieldKey, "=", value, "condition." + fieldKey, context));
            }
        }
    }

    /** 比较的列名取可信物理映射；产品方言负责 CLOB 全文比较，不能在此截取前缀。 */
    private String conditionComparison(Map<String, Object> params, Map<String, Object> condition,
                                       String field, String operator, Object value, String originalProperty,
                                       ProviderContext context) {
        String columnName = conditionColumnName(field, condition, context);
        String parameter = bindConditionScalar(params, condition, field, value, originalProperty, context);
        if (condition instanceof EntityQueryConditions typed) {
            var column = typed.column(field);
            return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId())
                    .comparisonPredicate(column.name(), column.type().kind(), operator, parameter);
        }
        return columnName + " " + operator + " " + parameter;
    }

    /**
     * 只有确定比较操作符后才转换值，保留未指定操作符的字符串 LIKE 语义。
     * 无可信发布元数据的内部旧调用保留原绑定，不从请求值猜物理列类型。
     */
    @SuppressWarnings("unchecked")
    private String bindConditionScalar(Map<String, Object> params, Map<String, Object> condition,
                                       String field, Object value, String originalProperty, ProviderContext context) {
        if (!(condition instanceof EntityQueryConditions typed)) return "#{" + originalProperty + "}";
        var kind = typed.column(field).type().kind();
        Object converted = EntityQueryScalarValues.scalarValue(value, kind);
        Map<String, Object> scalars = (Map<String, Object>) params.get("__conditionScalars");
        String key = "value" + scalars.size();
        scalars.put(key, converted);
        String jdbcType = DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).comparisonJdbcType(kind);
        // String + jdbcType=CLOB 交给 MyBatis 内置 ClobTypeHandler，完整流式绑定，不限制文本长度。
        return "#{__conditionScalars." + key + ",jdbcType=" + jdbcType + "}";
    }

    /** 普通请求只含条件值；已发布元数据由服务端封装，别名解析不能覆盖参数绑定键。 */
    private String conditionColumnName(String field, Map<String, Object> condition, ProviderContext context) {
        // 即使字段有可信映射，也先校验原键，因为它仍会成为 MyBatis 绑定属性的一部分。
        columnName(field, context);
        return condition instanceof EntityQueryConditions typed
                ? DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).quoteIdentifier(typed.column(field).name())
                : columnName(field, context);
    }

    private void appendInCondition(
            StringBuilder sql,
            Map<String, Object> params,
            Map<String, Object> condition,
            String columnName,
            String fieldKey,
            Object rawValue,
            boolean negated, ProviderContext context) {
        List<Object> values = normalizeInValues(rawValue);
        if (values.isEmpty()) {
            sql.append(negated ? " AND 1 = 1" : " AND 1 = 0");
            return;
        }

        List<String> placeholders = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String parameterKey =
                    "__condition_" + fieldKey + "_" + index;
            if (!(condition instanceof EntityQueryConditions)) params.put(parameterKey, values.get(index));
            placeholders.add(bindConditionScalar(params, condition, fieldKey, values.get(index), parameterKey, context));
        }
        if (condition instanceof EntityQueryConditions typed) {
            var column = typed.column(fieldKey);
            var dialect = DatabaseQueryDialects.forDatabaseId(context.getDatabaseId());
            if ("CLOB".equals(dialect.comparisonJdbcType(column.type().kind()))) {
                // CLOB 不能直接 IN；等价展开保留 NULL 列的 UNKNOWN，不用 COALESCE 改成真/假。
                // 列表元素仍先沿用本查询原有的空值过滤规则，区别于权限规则保留 NULL 的策略。
                List<String> predicates = placeholders.stream()
                        .map(parameter -> dialect.comparisonPredicate(column.name(), column.type().kind(), negated ? "<>" : "=", parameter))
                        .toList();
                sql.append(" AND (").append(String.join(negated ? " AND " : " OR ", predicates)).append(")");
                return;
            }
        }
        sql.append(" AND ")
                .append(columnName)
                .append(negated ? " NOT IN (" : " IN (")
                .append(String.join(", ", placeholders))
                .append(")");
    }

    private List<Object> normalizeInValues(Object value) {
        List<Object> values = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.stream()
                    .filter(item -> item != null
                            && !String.valueOf(item).trim().isEmpty())
                    .forEach(values::add);
            return values;
        }
        if (value != null && value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                Object item = Array.get(value, index);
                if (item != null
                        && !String.valueOf(item).trim().isEmpty()) {
                    values.add(item);
                }
            }
            return values;
        }
        if (value instanceof String text) {
            for (String item : text.split(",")) {
                String normalized = item.trim();
                if (!normalized.isEmpty()) {
                    values.add(normalized);
                }
            }
            return values;
        }
        if (value != null) {
            values.add(value);
        }
        return values;
    }

    /** 从参数中取出并校验表名 */
    private String tableName(Map<String, Object> params, ProviderContext context) {
        return requireIdentifier((String) params.get("tableName"), "表名", context);
    }

    /** 将驼峰字段 key 转为下划线列名并校验合法性 */
    private String columnName(String fieldKey, ProviderContext context) {
        return requireIdentifier(camelToUnderscore(fieldKey), "字段名", context);
    }

    /** 校验标识符是否符合 SQL 标识符规范，不合法抛出 IllegalArgumentException */
    private String requireIdentifier(String value, String label, ProviderContext context) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + "不合法");
        }
        return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).quoteIdentifier(value);
    }

    /**
     * 驼峰命名转换为下划线命名
     * 例如：processInstanceId -> process_instance_id
     */
    private String camelToUnderscore(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append("_").append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
