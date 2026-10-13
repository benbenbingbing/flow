package com.workflow.database;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.core.database.jdbc.InitializedDriverDataSource;
import com.workflow.core.database.mybatis.OffsetPage;
import com.workflow.entity.data.application.EntityQueryConditions;
import com.workflow.entity.data.application.EntityTableDefinitionFactory;
import com.workflow.entity.data.application.model.EntityExportBatch;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormPublished;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import com.workflow.process.task.application.model.TaskInboxQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.beans.Introspector;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OceanBase MySQL 的生产 Mapper SQL 冒烟回归。
 *
 * <p>必须显式提供 FLOW_OBMYSQL_TEST_URL/USER/PASSWORD，并预先把 docs/sql/obmysql.sql
 * 导入本机 flow_ob_mapper_* 独立库。禁止连接业务库；不启动 Spring Boot/后台任务。
 * 每次代理调用使用 JDBC 事务并强制回滚，动态探测表只在本独立库按随机名称创建和清理。
 * 输出 target/obmysql-mapper-statements.tsv，记录真实执行结果；任何执行失败均导致测试失败。本测试不声称
 * 覆盖所有动态分支或替代完整业务流程回归。</p>
 */
@EnabledIfEnvironmentVariable(named = "FLOW_OBMYSQL_TEST_URL", matches = ".+")
class OceanBaseMapperDatabaseTest {
    private static final String MISSING = "obp" + Long.toUnsignedString(System.nanoTime(), 36);
    private static String currentMapper = "";
    private static boolean dependencyFixture;
    private static final String DIGEST = "a".repeat(64);
    private static final Map<String, Object> DEPENDENCY_VALUES = dependencyValues();

