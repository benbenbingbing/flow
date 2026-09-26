package com.workflow.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.core.database.mybatis.OffsetPage;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.TaskInboxProjectionMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/** 同时验证保留的复杂 SQL 和 Wrapper 入口，分页语法及参数顺序由真实 MP 插件生成。 */
class DatabasePaginationMapperContractTest {
    private static final String SENTINEL = "needle' OR 1=1 --";

    @TestFactory
    Stream<DynamicTest> everyPagedAnnotationRendersWithAndWithoutOptionalFilters() throws Exception {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:com/workflow/**/*Mapper.class");
        var metadata = new CachingMetadataReaderFactory();
        Set<Method> methods = new HashSet<>();
        for (var resource : resources) {
            Class<?> type = Class.forName(metadata.getMetadataReader(resource).getClassMetadata().getClassName(), false,
                    getClass().getClassLoader());
            // 仅扫描生产注册的 Mapper，排除实库测试中以动态表名为参数的内部夹具接口。
            if (!type.isInterface() || !type.isAnnotationPresent(Mapper.class)) continue;
            for (var method : type.getDeclaredMethods()) {
                var select = method.getAnnotation(Select.class);
                if (select != null && (hasPageParameter(method)
                        || String.join(" ", select.value()).contains("DatabaseQuerySql@page"))) methods.add(method);
            }
        }
        // 注册范围用业务模块验证，不依赖手写 SQL 数量；迁移成 Wrapper 后不会被错误排除。
        for (String module : List.of("admin", "process", "entity", "embed")) {
            assertTrue(methods.stream().anyMatch(method -> method.getDeclaringClass().getName()
                    .startsWith("com.workflow." + module + ".")), "缺少模块分页入口: " + module);
        }
        assertTrue(methods.stream().anyMatch(DatabasePaginationMapperContractTest::hasPageParameter),
                "必须覆盖接入 MyBatis-Plus IPage 的复杂 SQL");
        return methods.stream().sorted(Comparator.comparing(method -> method.getDeclaringClass().getName() + method.getName()))
                .flatMap(method -> Stream.of(false, true).map(empty -> DynamicTest.dynamicTest(
                        method.getDeclaringClass().getSimpleName() + "." + method.getName() + (empty ? " [optional empty]" : " [filters set]"),
                        () -> verifyQuery(method, empty))));
    }

    @Test
    void wrapperEntryPointsKeepBoundValuesAndExactOffsets() throws Exception {
        var cases = List.of(
                new PageCase("menu first row", renderWrapper(SysMenuMapper.class,
                        mapper -> mapper.selectByPerm(SENTINEL)), 0, 1),
                new PageCase("release first row", renderWrapper(UiConfigReleaseMapper.class,
                        mapper -> mapper.findByVersion(SENTINEL, "config", 3)), 0, 1),
                new PageCase("record history", renderWrapper(EntityRecordVersionMapper.class,
                        mapper -> mapper.findSummaryPage(SENTINEL, "record", 1, 3)), 1, 3),
                new PageCase("cc filters", renderWrapper(ProcessCcRecordMapper.class,
                        mapper -> mapper.findByCcUserIdFiltered("user", SENTINEL, null, null, null, 1, 3)), 1, 3),
                new PageCase("sla filters", renderWrapper(ProcessTaskSlaMapper.class,
                        mapper -> mapper.findMonitorPage(null, null, null, SENTINEL, 1, 3)), 1, 3));
        for (var item : cases) {
            var query = assertNotNullQuery(item.query(), item.name());
            assertNotNull(query.page(), item.name());
            assertEquals(item.offset(), query.page().offset(), item.name());
            assertEquals(item.limit(), query.page().getSize(), item.name());
            assertFalse(query.page().searchCount(), "已有计数或首行查询不能额外生成 COUNT");
            assertFalse(query.bound().getSql().contains("LIMIT"), "分页必须交给插件，而非 Wrapper.last");
            assertTrue(query.values().stream().anyMatch(value -> SENTINEL.equals(value)
                    || ("%" + SENTINEL + "%").equals(value)), item.name());
            applyPagination(query.configuration(), query.statement(), query.parameters(), query.bound());
            assertSafeSql(query.bound().getSql());
            List<Object> values = query.values();
            List<Object> pageValues = values.subList(values.size() - (item.offset() == 0 ? 1 : 2), values.size());
            assertEquals(item.offset() == 0 ? List.of(item.limit()) : List.of(item.offset(), item.limit()),
                    pageValues.stream().map(value -> ((Number) value).longValue()).toList(), item.name());
        }
    }

