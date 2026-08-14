package com.workflow.entity.data.infrastructure.persistence.provider;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDataSqlProviderHistoricalAccessTest {

    private final EntityDataSqlProvider provider = new EntityDataSqlProvider();

    @Test
    void historicalLookupKeepsPermissionButAllowsDeletedRow() {
        String sql = provider.selectByIdIncludingDeletedWithPermission(Map.of(
                "tableName", "entity_asset",
                "permissionSql", "dept_id = #{permissionParameters.deptId}"));

        assertTrue(sql.contains("id = #{id}"));
        assertTrue(sql.contains("dept_id = #{permissionParameters.deptId}"));
        assertFalse(sql.contains("deleted = 0"));
    }
}
