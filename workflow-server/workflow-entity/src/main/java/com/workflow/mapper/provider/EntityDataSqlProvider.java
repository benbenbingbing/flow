package com.workflow.mapper.provider;

import org.apache.ibatis.jdbc.SQL;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 实体数据动态 SQL 提供者
 * 支持动态表名和动态字段
 */
public class EntityDataSqlProvider {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /**
     * 根据 ID 查询
     */
    public String selectById(Map<String, Object> params) {
        String tableName = tableName(params);
        
        return new SQL() {{
            SELECT("*");
            FROM(tableName);
            WHERE("id = #{id}");
            WHERE("deleted = 0");
        }}.toString();
    }

    /**
     * 根据 ID 查询（带数据权限过滤）。
     */
    public String selectByIdWithPermission(Map<String, Object> params) {
        String tableName = tableName(params);
        String permissionSql = (String) params.get("permissionSql");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName)
                .append(" WHERE id = #{id} AND deleted = 0");
        if (permissionSql != null && !permissionSql.isBlank()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        sql.append(" LIMIT 1");
        return sql.toString();
    }

    /**
     * 根据流程实例ID查询
     */
    public String selectByProcessInstanceId(Map<String, Object> params) {
        String tableName = tableName(params);
        
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
    public String selectList(Map<String, Object> params) {
        String tableName = tableName(params);
        
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
     * 条件查询（支持 LIKE 模糊查询和 BETWEEN 范围查询）
     */
    public String selectByCondition(Map<String, Object> params) {
        String tableName = tableName(params);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName);
        sql.append(" WHERE deleted = 0");

        appendConditionSql(sql, condition);

        sql.append(" ORDER BY create_time DESC");
        return sql.toString();
    }

    /**
     * 插入数据（动态字段）
     */
    public String insert(Map<String, Object> params) {
        String tableName = tableName(params);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) params.get("data");
        
        SQL sql = new SQL();
        sql.INSERT_INTO(tableName);
        
        // 添加所有非空字段（驼峰命名转换为下划线命名）
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() != null) {
                String columnName = columnName(entry.getKey());
                sql.VALUES(columnName, "#{data." + entry.getKey() + "}");
            }
        }
        
        return sql.toString();
    }

    /**
     * 更新数据（动态字段）
     */
    public String update(Map<String, Object> params) {
        String tableName = tableName(params);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) params.get("data");
        
        SQL sql = new SQL();
        sql.UPDATE(tableName);
        
        // 添加所有非空字段（排除 id，驼峰命名转换为下划线命名）
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (!"id".equals(key) && entry.getValue() != null) {
                String columnName = columnName(key);
                sql.SET(columnName + " = #{data." + key + "}");
            }
        }
        
        sql.WHERE("id = #{data.id}");
        
        return sql.toString();
    }

    /**
     * 更新当前任务信息，允许显式置空任务字段
     */
    public String updateCurrentTask(Map<String, Object> params) {
        String tableName = tableName(params);

        return new SQL() {{
            UPDATE(tableName);
            SET("current_task_id = #{currentTaskId}");
            SET("current_task_name = #{currentTaskName}");
            SET("current_task_assignee = #{currentTaskAssignee}");
            SET("update_time = NOW()");
            WHERE("id = #{id}");
        }}.toString();
    }

    /**
     * 逻辑删除
     */
    public String deleteById(Map<String, Object> params) {
        String tableName = tableName(params);
        
        return new SQL() {{
            UPDATE(tableName);
            SET("deleted = 1");
            SET("update_time = NOW()");
            WHERE("id = #{id}");
        }}.toString();
    }

    /**
     * 物理删除
     */
    public String physicalDeleteById(Map<String, Object> params) {
        String tableName = tableName(params);
        
        return new SQL() {{
            DELETE_FROM(tableName);
            WHERE("id = #{id}");
        }}.toString();
    }

    /**
     * 查询列表（带数据权限过滤）
     */
    public String selectListWithPermission(Map<String, Object> params) {
        String tableName = tableName(params);
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

    public String selectPage(Map<String, Object> params) {
        String tableName = tableName(params);
        return "SELECT * FROM " + tableName
                + " WHERE deleted = 0"
                + " ORDER BY create_time DESC"
                + " LIMIT #{offset}, #{limit}";
    }

    public String selectPageWithPermission(Map<String, Object> params) {
        String tableName = tableName(params);
        String permissionSql = (String) params.get("permissionSql");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName)
                .append(" WHERE deleted = 0");
        if (permissionSql != null && !permissionSql.isBlank()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        sql.append(" ORDER BY create_time DESC")
                .append(" LIMIT #{offset}, #{limit}");
        return sql.toString();
    }

    /**
     * 条件查询（带数据权限过滤）
     */
    public String selectByConditionWithPermission(Map<String, Object> params) {
        String tableName = tableName(params);
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
        appendConditionSql(sql, condition);

        sql.append(" ORDER BY create_time DESC");
        return sql.toString();
    }

    public String selectPageByCondition(Map<String, Object> params) {
        String tableName = tableName(params);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName)
                .append(" WHERE deleted = 0");
        appendConditionSql(sql, condition);
        sql.append(" ORDER BY create_time DESC")
                .append(" LIMIT #{offset}, #{limit}");
        return sql.toString();
    }

    public String selectPageByConditionWithPermission(Map<String, Object> params) {
        String tableName = tableName(params);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");
        String permissionSql = (String) params.get("permissionSql");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName)
                .append(" WHERE deleted = 0");
        if (permissionSql != null && !permissionSql.isBlank()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        appendConditionSql(sql, condition);
        sql.append(" ORDER BY create_time DESC")
                .append(" LIMIT #{offset}, #{limit}");
        return sql.toString();
    }

    /**
     * 统计查询
     */
    public String count(Map<String, Object> params) {
        String tableName = tableName(params);

        return new SQL() {{
            SELECT("COUNT(*)");
            FROM(tableName);
            WHERE("deleted = 0");
        }}.toString();
    }

    /**
     * 统计查询（根据条件）
     */
    public String countByCondition(Map<String, Object> params) {
        String tableName = tableName(params);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(tableName);
        sql.append(" WHERE deleted = 0");

        appendConditionSql(sql, condition);

        return sql.toString();
    }

    /**
     * 统计查询（带数据权限过滤）
     */
    public String countWithPermission(Map<String, Object> params) {
        String tableName = tableName(params);
        String permissionSql = (String) params.get("permissionSql");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(tableName);
        sql.append(" WHERE deleted = 0");
        if (permissionSql != null && !permissionSql.isEmpty()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        return sql.toString();
    }

    public String countByConditionWithPermission(Map<String, Object> params) {
        String tableName = tableName(params);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");
        String permissionSql = (String) params.get("permissionSql");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(tableName)
                .append(" WHERE deleted = 0");
        if (permissionSql != null && !permissionSql.isBlank()) {
            sql.append(" AND (").append(permissionSql).append(")");
        }
        appendConditionSql(sql, condition);
        return sql.toString();
    }

    public String countProcessInstances(Map<String, Object> params) {
        String tableName = tableName(params);
        return "SELECT COUNT(*) FROM " + tableName
                + " WHERE deleted = 0"
                + " AND process_instance_id IS NOT NULL"
                + " AND process_instance_id <> ''";
    }

    // ============ 私有辅助方法 ============

    /**
     * 追加查询条件到 SQL
     * 支持查询方式：EQ(等于)、NE(不等于)、LIKE(包含)、GT(大于)、LT(小于)、BETWEEN(范围)
     * 通过 _op 后缀参数指定查询方式，例如：name=xxx&name_op=EQ
     */
    private void appendConditionSql(StringBuilder sql, Map<String, Object> condition) {
        if (condition == null) {
            return;
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
                columnName(fieldKey);
                opMap.put(fieldKey, ((String) value).toUpperCase());
            } else {
                normalConditions.put(key, value);
            }
        }

        // 处理 BETWEEN 条件（同时有 start 和 end）
        java.util.Set<String> betweenKeys = new java.util.HashSet<>(startMap.keySet());
        betweenKeys.retainAll(endMap.keySet());
        for (String fieldKey : betweenKeys) {
            String columnName = columnName(fieldKey);
            sql.append(" AND ").append(columnName).append(" >= #{condition.").append(fieldKey).append("_start} AND ").append(columnName).append(" <= #{condition.").append(fieldKey).append("_end}");
        }
        // 只有 start 没有 end
        for (Map.Entry<String, Object> entry : startMap.entrySet()) {
            if (!endMap.containsKey(entry.getKey())) {
                String columnName = columnName(entry.getKey());
                sql.append(" AND ").append(columnName).append(" >= #{condition.").append(entry.getKey()).append("_start}");
            }
        }
        // 只有 end 没有 start
        for (Map.Entry<String, Object> entry : endMap.entrySet()) {
            if (!startMap.containsKey(entry.getKey())) {
                String columnName = columnName(entry.getKey());
                sql.append(" AND ").append(columnName).append(" <= #{condition.").append(entry.getKey()).append("_end}");
            }
        }

        // 处理普通条件（根据查询方式生成对应 SQL）
        for (Map.Entry<String, Object> entry : normalConditions.entrySet()) {
            String fieldKey = entry.getKey();
            String columnName = columnName(fieldKey);
            Object value = entry.getValue();
            String op = opMap.getOrDefault(fieldKey, "");

            if ("EQ".equals(op)) {
                sql.append(" AND ").append(columnName).append(" = #{condition.").append(fieldKey).append("}");
            } else if ("NE".equals(op)) {
                sql.append(" AND ").append(columnName).append(" <> #{condition.").append(fieldKey).append("}");
            } else if ("GT".equals(op)) {
                sql.append(" AND ").append(columnName).append(" > #{condition.").append(fieldKey).append("}");
            } else if ("LT".equals(op)) {
                sql.append(" AND ").append(columnName).append(" < #{condition.").append(fieldKey).append("}");
            } else if ("LIKE".equals(op) || (op.isEmpty() && value instanceof String)) {
                sql.append(" AND ").append(columnName).append(" LIKE CONCAT('%', #{condition.").append(fieldKey).append("}, '%')");
            } else {
                sql.append(" AND ").append(columnName).append(" = #{condition.").append(fieldKey).append("}");
            }
        }
    }

    private String tableName(Map<String, Object> params) {
        return requireIdentifier((String) params.get("tableName"), "表名");
    }

    private String columnName(String fieldKey) {
        return requireIdentifier(camelToUnderscore(fieldKey), "字段名");
    }

    private String requireIdentifier(String value, String label) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + "不合法");
        }
        return value;
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