    @Test
    void inboxProjectionPaginationUsesConfiguredDatabaseDialect() throws Exception {
        // 覆盖生产使用的五种 MP 分页方言；OceanBase 的两种模式分别复用 MySQL/Oracle。
        for (DbType type : List.of(DbType.MYSQL, DbType.POSTGRE_SQL, DbType.ORACLE_12C, DbType.KINGBASE_ES, DbType.DM)) {
            for (boolean first : List.of(true, false)) {
                BoundQuery query = renderWrapper(TaskInboxProjectionMapper.class, mapper -> {
                    if (first) mapper.findFirstBusinessTask(SENTINEL, "record");
                    else mapper.findUnready(17, 3);
                });
                assertNotNull(query.page());
                assertFalse(query.page().searchCount());
                assertEquals(0, query.page().offset());
                assertEquals(first ? 1 : 3, query.page().getSize());
                String original = query.bound().getSql();
                List<Object> originalValues = query.values();
                new PaginationInnerInterceptor(type).beforeQuery(null, query.statement(), query.parameters(),
                        RowBounds.DEFAULT, null, query.bound());
                assertNotEquals(original, query.bound().getSql(), type + " 必须在数据库端限制数量");
                assertFalse(query.bound().getSql().contains(SENTINEL));
                assertEquals(originalValues, query.values().subList(0, originalValues.size()));
                long limit = first ? 1L : 3L;
                List<Long> expected = switch (type) {
                    case ORACLE_12C -> List.of(0L, limit);
                    case DM -> List.of(limit, 0L);
                    default -> List.of(limit);
                };
                assertEquals(expected, query.values().subList(originalValues.size(), query.values().size())
                        .stream().map(value -> ((Number) value).longValue()).toList(), type.name());
            }
        }
    }

    private void verifyQuery(Method method, boolean emptyOptional) throws Exception {
        var config = new Configuration();
        config.setDatabaseId("MYSQL");
        config.addMapper(method.getDeclaringClass());
        Map<String, Object> values = parameters(config, method, emptyOptional);
        var statement = config.getMappedStatement(method.getDeclaringClass().getName() + "." + method.getName());
        var bound = statement.getBoundSql(values);
        if (hasPageParameter(method)) applyPagination(config, statement, values, bound);
        assertSafeSql(bound.getSql());
        // 读取全部绑定值，覆盖 foreach 临时参数、框架分页附加参数和参数改名。
        for (var mapping : bound.getParameterMappings()) {
            if (!bound.hasAdditionalParameter(mapping.getProperty())) {
                assertTrue(config.newMetaObject(values).hasGetter(mapping.getProperty()), mapping.getProperty());
                config.newMetaObject(values).getValue(mapping.getProperty());
            }
        }
    }

    private static void assertSafeSql(String sql) {
        assertTrue(sql.contains("LIMIT "), sql);
        assertFalse(sql.contains("${") || sql.contains("#{") || sql.contains("<script>") || sql.contains("&lt;"), sql);
        assertFalse(sql.contains(SENTINEL), "条件值不得进入 SQL 文本");
    }

    private static void applyPagination(Configuration config, MappedStatement statement,
            Map<String, Object> values, BoundSql bound) throws Exception {
        // beforeQuery 只改写 SQL 和参数，指定方言后不连接数据库，也不执行 COUNT。
        new PaginationInnerInterceptor(DbType.MYSQL).beforeQuery(null, statement, values,
                RowBounds.DEFAULT, null, bound);
    }

