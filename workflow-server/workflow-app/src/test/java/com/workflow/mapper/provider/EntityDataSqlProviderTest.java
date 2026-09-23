package com.workflow.mapper.provider;

import com.workflow.entity.data.infrastructure.persistence.provider.EntityDataSqlProvider;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.annotation.ProviderContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 实体数据 SQL 提供器单元测试。
 *
 * <p>被测对象为 {@link EntityDataSqlProvider}，验证表名与条件字段的 SQL 注入防护、
     * 当前任务更新允许显式 null 赋值，以及分页权限查询与计数查询的 SQL 结构。</p>
 */
class EntityDataSqlProviderTest {
    private final ProviderContext context = mysqlContext();

    private static ProviderContext mysqlContext() {
        // ProviderContext 由 MyBatis 创建且构造器不公开；仅在直接渲染测试中构造，
        // 真实 Mapper/绑定路径由 MySQL 实库测试覆盖，不修改项目的 Mockito 策略。
        try {
            var constructor = ProviderContext.class.getDeclaredConstructor(
                    Class.class, java.lang.reflect.Method.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(Object.class, Object.class.getMethod("toString"), "MYSQL");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }


    /** 被测 SQL 提供器实例 */
    private final EntityDataSqlProvider provider = new EntityDataSqlProvider();

    /** 含 SQL 注入的表名应被拒绝 */
    @Test
    void rejectsUnsafeTableName() {
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order;drop table sys_user");

        assertThrows(IllegalArgumentException.class, () -> provider.selectList(params, context));
    }

    /** 含 SQL 注入的条件字段名应被拒绝 */
    @Test
    void rejectsUnsafeConditionField() {
        Map<String, Object> condition = new HashMap<>();
        condition.put("name) OR 1=1 --", "x");

        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");
        params.put("condition", condition);

        assertThrows(IllegalArgumentException.class, () -> provider.selectByCondition(params, context));
    }

    /** 更新当前任务时应允许显式 null 赋值(清空字段) */
    @Test
    void updateCurrentTaskAllowsExplicitNullAssignment() {
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");

        String sql = provider.updateCurrentTask(params, context);

        assertTrue(sql.contains("current_task_id = #{currentTaskId,jdbcType=VARCHAR}"));
        assertTrue(sql.contains("current_task_name = #{currentTaskName,jdbcType=VARCHAR}"));
        assertTrue(sql.contains("current_task_assignee = #{currentTaskAssignee,jdbcType=VARCHAR}"));
    }

    /**
     * 权限查询生成过滤条件和稳定排序，分页子句留给 MyBatis-Plus 插件追加。
     *
     * <p>真实分页及 count 一致性由 MySqlEntityQueryDatabaseTest 验证。</p>
     */
    @Test
    void pagedPermissionQueryLeavesPaginationToFramework() {
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");
        params.put("permissionSql", "dept_id = 'dept-1'");

        String sql = provider.selectPageWithPermission(params, context);

        assertTrue(sql.contains("deleted = 0 AND (dept_id = 'dept-1')"));
        assertTrue(sql.contains("ORDER BY create_time DESC"));
        assertFalse(sql.contains("LIMIT"));
    }

    /**
     * 计数查询应使用与分页查询相同的权限谓词。
     *
     * <p>断言 SQL 含权限条件 AND 括号包裹的权限 SQL 与条件参数占位符。</p>
     */
    @Test
    void countByConditionUsesSamePermissionPredicate() {
        Map<String, Object> condition = new HashMap<>();
        condition.put("status", "OPEN");
        condition.put("status_op", "EQ");
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");
        params.put("permissionSql", "create_by = 'u1'");
        params.put("condition", condition);

        String sql = provider.countByConditionWithPermission(params, context);

        assertTrue(sql.contains("AND (create_by = 'u1')"));
        assertTrue(sql.contains("`status` = #{condition.status}"));
    }

    /** IN 查询应把集合值展开为独立的预编译参数。 */
    @Test
    void inQueryExpandsCollectionValues() {
        Map<String, Object> condition = new HashMap<>();
        condition.put("status", List.of("DRAFT", "PENDING"));
        condition.put("status_op", "IN");
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");
        params.put("condition", condition);

        String sql = provider.selectPageByCondition(params, context);

        assertTrue(sql.contains(
                "`status` IN (#{__condition_status_0}, #{__condition_status_1})"));
        assertEquals("DRAFT", params.get("__condition_status_0"));
        assertEquals("PENDING", params.get("__condition_status_1"));
    }

    /** NOT_IN 查询应使用与 IN 相同的安全参数展开。 */
    @Test
    void notInQueryExpandsCollectionValues() {
        Map<String, Object> condition = new HashMap<>();
        condition.put("status", List.of("TERMINATED", "WITHDRAWN"));
        condition.put("status_op", "NOT_IN");
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "biz_order");
        params.put("condition", condition);

        String sql = provider.countByCondition(params, context);

        assertTrue(sql.contains(
                "`status` NOT IN (#{__condition_status_0}, #{__condition_status_1})"));
        assertEquals("TERMINATED", params.get("__condition_status_0"));
        assertEquals("WITHDRAWN", params.get("__condition_status_1"));
    }
}
