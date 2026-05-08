package com.workflow.mapper.provider;

import org.apache.ibatis.jdbc.SQL;

import java.util.Map;

/**
 * 实体数据动态 SQL 提供者
 * 支持动态表名和动态字段
 */
public class EntityDataSqlProvider {

    /**
     * 根据 ID 查询
     */
    public String selectById(Map<String, Object> params) {
        String tableName = (String) params.get("tableName");
        
        return new SQL() {{
            SELECT("*");
            FROM(tableName);
            WHERE("id = #{id}");
            WHERE("deleted = 0");
        }}.toString();
    }

    /**
     * 根据流程实例ID查询
     */
    public String selectByProcessInstanceId(Map<String, Object> params) {
        String tableName = (String) params.get("tableName");
        
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
        String tableName = (String) params.get("tableName");
        
        SQL sql = new SQL() {{
            SELECT("*");
            FROM(tableName);
            WHERE("deleted = 0");
        }};
        
        // 添加排序
        sql.ORDER_BY("created_at DESC");
        
        return sql.toString();
    }

    /**
     * 条件查询（支持 LIKE 模糊查询和 BETWEEN 范围查询）
     */
    public String selectByCondition(Map<String, Object> params) {
        String tableName = (String) params.get("tableName");
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("condition");
        
        SQL sql = new SQL() {{
            SELECT("*");
            FROM(tableName);
            WHERE("deleted = 0");
        }};
        
        if (condition != null) {
            // 分离 start/end 和普通条件
            Map<String, Object> startMap = new java.util.HashMap<>();
            Map<String, Object> endMap = new java.util.HashMap<>();
            Map<String, Object> normalConditions = new java.util.HashMap<>();
            
            for (Map.Entry<String, Object> entry : condition.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                // 跳过 null 和空字符串
                if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                    continue;
                }
                if (key.endsWith("_start")) {
                    startMap.put(key.substring(0, key.length() - 6), value);
                } else if (key.endsWith("_end")) {
                    endMap.put(key.substring(0, key.length() - 4), value);
                } else {
                    normalConditions.put(key, value);
                }
            }
            
            // 处理 BETWEEN 条件（同时有 start 和 end）
            java.util.Set<String> betweenKeys = new java.util.HashSet<>(startMap.keySet());
            betweenKeys.retainAll(endMap.keySet());
            for (String fieldKey : betweenKeys) {
                String columnName = camelToUnderscore(fieldKey);
                sql.WHERE(columnName + " >= #{condition." + fieldKey + "_start} AND " + columnName + " <= #{condition." + fieldKey + "_end}");
            }
            // 只有 start 没有 end
            for (Map.Entry<String, Object> entry : startMap.entrySet()) {
                if (!endMap.containsKey(entry.getKey())) {
                    String columnName = camelToUnderscore(entry.getKey());
                    sql.WHERE(columnName + " >= #{condition." + entry.getKey() + "_start}");
                }
            }
            // 只有 end 没有 start
            for (Map.Entry<String, Object> entry : endMap.entrySet()) {
                if (!startMap.containsKey(entry.getKey())) {
                    String columnName = camelToUnderscore(entry.getKey());
                    sql.WHERE(columnName + " <= #{condition." + entry.getKey() + "_end}");
                }
            }
            
            // 处理普通条件（字符串使用 LIKE 模糊查询）
            for (Map.Entry<String, Object> entry : normalConditions.entrySet()) {
                String columnName = camelToUnderscore(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof String) {
                    sql.WHERE(columnName + " LIKE CONCAT('%', #{condition." + entry.getKey() + "}, '%')");
                } else {
                    sql.WHERE(columnName + " = #{condition." + entry.getKey() + "}");
                }
            }
        }
        
        sql.ORDER_BY("created_at DESC");
        
        return sql.toString();
    }

    /**
     * 插入数据（动态字段）
     */
    public String insert(Map<String, Object> params) {
        String tableName = (String) params.get("tableName");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) params.get("data");
        
        SQL sql = new SQL();
        sql.INSERT_INTO(tableName);
        
        // 添加所有非空字段（驼峰命名转换为下划线命名）
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() != null) {
                String columnName = camelToUnderscore(entry.getKey());
                sql.VALUES(columnName, "#{data." + entry.getKey() + "}");
            }
        }
        
        return sql.toString();
    }

    /**
     * 更新数据（动态字段）
     */
    public String update(Map<String, Object> params) {
        String tableName = (String) params.get("tableName");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) params.get("data");
        
        SQL sql = new SQL();
        sql.UPDATE(tableName);
        
        // 添加所有非空字段（排除 id，驼峰命名转换为下划线命名）
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (!"id".equals(key) && entry.getValue() != null) {
                String columnName = camelToUnderscore(key);
                sql.SET(columnName + " = #{data." + key + "}");
            }
        }
        
        sql.WHERE("id = #{data.id}");
        
        return sql.toString();
    }

    /**
     * 逻辑删除
     */
    public String deleteById(Map<String, Object> params) {
        String tableName = (String) params.get("tableName");
        
        return new SQL() {{
            UPDATE(tableName);
            SET("deleted = 1");
            SET("updated_at = NOW()");
            WHERE("id = #{id}");
        }}.toString();
    }

    /**
     * 物理删除
     */
    public String physicalDeleteById(Map<String, Object> params) {
        String tableName = (String) params.get("tableName");
        
        return new SQL() {{
            DELETE_FROM(tableName);
            WHERE("id = #{id}");
        }}.toString();
    }

    /**
     * 统计查询
     */
    public String count(Map<String, Object> params) {
        String tableName = (String) params.get("tableName");
        
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM(tableName);
            WHERE("deleted = 0");
        }}.toString();
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
