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
     * 条件查询
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
        
        // 动态添加条件
        if (condition != null) {
            for (Map.Entry<String, Object> entry : condition.entrySet()) {
                if (entry.getValue() != null) {
                    sql.WHERE(entry.getKey() + " = #{condition." + entry.getKey() + "}");
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
