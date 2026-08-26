package com.workflow.entity.data.infrastructure.persistence.provider;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDataSqlProviderCandidateFilterTest {

    @Test
    void trustedIsNullOperatorGeneratesNullPredicate() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_requirement");
        params.put("condition", Map.of(
                "projectId", true,
                "projectId_op", "IS_NULL"));

        String sql = new EntityDataSqlProvider()
                .selectByCondition(params);

        assertTrue(sql.contains("project_id IS NULL"));
        assertFalse(sql.contains("project_id ="));
    }
}
