package com.workflow.entity.permission.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.workflow.integration.database.api.DatabaseVendor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证真实 Wrapper 与 MyBatis 脚本组合，避免方言时间或授权分组在重构中退化。 */
class EntityListScopeDelegationMapperSqlTest {

    @Test
    void postgresAndKingbaseUseStatementTimeWhileOtherVendorsKeepTheirSessionClock() {
        for (DatabaseVendor vendor : DatabaseVendor.values()) {
            String sql = render(vendor, "reader", "asset").sql();
            String clock = switch (vendor) {
                case POSTGRESQL, KINGBASE -> "(statement_timestamp() at time zone current_setting('timezone'))";
                case ORACLE, DM, OCEANBASE_ORACLE -> "localtimestamp";
                case MYSQL, OCEANBASE_MYSQL -> "current_timestamp";
            };
            assertTrue(sql.contains("start_time <= " + clock), vendor.name());
            assertTrue(sql.contains("end_time >= " + clock), vendor.name());
            if (vendor == DatabaseVendor.POSTGRESQL || vendor == DatabaseVendor.KINGBASE) {
                assertFalse(sql.contains("current_timestamp"), "委托起止判断不能使用事务开始时间");
            }
        }
    }

    @Test
    void groupedScopeKeepsDeletionGuardBoundValuesNullAndMappedProjection() {
        for (String entityCode : Arrays.asList(null, "", "asset' OR 1=1 --")) {
            Query query = render(DatabaseVendor.POSTGRESQL, "reader' OR 1=1 --", entityCode);
            assertTrue(query.sql().contains("where (to_user_id = ? and enabled = ? and deleted = ? "
                    + "and (entity_code is null or entity_code = ? or entity_code = ?)) "
                    + "and (start_time is null or start_time <="));
            assertEquals(Arrays.asList("reader' OR 1=1 --", 1, 0, "", entityCode), query.values());
            assertFalse(query.sql().contains("or 1=1"), "用户和实体值必须作为参数绑定");
            assertTrue(query.sql().contains("create_time as createdat"));
            assertTrue(query.sql().contains("update_time as updatedat"));
        }
    }

    /** 调用生产默认方法，只截获其执行终点，再用当前工厂 databaseId 编译实际脚本。 */
    private Query render(DatabaseVendor vendor, String userId, String entityCode) {
        var configuration = new MybatisConfiguration();
        configuration.setDatabaseId(vendor.name());
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(EntityListScopeDelegationMapper.class);
        var captured = new AtomicReference<Query>();
        EntityListScopeDelegationMapper mapper = mock(EntityListScopeDelegationMapper.class,
                withSettings().defaultAnswer(call -> {
                    if (!"selectActiveRows".equals(call.getMethod().getName())) {
                        return CALLS_REAL_METHODS.answer(call);
                    }
                    Wrapper<?> wrapper = call.getArgument(0);
                    Map<String, Object> parameters = Map.of(Constants.WRAPPER, wrapper);
                    var statement = configuration.getMappedStatement(
                            EntityListScopeDelegationMapper.class.getName() + ".selectActiveRows");
                    var bound = statement.getBoundSql(parameters);
                    var parameterObject = configuration.newMetaObject(parameters);
                    List<Object> values = bound.getParameterMappings().stream()
                            .map(parameter -> bound.hasAdditionalParameter(parameter.getProperty())
                                    ? bound.getAdditionalParameter(parameter.getProperty())
                                    : parameterObject.getValue(parameter.getProperty()))
                            .toList();
                    captured.set(new Query(bound.getSql().replaceAll("\\s+", " ").trim()
                            .toLowerCase(Locale.ROOT), values));
                    return List.of();
                }));
        mapper.findActiveByToUserId(userId, entityCode);
        assertNotNull(captured.get(), "必须执行 Wrapper 与方言脚本组合的查询");
        return captured.get();
    }

    private record Query(String sql, List<Object> values) { }
}
