package com.workflow.entity.data.infrastructure.persistence.provider;

import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import org.apache.ibatis.annotations.Options;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void formUniqueCandidatesUseNormalizedValueAndExcludeSelf() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_project");
        params.put("columnName", "project_name");
        params.put("normalizedValue", "project a");
        params.put("excludeRecordId", "record-1");

        String sql = new EntityDataSqlProvider()
                .selectFormUniqueCandidates(params);

        assertTrue(sql.contains(
                "LOWER(TRIM(CAST(project_name AS CHAR))) = #{normalizedValue}"));
        assertTrue(sql.contains("id <> #{excludeRecordId}"));
    }

    @Test
    void formUniqueCandidatesRejectUnsafePublishedColumn() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_project");
        params.put("columnName", "name) OR 1=1 --");
        params.put("normalizedValue", "project a");

        assertThrows(
                IllegalArgumentException.class,
                () -> new EntityDataSqlProvider()
                        .selectFormUniqueCandidates(params));
    }

    @Test
    void formUniqueBlankCandidateIncludesNullAndWhitespace() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_project");
        params.put("columnName", "project_name");
        params.put("normalizedValue", "");

        String sql = new EntityDataSqlProvider()
                .selectFormUniqueCandidates(params);

        assertTrue(sql.contains(
                "project_name IS NULL OR TRIM(CAST(project_name AS CHAR)) = ''"));
    }

    @Test
    void authoritativeFullScanUsesCurrentReadUnderRepeatableRead() {
        Map<String, Object> params = Map.of(
                "tableName", "d_project");
        EntityDataSqlProvider provider =
                new EntityDataSqlProvider();

        assertEquals(
                provider.selectList(params) + " FOR UPDATE",
                provider.selectListForUpdate(params));
        assertFalse(provider.selectList(params)
                .endsWith("FOR UPDATE"));
    }

    @Test
    void authoritativeFilteredScanUsesCurrentReadUnderRepeatableRead() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_project");
        params.put("columnName", "project_name");
        params.put("normalizedValue", "project a");
        params.put("excludeRecordId", "record-1");
        EntityDataSqlProvider provider =
                new EntityDataSqlProvider();

        assertEquals(
                provider.selectFormUniqueCandidates(params)
                        + " FOR UPDATE",
                provider.selectFormUniqueCandidatesForUpdate(
                        params));
        assertFalse(provider.selectFormUniqueCandidates(params)
                .endsWith("FOR UPDATE"));
    }

    @Test
    void authoritativeStatementsBypassMyBatisCaches() throws Exception {
        Options fullScan = EntityDataDynamicMapper.class
                .getMethod("selectListForUpdate", String.class)
                .getAnnotation(Options.class);
        Options filtered = EntityDataDynamicMapper.class
                .getMethod(
                        "selectFormUniqueCandidatesForUpdate",
                        String.class,
                        String.class,
                        String.class,
                        String.class)
                .getAnnotation(Options.class);

        assertFalse(fullScan.useCache());
        assertEquals(
                Options.FlushCachePolicy.TRUE,
                fullScan.flushCache());
        assertFalse(filtered.useCache());
        assertEquals(
                Options.FlushCachePolicy.TRUE,
                filtered.flushCache());
    }
}
