package com.workflow.entity.data.infrastructure.persistence.provider;

import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
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

    /** 固定摘要投影避开 SELECT *；唯一可变列必须来自已发布元数据且通过标识符校验。 */
    public String selectTaskSummary(Map<String, Object> params, ProviderContext context) {
        String customColumn = (String) params.get("dataNameColumn");
        if (customColumn != null && !SQL_IDENTIFIER.matcher(customColumn).matches()) {
            throw new IllegalArgumentException("非法任务摘要字段");
        }
        String extra = customColumn == null ? "NULL" :
                DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).quoteIdentifier(customColumn);
        return "SELECT name,code,status,current_task_name," + extra + " AS data_name FROM "
                + tableName(params, context) + " WHERE id=#{id} AND deleted=0";
    }

    /**
     * 根据 ID 查询
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续ID步骤传递身份、配置或状态
     * @return 查询后的ID文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code selectById} 的输入影响后续处理
     * @param context 执行上下文，向后续ID更新步骤传递身份、配置或状态
     * @return 查询后的ID更新文本，供调用方比较或展示
     */
    public String selectByIdForUpdate(Map<String, Object> params, ProviderContext context) {
        return selectById(params, context) + " FOR UPDATE";
    }

    /**
     * 根据 ID 查询（带数据权限过滤）。
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续ID权限步骤传递身份、配置或状态
     * @return 查询后的ID权限文本，供调用方比较或展示
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

    /**
     * 历史审计读取：包含逻辑删除行，但仍由调用方附加数据权限。
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续ID{@code including}已删除步骤传递身份、配置或状态
     * @return 查询后的ID{@code including}已删除文本，供调用方比较或展示
     */
    public String selectByIdIncludingDeleted(Map<String, Object> params, ProviderContext context) {
        return "SELECT * FROM " + tableName(params, context)
                + " WHERE id = #{id}";
    }

    /**
     * 历史审计读取：只移除 deleted 条件，不移除行级权限条件。
     *
     * @param params 参数，供本方法查询ID{@code including}已删除权限时使用
     * @param context 执行上下文，向后续ID{@code including}已删除权限步骤传递身份、配置或状态
     * @return 查询后的ID{@code including}已删除权限文本，供调用方比较或展示
     */
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续流程实例ID步骤传递身份、配置或状态
     * @return 查询后的流程实例ID文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续实体数据SQL提供者列表步骤传递身份、配置或状态
     * @return 查询后的实体数据SQL提供者列表文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code selectList} 的输入影响后续处理
     * @param context 执行上下文，向后续列表更新步骤传递身份、配置或状态
     * @return 查询后的列表更新文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续表单唯一候选集合步骤传递身份、配置或状态
     * @return 查询后的表单唯一候选集合文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code selectFormUniqueCandidates} 的输入影响后续处理
     * @param context 执行上下文，向后续表单唯一候选集合更新步骤传递身份、配置或状态
     * @return 查询后的表单唯一候选集合更新文本，供调用方比较或展示
     */
    public String selectFormUniqueCandidatesForUpdate(
            Map<String, Object> params, ProviderContext context) {
        return selectFormUniqueCandidates(params, context) + " FOR UPDATE";
    }

    /**
     * 条件查询（支持 LIKE 模糊查询和 BETWEEN 范围查询）
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续条件步骤传递身份、配置或状态
     * @return 查询后的条件文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续实体数据SQL提供者步骤传递身份、配置或状态
     * @return 插入后的实体数据SQL提供者文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续实体数据SQL提供者步骤传递身份、配置或状态
     * @return 更新后的实体数据SQL提供者文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续当前任务步骤传递身份、配置或状态
     * @return 更新后的当前任务文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续ID步骤传递身份、配置或状态
     * @return 删除后的ID文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续物理删除ID步骤传递身份、配置或状态
     * @return 处理后的物理删除ID文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续列表权限步骤传递身份、配置或状态
     * @return 查询后的列表权限文本，供调用方比较或展示
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
     * 分页查询（不带条件）；未指定可信排序列时按创建时间、主键倒序。
     *
     * @param params 参数 Map，需含 tableName；行范围由 Mapper 的 MP Page 提供
     * @param context 执行上下文，向后续实体数据SQL提供者分页步骤传递身份、配置或状态
     * @return 过滤及排序 SQL，由 MyBatis-Plus 追加分页
     */
    public String selectPage(Map<String, Object> params, ProviderContext context) {
        String tableName = tableName(params, context);
        return "SELECT * FROM " + tableName
                + " WHERE deleted = 0" + pageOrderBy(params, context);
    }

    /**
     * 分页查询（带数据权限过滤）；可信排序列在数据库分页前生效。
     *
     * @param params 参数 Map，需含 tableName、permissionSql；行范围由 Mapper 的 MP Page 提供
     * @param context 执行上下文，向后续分页权限步骤传递身份、配置或状态
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
        sql.append(pageOrderBy(params, context));
        return sql.toString();
    }

    /**
     * 条件查询（带数据权限过滤）
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续条件权限步骤传递身份、配置或状态
     * @return 查询后的条件权限文本，供调用方比较或展示
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
     * 分页条件查询（不带权限过滤）；可信排序列在数据库分页前生效。
     *
     * @param params 参数 Map，需含 tableName、condition；行范围由 Mapper 的 MP Page 提供
     * @param context 执行上下文，向后续分页条件步骤传递身份、配置或状态
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
        sql.append(pageOrderBy(params, context));
        return sql.toString();
    }

    /**
     * 分页条件查询（带数据权限过滤）；可信排序列在数据库分页前生效。
     *
     * @param params 参数 Map，需含 tableName、condition、permissionSql；行范围由 Mapper 的 MP Page 提供
     * @param context 执行上下文，向后续分页条件权限步骤传递身份、配置或状态
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
        sql.append(pageOrderBy(params, context));
        return sql.toString();
    }

    /**
     * 导出以排序列和主键定位下一批，选中 ID 作为独立 AND 条件保留原有用户/权限限制。
     * 所有值沿用发布字段的类型绑定；仅经过标识符校验的物理列进入 SQL 结构。
     */
    public String selectExportBatch(Map<String, Object> params, ProviderContext context) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName(params, context))
                .append(" WHERE deleted = 0");
        String permissionSql = (String) params.get("permissionSql");
        if (permissionSql != null && !permissionSql.isBlank()) sql.append(" AND (").append(permissionSql).append(")");
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");
        appendConditionSql(sql, params, condition, context);
        if (params.get("selectedIds") instanceof List<?> ids) {
            if (ids.isEmpty()) {
                sql.append(" AND 1 = 0");
            } else {
                if (ids.size() > 5_000) throw new IllegalArgumentException("导出选中 ID 不能超过 5000 个");
                // Oracle 等数据库限制单个 IN 的表达式数量；分组仍在同一查询中排序、按 200 行取批次。
                List<String> groups = new ArrayList<>();
                for (int start = 0; start < ids.size(); start += 500) {
                    List<String> bindings = new ArrayList<>();
                    for (int i = start; i < Math.min(start + 500, ids.size()); i++) {
                        bindings.add(bindConditionScalar(params, condition, "id", ids.get(i), "__exportId" + i, context));
                    }
                    groups.add("id IN (" + String.join(", ", bindings) + ")");
                }
                sql.append(" AND (").append(String.join(" OR ", groups)).append(")");
            }
        }
        // 先解析排序，校验方向后才能构造比较运算符；空值顺序遵循各数据库默认排序。
        String order = pageOrderBy(params, context);
        boolean configured = params.get("sortColumn") instanceof String value && !value.isBlank();
        String sortColumn = configured ? (String) params.get("sortColumn") : "create_time";
        boolean ascending = configured && (params.get("sortDirection") == null
                || "ASC".equalsIgnoreCase(params.get("sortDirection").toString().trim()));
        String quoted = requireIdentifier(sortColumn, "导出排序字段", context);
        if (params.get("cursor") instanceof com.workflow.entity.data.application.EntityExportBatch.Cursor cursor) {
            String id = bindConditionScalar(params, condition, "id", cursor.id(), "__exportCursorId", context);
            boolean nullHigh = !java.util.Set.of("MYSQL", "OCEANBASE_MYSQL").contains(context.getDatabaseId());
            boolean nullFirst = ascending != nullHigh;
            if (cursor.sortValue() == null) {
                sql.append(" AND ((").append(quoted).append(" IS NULL AND id < ").append(id).append(")");
                if (nullFirst) sql.append(" OR ").append(quoted).append(" IS NOT NULL");
            } else {
                String value = bindConditionScalar(params, condition, sortColumn, cursor.sortValue(), "__exportCursorSort", context);
                sql.append(" AND ((").append(quoted).append(ascending ? " > " : " < ").append(value)
                        .append(") OR (").append(quoted).append(" = ").append(value)
                        .append(" AND id < ").append(id).append(")");
                if (!nullFirst) sql.append(" OR ").append(quoted).append(" IS NULL");
            }
            sql.append(")");
        }
        return sql.append(order).toString();
    }

    /**
     * 分页排序只能使用应用层已核实的发布物理列；再次校验标识符和方向，
     * 防止配置 JSON 被直接拼成 SQL。数据库按原始列类型排序，DECIMAL 不会按文本字典序。
     */
    private String pageOrderBy(Map<String, Object> params, ProviderContext context) {
        Object rawColumn = params.get("sortColumn");
        if (!(rawColumn instanceof String column) || column.isBlank()) {
            return " ORDER BY create_time DESC, id DESC";
        }
        String quotedColumn = requireIdentifier(column, "排序字段", context);
        Object rawDirection = params.get("sortDirection");
        String direction = rawDirection == null ? "ASC" : rawDirection.toString().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
            throw new IllegalArgumentException("默认排序方向仅支持 ASC 或 DESC");
        }
        return " ORDER BY " + quotedColumn + " " + direction + ", id DESC";
    }

    /**
     * 统计查询
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续实体数据SQL提供者步骤传递身份、配置或状态
     * @return 统计后的实体数据SQL提供者文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续条件步骤传递身份、配置或状态
     * @return 统计后的条件文本，供调用方比较或展示
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
     *
     * @param params 参数，作为 {@code tableName} 的输入影响后续处理
     * @param context 执行上下文，向后续权限步骤传递身份、配置或状态
     * @return 统计后的权限文本，供调用方比较或展示
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
     * @param context 执行上下文，向后续条件权限步骤传递身份、配置或状态
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
     * @param context 执行上下文，向后续流程{@code instances}步骤传递身份、配置或状态
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
     *
     * @param sql SQL，作为 {@code appendInCondition} 的输入影响后续处理
     * @param params 参数，作为 {@code appendInCondition} 的输入影响后续处理
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param context 执行上下文，向后续条件SQL步骤传递身份、配置或状态
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

    /**
     * 比较的列名取可信物理映射；产品方言负责 CLOB 全文比较，不能在此截取前缀。
     *
     * @param params 参数，作为 {@code bindConditionScalar} 的输入影响后续处理
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param field 字段，作为 {@code conditionColumnName} 的输入影响后续处理
     * @param operator 操作人，供本方法处理条件比较时使用
     * @param value 待处理条件比较的原始输入，结果供调用方继续使用
     * @param originalProperty 原始属性，作为 {@code bindConditionScalar} 的输入影响后续处理
     * @param context 执行上下文，向后续条件比较步骤传递身份、配置或状态
     * @return 处理后的条件比较文本，供调用方比较或展示
     */
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
     *
     * @param params 参数，供本方法处理绑定条件标量时使用
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param field 字段，作为 {@code typed.column} 的输入影响后续处理
     * @param value 待处理绑定条件标量的原始输入，结果供调用方继续使用
     * @param originalProperty 原始属性，供本方法处理绑定条件标量时使用
     * @param context 执行上下文，向后续绑定条件标量步骤传递身份、配置或状态
     * @return 处理后的绑定条件标量文本，供调用方比较或展示
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

    /**
     * 普通请求只含条件值；已发布元数据由服务端封装，别名解析不能覆盖参数绑定键。
     *
     * @param field 字段，作为 {@code columnName} 的输入影响后续处理
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param context 执行上下文，向后续条件列名称步骤传递身份、配置或状态
     * @return 处理后的条件列名称文本，供调用方比较或展示
     */
    private String conditionColumnName(String field, Map<String, Object> condition, ProviderContext context) {
        // 即使字段有可信映射，也先校验原键，因为它仍会成为 MyBatis 绑定属性的一部分。
        columnName(field, context);
        return condition instanceof EntityQueryConditions typed
                ? DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).quoteIdentifier(typed.column(field).name())
                : columnName(field, context);
    }

    /**
     * 追加条件；结果供后续流程传递或持久化。
     *
     * @param sql SQL，供本方法追加条件时使用
     * @param params 参数，作为 {@code placeholders.add} 的输入影响后续处理
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param columnName 列名称，后续用于追加条件时匹配或展示
     * @param fieldKey 字段键，后续用于授权校验、关联或幂等去重
     * @param rawValue 原始值，作为 {@code normalizeInValues} 的输入影响后续处理
     * @param negated {@code negated}，作为 {@code sql.append} 的输入影响后续处理
     * @param context 执行上下文，向后续条件步骤传递身份、配置或状态
     */
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

    /**
     * 规范化值集合；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化值集合的原始输入，结果供调用方继续使用
     * @return 实体数据SQL提供者集合，供调用方遍历或展示
     */
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

    /**
     * 从参数中取出并校验表名
     *
     * @param params 参数，供本方法处理表名称时使用
     * @param context 执行上下文，向后续表名称步骤传递身份、配置或状态
     * @return 处理后的表名称文本，供调用方比较或展示
     */
    private String tableName(Map<String, Object> params, ProviderContext context) {
        return requireIdentifier((String) params.get("tableName"), "表名", context);
    }

    /**
     * 将驼峰字段 key 转为下划线列名并校验合法性
     *
     * @param fieldKey 字段键，后续用于授权校验、关联或幂等去重
     * @param context 执行上下文，向后续列名称步骤传递身份、配置或状态
     * @return 处理后的列名称文本，供调用方比较或展示
     */
    private String columnName(String fieldKey, ProviderContext context) {
        return requireIdentifier(camelToUnderscore(fieldKey), "字段名", context);
    }

    /**
     * 校验标识符是否符合 SQL 标识符规范，不合法抛出 IllegalArgumentException
     *
     * @param value 待校验并获取标识符的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于校验并获取标识符时匹配或展示
     * @param context 执行上下文，向后续标识符步骤传递身份、配置或状态
     * @return 校验并获取后的标识符文本，供调用方比较或展示
     */
    private String requireIdentifier(String value, String label, ProviderContext context) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + "不合法");
        }
        return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).quoteIdentifier(value);
    }

    /**
     * 驼峰命名转换为下划线命名
     * 例如：processInstanceId -> process_instance_id
     *
     * @param camelCase {@code camel}分支，供本方法处理{@code camel}截止{@code underscore}时使用
     * @return 处理后的{@code camel}截止{@code underscore}文本，供调用方比较或展示
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