    private static boolean hasPageParameter(Method method) {
        return Arrays.stream(method.getParameterTypes()).anyMatch(IPage.class::isAssignableFrom);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parameters(Configuration configuration, Method method, boolean emptyOptional) {
        Object[] arguments = new Object[method.getParameterCount()];
        for (int index = 0; index < arguments.length; index++) {
            var parameter = method.getParameters()[index];
            var named = parameter.getAnnotation(Param.class);
            Class<?> type = parameter.getType();
            // IPage 由插件从参数值中识别，不要求 @Param；业务条件仍需显式命名。
            if (!IPage.class.isAssignableFrom(type)) {
                assertNotNull(named, method + " 的业务查询参数应显式命名");
            }
            String name = named == null ? parameter.getName() : named.value();
            Object value;
            if (IPage.class.isAssignableFrom(type)) value = new OffsetPage<>(1, 3);
            else if (type == int.class || type == Integer.class) value = name.equals("offset") ? 1 : 3;
            else if (type == long.class || type == Long.class) value = name.equals("offset") ? 1L : 3L;
            else if (type == boolean.class || type == Boolean.class) value = !emptyOptional;
            else if (Set.class.isAssignableFrom(type)) value = Set.of("one", "two");
            else if (Collection.class.isAssignableFrom(type)) value = List.of("one", "two");
            else if (emptyOptional) value = null;
            else if (type == String.class) value = SENTINEL;
            else if (type == LocalDateTime.class) value = LocalDateTime.of(2026, 1, 1, 0, 0);
            else if (type == java.time.LocalDate.class) value = java.time.LocalDate.of(2026, 1, 1);
            else if (Date.class.isAssignableFrom(type)) value = new Date(0);
            else throw new AssertionError("需要显式构造测试参数: " + method + " / " + type);
            arguments[index] = value;
        }
        // 与真实 Mapper 代理共用参数解析，覆盖未注解的 page、@Param 及 paramN 别名。
        Object parameters = new ParamNameResolver(configuration, method).getNamedParams(arguments);
        assertInstanceOf(Map.class, parameters, method + " 应使用命名查询参数");
        return (Map<String, Object>) parameters;
    }

    /** 执行生产 Mapper 的默认方法，仅截获 BaseMapper 终点，再由 MP 生成真实 BoundSql。 */
    static <T> BoundQuery renderWrapper(Class<T> mapperType, Consumer<T> invocation) {
        var config = new MybatisConfiguration();
        config.setDatabaseId("MYSQL");
        config.setMapUnderscoreToCamelCase(true);
        GlobalConfigUtils.getGlobalConfig(config).getDbConfig().setLogicDeleteField("deleted");
        config.addMapper(mapperType);
        var captured = new AtomicReference<BoundQuery>();
        T mapper = mock(mapperType, withSettings().defaultAnswer(call -> {
            Wrapper<?> wrapper = Arrays.stream(call.getArguments()).filter(Wrapper.class::isInstance)
                    .map(Wrapper.class::cast).findFirst().orElse(null);
            if (wrapper == null) return CALLS_REAL_METHODS.answer(call);
            String operation = call.getMethod().getName();
            assertTrue(Set.of("selectList", "selectCount", "selectPage").contains(operation), operation);
            IPage<?> page = Arrays.stream(call.getArguments()).filter(IPage.class::isInstance)
                    .map(IPage.class::cast).findFirst().orElse(null);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("ew", wrapper);
            if (page != null) parameters.put("page", page);
            var statement = config.getMappedStatement(mapperType.getName() + "."
                    + (operation.equals("selectPage") ? "selectList" : operation));
            captured.set(new BoundQuery(config, statement, statement.getBoundSql(parameters), parameters, page));
            if (operation.equals("selectCount")) return 0L;
            if (operation.equals("selectPage")) return page;
            return List.of();
        }));
        invocation.accept(mapper);
        return captured.get();
    }

    static BoundQuery assertNotNullQuery(BoundQuery query, String name) {
        assertNotNull(query, "未经过 Wrapper 查询: " + name);
        return query;
    }

    record BoundQuery(Configuration configuration, MappedStatement statement, BoundSql bound,
            Map<String, Object> parameters, IPage<?> page) {
        List<Object> values() {
            return bound.getParameterMappings().stream().map(mapping ->
                    bound.hasAdditionalParameter(mapping.getProperty())
                            ? bound.getAdditionalParameter(mapping.getProperty())
                            : configuration.newMetaObject(parameters).getValue(mapping.getProperty())).toList();
        }
    }

    private record PageCase(String name, BoundQuery query, long offset, long limit) { }
}
