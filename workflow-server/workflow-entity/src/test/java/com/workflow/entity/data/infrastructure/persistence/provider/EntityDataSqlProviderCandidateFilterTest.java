package com.workflow.entity.data.infrastructure.persistence.provider;

import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import org.apache.ibatis.annotations.Options;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.annotation.ProviderContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDataSqlProviderCandidateFilterTest {
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


    @Test
    void trustedIsNullOperatorGeneratesNullPredicate() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_requirement");
        params.put("condition", Map.of(
                "projectId", true,
                "projectId_op", "IS_NULL"));

        String sql = new EntityDataSqlProvider()
                .selectByCondition(params, context);

        assertTrue(sql.contains("`project_id` IS NULL"));
        assertFalse(sql.contains("`project_id` ="));
    }

    @Test
    void formUniqueCandidatesUseNormalizedValueAndExcludeSelf() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_project");
        params.put("columnName", "project_name");
        params.put("normalizedValue", "project a");
        params.put("excludeRecordId", "record-1");

        String sql = new EntityDataSqlProvider()
                .selectFormUniqueCandidates(params, context);

        assertTrue(sql.contains(
                "REGEXP_LIKE(`project_name`, CAST(#{_uniqueAsciiPattern,jdbcType=VARCHAR} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin, 'c')"));
        assertTrue(sql.contains("id <> #{excludeRecordId,jdbcType=VARCHAR}"));
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
                        .selectFormUniqueCandidates(params, context));
    }

    @Test
    void formUniqueBlankCandidateIncludesNullAndWhitespace() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "d_project");
        params.put("columnName", "project_name");
        params.put("normalizedValue", "");

        String sql = new EntityDataSqlProvider()
                .selectFormUniqueCandidates(params, context);

        assertTrue(sql.contains(
                "`project_name` IS NULL OR LENGTH(`project_name`) = 0"));
    }

    @Test
    void authoritativeFullScanUsesCurrentReadUnderRepeatableRead() {
        Map<String, Object> params = Map.of(
                "tableName", "d_project");
        EntityDataSqlProvider provider =
                new EntityDataSqlProvider();

        assertEquals(
                provider.selectList(params, context) + " FOR UPDATE",
                provider.selectListForUpdate(params, context));
        assertFalse(provider.selectList(params, context)
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
                provider.selectFormUniqueCandidates(params, context)
                        + " FOR UPDATE",
                provider.selectFormUniqueCandidatesForUpdate(
                        params, context));
        assertFalse(provider.selectFormUniqueCandidates(params, context)
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
