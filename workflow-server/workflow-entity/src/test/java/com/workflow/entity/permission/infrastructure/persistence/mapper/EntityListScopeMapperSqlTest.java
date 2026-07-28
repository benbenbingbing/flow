package com.workflow.entity.permission.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Delete;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据范围草稿清理 SQL 回归测试。
 */
class EntityListScopeMapperSqlTest {

    @Test
    void policyCleanupPhysicallyDeletesOnlyHistoricalRows() throws Exception {
        assertHistoricalDelete(
                EntityListScopePolicyMapper.class.getMethod(
                        "purgeDeletedByEntityCode", String.class));
    }

    @Test
    void bindingCleanupPhysicallyDeletesOnlyHistoricalRows() throws Exception {
        assertHistoricalDelete(
                EntityListScopeBindingMapper.class.getMethod(
                        "purgeDeletedByEntityCode", String.class));
    }

    private void assertHistoricalDelete(Method method) {
        Delete delete = method.getAnnotation(Delete.class);
        String sql = String.join(" ", delete.value()).toLowerCase();
        assertTrue(sql.startsWith("delete from"));
        assertTrue(sql.contains("entity_code = #{entitycode}"));
        assertTrue(sql.contains("deleted = 1"));
    }
}
