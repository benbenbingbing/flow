package com.workflow.entity.data.infrastructure.persistence.provider;

import com.workflow.entity.data.infrastructure.persistence.provider.EntityRelationProjectionSqlProvider.ColumnProjection;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityRelationProjectionSqlProviderTest {

    private final EntityRelationProjectionSqlProvider provider =
            new EntityRelationProjectionSqlProvider();

    @Test
    void buildsBoundProjectionAndPermissionQuery() {
        Map<String, Object> parameters = base();
        parameters.put("columns", List.of(new ColumnProjection(
                "project_id", "link_0")));
        parameters.put("predicateType", "SCALAR_LINK_IN");
        parameters.put("predicateColumn", "project_id");
        parameters.put("predicateValues", List.of("p-1", "p-2"));

        String sql = provider.selectPage(parameters);

        assertTrue(sql.contains("SELECT id AS record_id, project_id AS link_0"));
        assertTrue(sql.contains("owner_id = #{permissionParameters.owner}"));
        assertTrue(sql.contains("project_id IN (#{predicateValues[0]},#{predicateValues[1]})"));
    }

    @Test
    void rejectsInjectedPinnedColumnBeforeSqlAllocation() {
        Map<String, Object> parameters = base();
        parameters.put("columns", List.of(new ColumnProjection(
                "project_id; DROP TABLE x", "link_0")));
        parameters.put("predicateType", "ID_IN");
        parameters.put("predicateValues", List.of("r-1"));
        assertThrows(IllegalArgumentException.class,
                () -> provider.selectPage(parameters));
    }

    private Map<String, Object> base() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", "biz_requirement");
        result.put("multiTable", "biz_requirement_multi");
        result.put("permissionSql",
                "owner_id = #{permissionParameters.owner}");
        result.put("permissionParameters", Map.of("owner", "u-1"));
        result.put("offset", 0L);
        result.put("pageSize", 20L);
        return result;
    }
}