    /**
     * 给静态 Mapper 生成受控探测参数；动态表、权限、分页和类型化条件使用正式生产契约。
     * 深层业务对象仅用于 SQL 冒烟检查，不把伪造外键/枚举被约束拒绝视为业务成功。
     */
    private static Object value(Type generic, String name, int depth) throws Exception {
        Class<?> type = generic instanceof Class<?> concrete ? concrete
                : generic instanceof ParameterizedType parameterized ? (Class<?>) parameterized.getRawType()
                : String.class;
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (dependencyFixture && DEPENDENCY_VALUES.containsKey(name)) {
            return DEPENDENCY_VALUES.get(name);
        }
        if (dependencyFixture && type == String.class && (lowerName.endsWith("digest") || name.equals("configHash"))) {
            return DIGEST;
        }
        if (name.equals("tableName") || name.equals("table")) {
            return PROBE_TABLE;
        }
        if (Set.of("column", "columnName", "primaryKey", "sortColumn").contains(name)) {
            return "id";
        }
        if (name.equals("sortDirection")) {
            return "ASC";
        }
        if (name.equals("permissionSql")) {
            return "1=0";
        }
        if (name.equals("dataNameColumn")) {
            return "name";
        }
        if (name.equals("cursor")) {
            return null;
        }
        // 这两个旧接口把数值主键声明为 String；合法数字可避免样本触发类型转换错误。
        if (type == String.class && ((currentMapper.contains("EntityDefinitionMapper") && name.equals("id"))
                || (currentMapper.contains("EntityFieldMapper") && name.equals("entityId")))) {
            return "0";
        }
        if (type == String.class || type == CharSequence.class) {
            return lowerName.contains("json") || lowerName.contains("document") ? "{}" : MISSING;
        }
        if (type == int.class || type == Integer.class) {
            return lowerName.contains("offset") ? 0 : 1;
        }
        if (type == long.class || type == Long.class) {
            return lowerName.contains("offset") ? 0L : 1L;
        }
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (type == byte.class || type == Byte.class) {
            return (byte) 0;
        }
        if (type == short.class || type == Short.class) {
            return (short) 1;
        }
        if (type == double.class || type == Double.class) {
            return 1d;
        }
        if (type == float.class || type == Float.class) {
            return 1f;
        }
        if (type == BigDecimal.class) {
            return BigDecimal.ONE;
        }
        if (type == BigInteger.class) {
            return BigInteger.ONE;
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.of(2000, 1, 1, 0, 0).plusHours(dependencyFixture && lowerName.contains("expires") ? 1 : 0);
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2000, 1, 1);
        }
        if (type == Instant.class) {
            return Instant.parse("2000-01-01T00:00:00Z").plusSeconds(dependencyFixture && lowerName.contains("expires") ? 3600 : 0);
        }
        if (type == Timestamp.class) {
            return Timestamp.valueOf("2000-01-01 00:00:00");
        }
        if (type == Date.class) {
            return new Date(946684800000L);
        }
        if (type == byte[].class) {
            return new byte[]{1};
        }
        if (type == OffsetPage.class) {
            return new OffsetPage<>(0, 2);
        }
        if (type == TaskInboxQuery.class) {
            return new TaskInboxQuery(MISSING, "todo", 1, 2, null, null, null, null, null);
        }
        if (IPage.class.isAssignableFrom(type)) {
            return new Page<>(1, 2, false);
        }
        if (Wrapper.class.isAssignableFrom(type)) {
            return new QueryWrapper<>().select("*").apply("1=0");
        }
        if (type.isEnum()) {
            return type.getEnumConstants().length == 0 ? null : type.getEnumConstants()[0];
        }
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), 1);
            Array.set(array, 0, value(type.getComponentType(), name, depth + 1));
            return array;
        }
        if (Collection.class.isAssignableFrom(type)) {
            Type element = generic instanceof ParameterizedType parameterized
                    ? parameterized.getActualTypeArguments()[0] : String.class;
            Object item = value(element, name, depth + 1);
            if (item == null) {
                item = MISSING;
            }
            return Set.class.isAssignableFrom(type) ? Set.of(item) : List.of(item);
        }
        if (Map.class.isAssignableFrom(type)) {
            if (name.equals("condition")) {
                return EntityQueryConditions.fromPublishedFields(Map.of(), List.of());
            }
            if (currentMapper.contains("EntityRelationProjectionMapper")) {
                return projectionParameters();
            }
            return new HashMap<>(Map.of("id", MISSING, "name", MISSING, "deleted", 0));
        }
        if (type == Object.class || type == Serializable.class) {
            return MISSING;
        }
        // 防止递归 DTO 引用导致无限构造；未支持的复杂接口保留 null 并让最终执行暴露缺口。
        if (depth > 3 || type.isInterface()) {
            return null;
        }
        if (type.isRecord()) {
            var components = type.getRecordComponents();
            Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                args[i] = value(components[i].getGenericType(), components[i].getName(), depth + 1);
            }
            var constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();
        for (var property : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
            if (property.getWriteMethod() != null) {
                try {
                    property.getWriteMethod().invoke(instance,
                            value(property.getWriteMethod().getGenericParameterTypes()[0], property.getName(), depth + 1));
                } catch (Exception ignored) {
                    // 可选对象无法通用构造时保留默认值；SQL 执行仍会检查真正需要的字段。
                }
            }
        }
        return instance;
    }

    /** 关系查询 Provider 读取整个参数 Map，因此给出完整但不命中业务行的条件。 */
    private static Map<String, Object> projectionParameters() {
        var parameters = new HashMap<String, Object>();
        parameters.put("tableName", PROBE_TABLE);
        parameters.put("multiTable", PROBE_TABLE + "_multi");
        parameters.put("columns", List.of());
        parameters.put("offset", 0L);
        parameters.put("pageSize", 2L);
        parameters.put("limitPlusOne", 2L);
        parameters.put("permissionSql", "1=0");
        parameters.put("predicateType", "ID_IN");
        parameters.put("predicateValues", List.of(MISSING));
        parameters.put("recordIds", List.of(MISSING));
        parameters.put("multiFieldCodes", List.of(MISSING));
        return parameters;
    }

    private static final String PROBE_TABLE = "biz_ob_probe_"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final Set<String> DEPENDENT_INSERTS = Set.of(
            "EmbedLaunchPersistenceMapper.insertAssertionReplay", "EmbedLaunchPersistenceMapper.insertLaunch",
            "EmbedOperationReceiptMapper.insert", "EmbedSessionExchangeMapper.insertSession",
            "EmbedTrafficControlMapper.insertRuntimeLease", "EmbedManagementMapper.insertBinding",
            "EmbedManagementMapper.insertGrant", "EmbedManagementMapper.insertOrigins",
            "EmbedManagementMapper.insertProvider", "EmbedManagementMapper.insertRelease",
            "EmbedManagementMapper.insertView", "IntegrationApiRequestLeaseMapper.insert");

    /** 注册全仓库真实 Mapper，调用静态/Provider/default 方法并检查所有 Mapper 都有成功执行证据。 */
    @Test
    void executesProductionMapperSqlAgainstAnIsolatedOceanBase() throws Exception {
        String url = System.getenv("FLOW_OBMYSQL_TEST_URL");
        assertTrue(url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/flow_ob_mapper_[a-z0-9_]+(?:\\?.*)?"),
                "只允许本机 flow_ob_mapper_* 独立库，拒绝在业务库上执行探测");
        var source = new InitializedDriverDataSource(url, System.getenv("FLOW_OBMYSQL_TEST_USER"),
                System.getenv("FLOW_OBMYSQL_TEST_PASSWORD"), null,
                DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.OCEANBASE_MYSQL));
        var jdbc = new JdbcTemplate(source);
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).contains("OceanBase"),
                "必须连接真实 OceanBase");
        var ddl = DatabaseDialects.forVendor(DatabaseVendor.OCEANBASE_MYSQL);
        var audit = new StatementAudit();
        List<String> failures = new ArrayList<>();
        try {
            ddl.createTable(EntityTableDefinitionFactory.mainTable(PROBE_TABLE, List.of(), "隔离Mapper验证表"))
                    .forEach(jdbc::execute);
            ddl.createTable(EntityTableDefinitionFactory.multiTable(PROBE_TABLE + "_multi"))
                    .forEach(jdbc::execute);
            var configuration = new MybatisConfiguration();
            configuration.setDatabaseId("OCEANBASE_MYSQL");
            configuration.setMapUnderscoreToCamelCase(true);
            GlobalConfigUtils.getGlobalConfig(configuration).getDbConfig().setLogicDeleteField("deleted");
            var databaseConfiguration = new DatabaseMybatisConfiguration();
            configuration.addInterceptor(databaseConfiguration.mybatisPlusInterceptor(ddl));
            databaseConfiguration.largeTextBindings().customize(configuration);
            configuration.addInterceptor(audit);
            List<Class<?>> types = discoverMappers();
            for (Class<?> type : types) {
                configuration.addMapper(type);
            }
            var bean = new MybatisSqlSessionFactoryBean();
            bean.setDataSource(source);
            bean.setConfiguration(configuration);
            // 默认 SpringManagedTransactionFactory 在没有 Spring 事务时不会替 openSession 管理回滚。
            // 这里显式选 JDBC 工厂，再检查 autoCommit，防止测试写入意外提交。
            bean.setTransactionFactory(new JdbcTransactionFactory());
            var factory = bean.getObject();
            verifyCurrentPublication(factory);
            verifyOrganizationPath(factory);
            verifyDynamicExport(factory);
            verifyDependentInserts(factory);
            for (Class<?> type : types) {
                exerciseMapper(factory, type, failures);
                if (!audit.successfulMappers.contains(type.getName())) {
                    failures.add(type.getName() + ": 没有真实执行成功的 SQL");
                }
            }
            assertTrue(types.size() >= 100, "生产 Mapper 扫描范围意外缩小");
        } finally {
            Files.createDirectories(Path.of("target"));
            Files.write(Path.of("target/obmysql-mapper-statements.tsv"), audit.lines);
            jdbc.execute("DROP TABLE IF EXISTS " + PROBE_TABLE + "_multi");
            jdbc.execute("DROP TABLE IF EXISTS " + PROBE_TABLE);
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    /** 每次调用独立事务，避免一个变更影响后续探测；依赖 INSERT 由专门的完整样本验证。 */
    private void exerciseMapper(SqlSessionFactory factory, Class<?> type, List<String> failures) throws Exception {
        currentMapper = type.getName();
        var methods = new ArrayList<Method>(List.of(type.getDeclaredMethods()));
        if (BaseMapper.class.isAssignableFrom(type)) {
            methods.add(type.getMethod("selectById", Serializable.class));
            methods.add(type.getMethod("selectList", Wrapper.class));
            methods.add(type.getMethod("selectCount", Wrapper.class));
        }
        methods.sort(Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            String label = type.getSimpleName() + "." + method.getName();
            // 成套授权 INSERT 已在 verifyDependentInserts 中用合法外键/枚举/摘要执行并读回。
            if (DEPENDENT_INSERTS.contains(label)) {
                continue;
            }
            try (var session = factory.openSession(false)) {
                try {
                    assertFalse(session.getConnection().getAutoCommit(), "探测写入必须处于可回滚事务");
                    // currentCount 的前置条件是调用者已创建桶，不能把不存在行的 null->int 映射误报为语法问题。
                    if (label.equals("IntegrationRateLimitMapper.currentCount")) {
                        String sql = "INSERT INTO integration_rate_limit_bucket"
                                + "(bucket_key,window_epoch,request_count,update_time) VALUES (?,1,0,CURRENT_TIMESTAMP)";
                        try (var statement = session.getConnection().prepareStatement(sql)) {
                            statement.setString(1, MISSING);
                            statement.executeUpdate();
                        }
                    }
                    Object[] values = new Object[method.getParameterCount()];
                    for (int i = 0; i < values.length; i++) {
                        Param named = method.getParameters()[i].getAnnotation(Param.class);
                        String name = named == null ? method.getParameters()[i].getName() : named.value();
                        values[i] = value(method.getGenericParameterTypes()[i], name, 0);
                    }
                    method.setAccessible(true);
                    method.invoke(session.getMapper(type), values);
                } catch (Throwable error) {
                    Throwable root = rootCause(error);
                    failures.add(label + ": " + root);
                } finally {
                    session.rollback(true);
                }
            }
        }
    }

    /** 当前发布覆盖、默认表单关联、旧摘要不可读取以及失败回滚必须在真实表上保持一致。 */
    private void verifyCurrentPublication(SqlSessionFactory factory) {
        String formId = MISSING + "_form";
        String entityId = MISSING + "_entity";
        String firstHash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        String secondHash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        try (var session = factory.openSession(false)) {
            try {
                var publications = session.getMapper(EntityFormPublishedMapper.class);
                var releases = session.getMapper(UiConfigReleaseMapper.class);
                assertEquals(1, publications.lockPublication());
                var row = new EntityFormPublished();
                row.setFormId(formId);
                row.setEntityId(entityId);
                row.setSnapshotDocument("{}");
                row.setContentHash(firstHash);
                row.setPublishedAt(LocalDateTime.now());
                assertEquals(1, publications.insert(row));
                assertEquals(1, publications.insertDefault(entityId, formId));
                assertEquals(firstHash, releases.findCurrentDefaultForm(entityId).getId());
                assertEquals(formId, releases.selectById(firstHash).getConfigId());
                row.setContentHash(secondHash);
                row.setSnapshotDocument("{\"current\":true}");
                assertEquals(1, publications.updateById(row));
                assertNull(releases.selectById(firstHash));
                assertEquals("{\"current\":true}", releases.findCurrentForm(formId).getSnapshotDocument());
                assertEquals(1, publications.clearDefault(entityId));
                assertNull(releases.findCurrentDefaultForm(entityId));
            } finally {
                session.rollback(true);
            }
        }
        try (var session = factory.openSession(false)) {
            assertNull(session.getMapper(EntityFormPublishedMapper.class).selectById(formId));
            assertNull(session.getMapper(UiConfigReleaseMapper.class).findCurrentDefaultForm(entityId));
            session.rollback(true);
        }
    }

    /** 父路径按请求顺序返回；重复/不存在/逻辑删除 ID 不得扩展结果，空路径不执行无条件查询。 */
    private void verifyOrganizationPath(SqlSessionFactory factory) {
        try (var session = factory.openSession(false)) {
            try {
                var mapper = session.getMapper(SysOrganizationMapper.class);
                for (String suffix : List.of("parent", "child", "deleted")) {
                    var row = new SysOrganization();
                    row.setId(MISSING + "_" + suffix);
                    row.setOrgCode(MISSING + "_" + suffix);
                    row.setOrgName(suffix);
                    row.setType("dept");
                    row.setDeleted(suffix.equals("deleted") ? 1 : 0);
                    row.setStatus("0");
                    assertEquals(1, mapper.insert(row));
                }
                String child = MISSING + "_child";
                String parent = MISSING + "_parent";
                String path = "/" + child + "/" + parent + "/" + child + "/" + MISSING + "_deleted/missing/";
                assertEquals(List.of(child, parent), mapper.selectParentsByPath(path).stream()
                        .map(SysOrganization::getId).toList());
                assertTrue(mapper.selectParentsByPath(null).isEmpty());
                assertTrue(mapper.selectParentsByPath("/").isEmpty());
                assertTrue(mapper.selectParentsByPath("/missing/").isEmpty());
            } finally {
                session.rollback(true);
            }
        }
    }

    /** 动态导出使用真实类型化条件、参数权限与续页游标，验证排序和选中 ID 条件共同生效。 */
    private void verifyDynamicExport(SqlSessionFactory factory) {
        try (var session = factory.openSession(false)) {
            try {
                var mapper = session.getMapper(EntityDataDynamicMapper.class);
                for (String id : List.of("one", "two", "three")) {
                    assertEquals(1, mapper.insert(PROBE_TABLE, Map.of("id", id, "name", id, "create_by", MISSING)));
                }
                var condition = EntityQueryConditions.fromPublishedFields(Map.of(), List.of());
                var first = mapper.selectExportBatch(new OffsetPage<>(0, 1), PROBE_TABLE, condition,
                        "create_by = #{permissionParameters.owner}", Map.of("owner", MISSING),
                        List.of("one", "two"), null, "id", "ASC");
                assertEquals(List.of("one"), first.stream().map(row -> row.get("id").toString()).toList());
                var cursor = new EntityExportBatch.Cursor("one", "one");
                var next = mapper.selectExportBatch(new OffsetPage<>(0, 1), PROBE_TABLE, condition,
                        "create_by = #{permissionParameters.owner}", Map.of("owner", MISSING),
                        List.of("one", "two"), cursor, "id", "ASC");
                assertEquals(List.of("two"), next.stream().map(row -> row.get("id").toString()).toList());
                assertEquals("one", mapper.selectTaskSummary(PROBE_TABLE, "one", "name").get("name"));
            } finally {
                session.rollback(true);
            }
        }
    }

    /**
     * 创建完整应用/用户/授权依赖后执行 12 条 INSERT，验证合法数据可落库并全部回滚。
     * 不关闭外键或 CHECK，不将预期约束错误当作成功；反射只为调用包级可见的管理 Row 类型。
     */
    private void verifyDependentInserts(SqlSessionFactory factory) throws Exception {
        String management = "com.workflow.embed.management.infrastructure.persistence.EmbedManagementMapper";
        String persistence = "com.workflow.embed.infrastructure.persistence.mapper.";
        List<String> insertionOrder = List.of(
                management + ".insertProvider", management + ".insertView", management + ".insertRelease",
                management + ".insertGrant", management + ".insertOrigins", management + ".insertBinding",
                persistence + "EmbedLaunchPersistenceMapper.insertAssertionReplay",
                persistence + "EmbedLaunchPersistenceMapper.insertLaunch",
                persistence + "EmbedSessionExchangeMapper.insertSession",
                persistence + "EmbedOperationReceiptMapper.insert",
                persistence + "EmbedTrafficControlMapper.insertRuntimeLease",
                "com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApiRequestLeaseMapper.insert");
        Map<String, String> insertedTables = Map.ofEntries(
                Map.entry("embed_identity_provider", "id"), Map.entry("embed_view", "id"),
                Map.entry("embed_view_release", "id"), Map.entry("embed_application_grant", "id"),
                Map.entry("embed_allowed_origin", "grant_id"), Map.entry("embed_external_identity_binding", "id"),
                Map.entry("embed_assertion_replay", "provider_id"), Map.entry("embed_launch", "id"),
                Map.entry("embed_session", "id"), Map.entry("embed_operation_receipt", "id"));
        try (var session = factory.openSession(false)) {
            try {
                assertFalse(session.getConnection().getAutoCommit());
                executeFixtureInsert(session.getConnection(),
                        "INSERT INTO integration_application(id,client_id,application_name,created_by,updated_by) VALUES (?,?,?,?,?)",
                        MISSING, MISSING, MISSING, MISSING, MISSING);
                executeFixtureInsert(session.getConnection(),
                        "INSERT INTO sys_user(id,username,password,status,deleted,password_reset_required) VALUES (?,?,?,'0',0,0)",
                        MISSING, MISSING, "isolated-test-no-login");
                dependencyFixture = true;
                for (String statement : insertionOrder) {
                    assertEquals(1, invokeFixtureMapper(session, statement), statement);
                }
                for (var table : insertedTables.entrySet()) {
                    assertEquals(1, countFixtureRows(session.getConnection(), table.getKey(), table.getValue()), table.getKey());
                }
                assertEquals(2, countFixtureRows(session.getConnection(), "integration_api_request_lease", "application_id"));
                // 读回经过生产 Mapper 的 JOIN/结果映射，确认写入后仍可供启动配置和回执查询使用。
                var launches = session.getMapper(com.workflow.embed.infrastructure.persistence.mapper.EmbedLaunchPersistenceMapper.class);
                assertEquals(MISSING, launches.findConfiguration(MISSING, MISSING).applicationId());
                var receipts = session.getMapper(com.workflow.embed.infrastructure.persistence.mapper.EmbedOperationReceiptMapper.class);
                assertEquals(MISSING, receipts.findById(MISSING).applicationId());
            } finally {
                dependencyFixture = false;
                session.rollback(true);
            }
        }
        // 新物理连接检查回滚，不允许复用当前会话缓存冒充无残留证据。
        try (var session = factory.openSession(false)) {
            for (var table : insertedTables.entrySet()) {
                assertEquals(0, countFixtureRows(session.getConnection(), table.getKey(), table.getValue()), table.getKey());
            }
            assertEquals(0, countFixtureRows(session.getConnection(), "integration_application", "id"));
            assertEquals(0, countFixtureRows(session.getConnection(), "sys_user", "id"));
            assertEquals(0, countFixtureRows(session.getConnection(), "integration_api_request_lease", "application_id"));
            session.rollback(true);
        }
    }

    /** 严格使用生产方法参数名构造样本；方法执行仍经 MyBatis 代理与 JDBC 审计。 */
    private int invokeFixtureMapper(SqlSession session, String statement) throws Exception {
        int separator = statement.lastIndexOf('.');
        Class<?> type = Class.forName(statement.substring(0, separator));
        currentMapper = type.getName();
        String methodName = statement.substring(separator + 1);
        Method method = Arrays.stream(type.getDeclaredMethods()).filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        Object[] arguments = new Object[method.getParameterCount()];
        for (int i = 0; i < arguments.length; i++) {
            Param named = method.getParameters()[i].getAnnotation(Param.class);
            String name = named == null ? method.getParameters()[i].getName() : named.value();
            arguments[i] = value(method.getGenericParameterTypes()[i], name, 0);
            // 两个 Mapper 共用租约表，使用不同主键验证两种 INSERT，而不是制造重复键。
            if (name.equals("leaseId") && type.getSimpleName().equals("IntegrationApiRequestLeaseMapper")) {
                arguments[i] = MISSING + "_api";
            }
        }
        method.setAccessible(true);
        return (Integer) method.invoke(session.getMapper(type), arguments);
    }

    private static void executeFixtureInsert(Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            assertEquals(1, statement.executeUpdate());
        }
    }

    /** 表/列只来自上方固定清单；行标识始终绑定，统计只用于验证本次隔离样本与回滚。 */
    private static int countFixtureRows(Connection connection, String table, String keyColumn) throws Exception {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + keyColumn + " = ?")) {
            statement.setString(1, MISSING);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    /** 与数据库 CHECK 对应的合法业务值；其余普通标识统一使用本次唯一的 MISSING。 */
    private static Map<String, Object> dependencyValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("type", "TRUSTED_EXTERNAL_ID");
        values.put("status", "ACTIVE");
        values.put("surfaceType", "LIST");
        values.put("entryMode", "LIST");
        values.put("revisionMode", "FOLLOW_ACTIVE");
        values.put("trustedSubjectAssertion", true);
        values.put("maxSessionSeconds", 1800);
        values.put("parentOrigin", "https://ob-test.invalid");
        values.put("origins", List.of("https://ob-test.invalid"));
        values.put("uiTheme", "light");
        values.put("uiFormPresentation", "seamless");
        values.put("uiLocale", "zh-CN");
        values.put("contextCiphertext", "{}");
        values.put("ciphertext", "{}");
        values.put("operation", "EMBED_RECORD_CREATE");
        for (String nullable : List.of("issuer", "jwksMode", "jwksJson", "jwksUrl", "revokedBy", "revokedAt",
                "consumedAt", "recordId", "pinnedRevision", "defaultFormId", "formReleaseId", "formReleaseVersion",
                "publishedReleaseId")) {
            values.put(nullable, null);
        }
        return values;
    }

    /** classpath 发现生产注解接口，排除测试夹具内部类并按类名去重，避免手写清单漏掉新增 Mapper。 */
    private List<Class<?>> discoverMappers() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var reader = new CachingMetadataReaderFactory();
        var names = new TreeSet<String>();
        for (var resource : resolver.getResources("classpath*:com/workflow/**/*Mapper.class")) {
            String name = reader.getMetadataReader(resource).getClassMetadata().getClassName();
            if (name.contains("$")) {
                continue;
            }
            Class<?> type = Class.forName(name);
            if (type.isInterface() && type.isAnnotationPresent(Mapper.class)) {
                names.add(name);
            }
        }
        var result = new ArrayList<Class<?>>();
        for (String name : names) {
            result.add(Class.forName(name));
        }
        return result;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    /** 在 StatementHandler 真正调用 JDBC 时记录，避免把缓存命中或只生成 BoundSql 当作执行覆盖。 */
    @Intercepts({
            @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
            @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
    })
    static final class StatementAudit implements Interceptor {
        final Set<String> successfulMappers = new HashSet<>();
        final List<String> lines = new ArrayList<>(List.of("result\tstatement\tsql\terror"));

        @Override
        public Object intercept(Invocation call) throws Throwable {
            var handler = (StatementHandler) call.getTarget();
            var target = PluginUtils.realTarget(handler);
            var statement = (MappedStatement) SystemMetaObject.forObject(target).getValue("delegate.mappedStatement");
            String state = "PASS";
            String detail = "";
            try {
                Object result = call.proceed();
                successfulMappers.add(statement.getId().substring(0, statement.getId().lastIndexOf('.')));
                return result;
            } catch (Throwable error) {
                state = "REJECTED";
                detail = rootCause(error).toString();
                throw error;
            } finally {
                lines.add(state + "\t" + statement.getId() + "\t"
                        + handler.getBoundSql().getSql().replaceAll("\\s+", " ")
                        + "\t" + detail.replaceAll("\\s+", " "));
            }
        }
    }
}
