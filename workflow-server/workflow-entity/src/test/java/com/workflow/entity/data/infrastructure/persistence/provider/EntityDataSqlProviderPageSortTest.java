package com.workflow.entity.data.infrastructure.persistence.provider;

import org.apache.ibatis.builder.annotation.ProviderContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDataSqlProviderPageSortTest {
    private final EntityDataSqlProvider provider = new EntityDataSqlProvider();
    private final ProviderContext context = mysqlContext();

    @Test
    void allPageBranchesSortOnThePhysicalDecimalColumnBeforePagination() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_expense");
        params.put("condition", Map.of("name", "QA"));
        params.put("permissionSql", "create_by = #{permissionParameters.userId}");
        params.put("sortColumn", "amount");
        params.put("sortDirection", "DESC");

        for (String sql : new String[] {
                provider.selectPage(params, context),
                provider.selectPageWithPermission(params, context),
                provider.selectPageByCondition(params, context),
                provider.selectPageByConditionWithPermission(params, context)}) {
            // 直接排序 DECIMAL 物理列，不能 CAST 为文本或只对当前页的 DTO 排序。
            assertTrue(sql.endsWith("ORDER BY `amount` DESC, id DESC"), sql);
        }
    }

    @Test
    void noConfigurationKeepsStableCreationOrderAndUnsafeValuesFailClosed() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_expense");
        assertTrue(provider.selectPage(params, context)
                .endsWith("ORDER BY create_time DESC, id DESC"));

        params.put("sortColumn", "amount; DROP TABLE d_expense");
        params.put("sortDirection", "DESC");
        assertThrows(IllegalArgumentException.class,
                () -> provider.selectPage(params, context));

        params.put("sortColumn", "amount");
        params.put("sortDirection", "DESC; DROP TABLE d_expense");
        assertThrows(IllegalArgumentException.class,
                () -> provider.selectPage(params, context));
    }

    private static ProviderContext mysqlContext() {
        try {
            var constructor = ProviderContext.class.getDeclaredConstructor(
                    Class.class, java.lang.reflect.Method.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(Object.class,
                    Object.class.getMethod("toString"), "MYSQL");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
