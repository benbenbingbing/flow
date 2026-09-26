package com.workflow.admin.externalsystem.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.workflow.admin.externalsystem.infrastructure.persistence.record.ExternalSystemParameterRecord;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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
                "selectAnyByCodePage", com.workflow.core.database.mybatis.OffsetPage.class, String.class);
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

        // 检查真实 MP 生成的语句，不能在改用默认方法后继续依赖 @Update 注解。
        var configuration = new MybatisConfiguration();
        configuration.addMapper(ExternalSystemMapper.class);
        var mapper = mock(ExternalSystemMapper.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            Map<String, Object> parameters = new java.util.HashMap<>();
            parameters.put("et", null);
            parameters.put("ew", invocation.getArgument(1));
            String updateSql = sql(new String[]{configuration
                    .getMappedStatement(ExternalSystemMapper.class.getName() + ".update")
                    .getBoundSql(parameters).getSql()}).replaceAll("\\s+", "");
            assertFalse(updateSql.contains("system_code"));
            assertTrue(updateSql.contains("version=version+1"));
            assertTrue(updateSql.contains("updated_by=?"));
            assertTrue(updateSql.contains("update_time=?"));
            String where = updateSql.substring(updateSql.indexOf("where"));
            assertTrue(where.contains("deleted=0"));
            assertTrue(where.contains("id=?"));
            assertTrue(where.contains("version=?"));
            return 1;
        }).when(mapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        mapper.updateMutableFields("id", "name", "0", null, null, 4L, "actor", now);
        mapper.updateStatus("id", "1", 4L, "actor", now);
        mapper.softDelete("id", 4L, "actor", now);
        verify(mapper, times(3)).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void parameterQueriesOnlyExposeActiveRowsAndBulkDeleteChildren()
            throws Exception {
        // 通过真实 MP 映射生成最终 SQL，验证框架追加的逻辑删除和 Wrapper 查询范围。
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig()
                .setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteField("deleted")));
        configuration.addMapper(ExternalSystemParameterMapper.class);
        ExternalSystemParameterMapper mapper = mock(ExternalSystemParameterMapper.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            Wrapper<ExternalSystemParameterRecord> wrapper = invocation.getArgument(0);
            var bound = configuration.getMappedStatement(ExternalSystemParameterMapper.class.getName() + ".selectList")
                    .getBoundSql(Map.of("ew", wrapper));
            String querySql = sql(new String[]{bound.getSql()});
            assertTrue(querySql.contains("deleted=0"));
            assertTrue(querySql.contains("parameter_value"));
            assertTrue(querySql.contains("external_system_id = ?"));
            assertTrue(querySql.contains("order by sort_order asc,parameter_name_en asc,id asc"));
            return List.of();
        }).when(mapper).selectList(any());
        mapper.selectActiveByExternalSystemId("system-id");

        Method countMethod = ExternalSystemParameterMapper.class
                .getDeclaredMethod(
                        "countActiveByExternalSystemIds", List.class);
        String countSql = sql(
                countMethod.getAnnotation(Select.class).value());
        assertTrue(countSql.contains("group by external_system_id"));

        doAnswer(invocation -> {
            Wrapper<ExternalSystemParameterRecord> wrapper = invocation.getArgument(1);
            Map<String, Object> parameters = new java.util.HashMap<>();
            parameters.put("ew", wrapper);
            parameters.put("et", null);
            String deleteSql = sql(new String[]{configuration
                    .getMappedStatement(ExternalSystemParameterMapper.class.getName() + ".update")
                    .getBoundSql(parameters).getSql()});
            assertTrue(deleteSql.contains("set deleted=?"));
            assertTrue(deleteSql.contains("updated_by=?"));
            assertTrue(deleteSql.contains("update_time=?"));
            assertTrue(deleteSql.contains("deleted=0"));
            assertTrue(deleteSql.contains("external_system_id = ?"));
            return 1;
        }).when(mapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        mapper.softDeleteByExternalSystemId("system-id", "actor", LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private String sql(String[] fragments) {
        return String.join(" ", fragments)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }
}
