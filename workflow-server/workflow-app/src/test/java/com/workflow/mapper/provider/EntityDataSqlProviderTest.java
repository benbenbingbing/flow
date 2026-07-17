package com.workflow.mapper.provider;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityDataSqlProviderTest {

    private final EntityDataSqlProvider provider = new EntityDataSqlProvider();

    @Test
    void rejectsUnsafeTableName() {
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order;drop table sys_user");

        assertThrows(IllegalArgumentException.class, () -> provider.selectList(params));
    }

    @Test
    void rejectsUnsafeConditionField() {
        Map<String, Object> condition = new HashMap<>();
        condition.put("name) OR 1=1 --", "x");

        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");
        params.put("condition", condition);

        assertThrows(IllegalArgumentException.class, () -> provider.selectByCondition(params));
    }

    @Test
    void updateCurrentTaskAllowsExplicitNullAssignment() {
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");

        String sql = provider.updateCurrentTask(params);

        assertTrue(sql.contains("current_task_id = #{currentTaskId}"));
        assertTrue(sql.contains("current_task_name = #{currentTaskName}"));
        assertTrue(sql.contains("current_task_assignee = #{currentTaskAssignee}"));
    }

    @Test
    void pagedPermissionQueryFiltersBeforeLimit() {
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");
        params.put("permissionSql", "dept_id = 'dept-1'");

        String sql = provider.selectPageWithPermission(params);

        assertTrue(sql.contains("deleted = 0 AND (dept_id = 'dept-1')"));
        assertTrue(sql.contains("ORDER BY create_time DESC"));
        assertTrue(sql.contains("LIMIT #{offset}, #{limit}"));
    }

    @Test
    void countByConditionUsesSamePermissionPredicate() {
        Map<String, Object> condition = new HashMap<>();
        condition.put("status", "OPEN");
        condition.put("status_op", "EQ");
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");
        params.put("permissionSql", "create_by = 'u1'");
        params.put("condition", condition);

        String sql = provider.countByConditionWithPermission(params);

        assertTrue(sql.contains("AND (create_by = 'u1')"));
        assertTrue(sql.contains("status = #{condition.status}"));
    }
}
