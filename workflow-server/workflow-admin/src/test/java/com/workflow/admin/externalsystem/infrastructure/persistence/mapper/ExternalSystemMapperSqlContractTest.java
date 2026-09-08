package com.workflow.admin.externalsystem.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定外部系统聚合保存所依赖的关键 SQL 约束，避免后续重构削弱永久唯一和并发保护。
 */
class ExternalSystemMapperSqlContractTest {

    @Test
    void codeLookupIncludesDeletedRowsAndUsesDirectIndexedEquality()
            throws Exception {
        Method method = ExternalSystemMapper.class.getDeclaredMethod(
                "selectAnyByCode", String.class);
        String sql = sql(method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("where system_code = #{systemcode}"));
        assertFalse(sql.contains("lower("));
        assertFalse(sql.contains("deleted = 0"));
    }

    @Test
    void aggregateMutationsUseLockedActiveParentAndNeverUpdateCode()
            throws Exception {
        Method lockMethod = ExternalSystemMapper.class.getDeclaredMethod(
                "selectForUpdate", String.class);
        String lockSql = sql(
                lockMethod.getAnnotation(Select.class).value());
        assertTrue(lockSql.contains("deleted = 0"));
        assertTrue(lockSql.contains("for update"));

        Method updateMethod = ExternalSystemMapper.class.getDeclaredMethod(
                "updateMutableFields",
                String.class, String.class, String.class, String.class,
                String.class, long.class, String.class,
                LocalDateTime.class);
        String updateSql = sql(
                updateMethod.getAnnotation(Update.class).value());
        assertFalse(updateSql.contains("system_code"));
        assertTrue(updateSql.contains("version = version + 1"));
        assertTrue(updateSql.contains("version = #{expectedversion}"));
        assertTrue(updateSql.contains("updated_by = #{updatedby}"));

        Method statusMethod = ExternalSystemMapper.class.getDeclaredMethod(
                "updateStatus", String.class, String.class, long.class,
                String.class, LocalDateTime.class);
        String statusSql = sql(
                statusMethod.getAnnotation(Update.class).value());
        assertTrue(statusSql.contains("version = version + 1"));
        assertTrue(statusSql.contains("version = #{expectedversion}"));

        Method deleteMethod = ExternalSystemMapper.class.getDeclaredMethod(
                "softDelete", String.class, long.class, String.class,
                LocalDateTime.class);
        String deleteSql = sql(
                deleteMethod.getAnnotation(Update.class).value());
        assertTrue(deleteSql.contains("version = version + 1"));
        assertTrue(deleteSql.contains("version = #{expectedversion}"));
    }

    @Test
    void parameterQueriesOnlyExposeActiveRowsAndBulkDeleteChildren()
            throws Exception {
        Method queryMethod = ExternalSystemParameterMapper.class
                .getDeclaredMethod(
                        "selectActiveByExternalSystemId", String.class);
        String querySql = sql(
                queryMethod.getAnnotation(Select.class).value());
        assertTrue(querySql.contains("deleted = 0"));
        assertTrue(querySql.contains("parameter_value"));

        Method countMethod = ExternalSystemParameterMapper.class
                .getDeclaredMethod(
                        "countActiveByExternalSystemIds", List.class);
        String countSql = sql(
                countMethod.getAnnotation(Select.class).value());
        assertTrue(countSql.contains("group by external_system_id"));

        Method deleteMethod = ExternalSystemParameterMapper.class
                .getDeclaredMethod(
                        "softDeleteByExternalSystemId",
                        String.class, String.class, LocalDateTime.class);
        String deleteSql = sql(
                deleteMethod.getAnnotation(Update.class).value());
        assertTrue(deleteSql.contains("set deleted = 1"));
        assertTrue(deleteSql.contains("updated_by = #{updatedby}"));
    }

    private String sql(String[] fragments) {
        return String.join(" ", fragments)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }
}
