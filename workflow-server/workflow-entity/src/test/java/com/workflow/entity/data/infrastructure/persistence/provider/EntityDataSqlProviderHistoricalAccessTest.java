package com.workflow.entity.data.infrastructure.persistence.provider;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.annotation.ProviderContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDataSqlProviderHistoricalAccessTest {
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


    private final EntityDataSqlProvider provider = new EntityDataSqlProvider();

    @Test
    void historicalLookupKeepsPermissionButAllowsDeletedRow() {
        String sql = provider.selectByIdIncludingDeletedWithPermission(Map.of(
                "tableName", "entity_asset",
                "permissionSql", "dept_id = #{permissionParameters.deptId}"), context);

        assertTrue(sql.contains("id = #{id}"));
        assertTrue(sql.contains("dept_id = #{permissionParameters.deptId}"));
        assertFalse(sql.contains("deleted = 0"));
    }
}
