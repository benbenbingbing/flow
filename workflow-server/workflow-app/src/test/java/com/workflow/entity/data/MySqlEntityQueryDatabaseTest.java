package com.workflow.entity.data;

import com.workflow.core.database.jdbc.InitializedDriverDataSource;

import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.contracts.process.port.ProcessTaskAccessPort;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationProjectionMapper;
import com.workflow.entity.data.infrastructure.persistence.provider.EntityRelationProjectionSqlProvider.ColumnProjection;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.permission.application.PermissionSqlBuilder;
import com.workflow.core.database.*;
import com.workflow.config.database.*;
import com.workflow.integration.database.schema.dialect.MySqlSchemaDdlDialect;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 随机隔离表上执行真实 Mapper，验证方言选取、参数绑定、权限、排序及动态列，不改业务表。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlEntityQueryDatabaseTest {
    private static final String OWNER_SCOPE = "owner_id = #{permissionParameters.owner}";
    private static final Map<String, Object> OWNER = Map.of("owner", "a");

    @Test
    void pagesAndCountsApplyTheSamePermissionsAndBoundConditions() throws Exception {
        try (var f = new Fixture(); var session = f.sessions.openSession(true)) {
            var mapper = session.getMapper(EntityDataDynamicMapper.class);
            assertTrue(f.jdbc.queryForMap("SELECT * FROM " + f.table + " WHERE id='r01'")
                    .keySet().stream().anyMatch("ID"::equals));
            var normalized = mapper.selectById(f.table, "r01");
            assertEquals("r01", normalized.get("id"));
            assertEquals("plain", normalized.get("order"));
            assertFalse(normalized.containsKey("ID"));
            assertEquals(7, mapper.count(f.table));
            assertEquals(List.of("r05", "r04"), ids(mapper.selectPage(f.table, 2, 2)));
            assertEquals(List.of("r01"), ids(mapper.selectPage(f.table, 6, 2)));
            assertTrue(mapper.selectPage(f.table, 7, 2).isEmpty());
            assertTrue(mapper.selectPage(f.table, 0, 0).isEmpty());
            assertEquals(5, mapper.countWithPermission(f.table, OWNER_SCOPE, OWNER));
            assertEquals(List.of("r05", "r04"), ids(mapper.selectPageWithPermission(f.table, OWNER_SCOPE, OWNER, 1, 2)));

            // IN 值中故意带引号及 SQL 标记，确认增加 ProviderContext 后仍由 MyBatis 绑定。
            Map<String, Object> condition = Map.of("status_op", "IN", "status", List.of("OPEN", "' OR 1=1 --"));
            assertEquals(4, mapper.countByConditionWithPermission(f.table, condition, OWNER_SCOPE, OWNER));
            assertEquals(List.of("r05", "r04"), ids(mapper.selectPageByConditionWithPermission(
                    f.table, condition, OWNER_SCOPE, OWNER, 1, 2)));
            assertEquals(List.of("r05", "r04"), ids(mapper.selectPageByCondition(f.table, condition, 1, 2)));
            assertEquals(0, mapper.countByCondition(f.table, Map.of("status_op", "IN", "status", List.of())));
            assertEquals(7, mapper.countByCondition(f.table, Map.of("status_op", "NOT_IN", "status", List.of())));
            assertEquals(Set.of("r04", "r05"), new HashSet<>(ids(mapper.selectByCondition(f.table,
                    Map.of("order", "needle", "order_op", "LIKE")))));
            assertEquals(2, mapper.countByCondition(f.table, Map.of("order", "needle", "order_op", "LIKE")));
            assertNull(mapper.selectByIdWithPermission(f.table, "r02", OWNER_SCOPE, OWNER));
            assertNull(mapper.selectByIdWithPermission(f.table, "r06", OWNER_SCOPE, OWNER));
            assertEquals("r06", mapper.selectByIdIncludingDeletedWithPermission(f.table, "r06", OWNER_SCOPE, OWNER).get("id"));
            assertNull(mapper.selectByIdIncludingDeletedWithPermission(f.table, "r07", OWNER_SCOPE, OWNER));
        }
    }

    @Test
    void quotedDynamicFieldsSupportWritesNullsUniqueCandidatesAndDeletes() throws Exception {
        try (var f = new Fixture(); var session = f.sessions.openSession(false)) {
            var mapper = session.getMapper(EntityDataDynamicMapper.class);
            var data = new LinkedHashMap<String, Object>();
            data.put("id", "new-record"); data.put("order", "  中文 O'Reilly\\路径  ");
            data.put("projectId", "p-new"); data.put("deleted", 0); data.put("status", "OPEN");
            data.put("createTime", LocalDateTime.of(2026, 1, 1, 0, 0));
            assertEquals(1, mapper.insert(f.table, data));
            assertEquals(data.get("order"), mapper.selectByIdForUpdate(f.table, "new-record").get("order"));
            assertEquals(List.of("new-record"), ids(mapper.selectFormUniqueCandidatesForUpdate(
                    f.table, "order", "中文 o'reilly\\路径", null)));
            assertTrue(mapper.selectFormUniqueCandidates(f.table, "order", "中文 o'reilly\\路径", "new-record").isEmpty());

            data.clear(); data.put("id", "new-record"); data.put("order", null);
            assertEquals(1, mapper.update(f.table, data));
            assertTrue(mapper.selectById(f.table, "new-record").get("order") == null);
            assertEquals(List.of("new-record"), ids(mapper.selectFormUniqueCandidates(f.table, "order", "", null)));
            assertEquals(1, mapper.updateCurrentTask(f.table, "new-record", "task-1", "审批", "lisi"));
            assertEquals(1, mapper.updateCurrentTask(f.table, "new-record", null, null, null));
            var cleared = mapper.selectById(f.table, "new-record");
            assertNull(cleared.get("current_task_id"));
            assertNotNull(cleared.get("update_time"));
            assertEquals(1, mapper.deleteById(f.table, "new-record"));
            assertNull(mapper.selectById(f.table, "new-record"));
            assertNotNull(mapper.selectByIdIncludingDeleted(f.table, "new-record"));
            assertEquals(1, mapper.physicalDeleteById(f.table, "new-record"));
            assertNull(mapper.selectByIdIncludingDeleted(f.table, "new-record"));
            session.commit();
        }
    }

    @Test
    void actualTodoPermissionBuilderBindsSpecialIdsAndDoesNotNeedConvert() throws Exception {
        try (var f = new Fixture(); var session = f.sessions.openSession(true)) {
            String special = "审批\\' OR 1=1 -- ${value} #{value}";
            f.jdbc.update("INSERT INTO " + f.table + " (id,status,deleted,create_time) VALUES (?, 'OPEN', 0, ?)",
                    special, LocalDateTime.of(2026, 1, 2, 0, 0));
            var resolver = mock(EntityPhysicalTableResolver.class);
            when(resolver.resolve("expense")).thenReturn(f.table);
            var tasks = mock(ProcessTaskAccessPort.class);
            when(tasks.findActionableEntityDataIds("reader", "expense")).thenReturn(List.of("r01", special, "r06"));
            var builder = new PermissionSqlBuilder(null, null, null, List.of(), null, resolver, null, tasks,
                com.workflow.integration.database.api.query.DatabaseQueryDialects.forVendor(
                        com.workflow.integration.database.api.DatabaseVendor.MYSQL));
            var filter = new FilterConfigDTO(); filter.setType("HAS_TODO");
            var user = new SysUser(); user.setId("reader"); user.setUsername("lisi");
            var bindings = new LinkedHashMap<String, Object>();
            String sql = builder.buildFilterSql("expense", filter, user, bindings);
            assertFalse(sql.contains("CONVERT")); assertFalse(sql.contains(special));
            var mapper = session.getMapper(EntityDataDynamicMapper.class);
            assertEquals(2, mapper.countWithPermission(f.table, sql, bindings));
            assertEquals(List.of(special), ids(mapper.selectPageWithPermission(f.table, sql, bindings, 0, 1)));
            assertEquals(List.of("r01"), ids(mapper.selectPageWithPermission(f.table, sql, bindings, 1, 1)));
            assertNull(mapper.selectByIdWithPermission(f.table, "r02", sql, bindings));
        }
    }

    @Test
    void relationProjectionKeepsAliasesScopeAndMultiValueLimits() throws Exception {
        try (var f = new Fixture(); var session = f.sessions.openSession(true)) {
            var mapper = session.getMapper(EntityRelationProjectionMapper.class);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("tableName", f.table); params.put("multiTable", f.multi);
            params.put("columns", List.of(new ColumnProjection("project_id", "link_0")));
            params.put("permissionSql", OWNER_SCOPE); params.put("permissionParameters", OWNER);
            params.put("predicateType", "SCALAR_LINK_IN"); params.put("predicateColumn", "project_id");
            params.put("predicateValues", List.of("p1")); params.put("offset", 1); params.put("pageSize", 2);
            assertEquals(3, mapper.count(params));
            var rows = mapper.selectPage(params);
            assertEquals(List.of("r03", "r05"), rows.stream().map(row -> row.get("record_id")).toList());
            assertEquals("p1", rows.get(0).get("link_0"));
            params.put("predicateType", "ID_IN"); params.put("predicateValues", List.of("r01", "r02", "r03"));
            params.put("offset", 0);
            assertEquals(2, mapper.count(params));
            assertEquals(2, mapper.selectPage(params).size());
            params.put("predicateType", "MULTI_LINK_IN"); params.put("predicateValues", List.of("target"));
            params.put("predicateFieldCode", "owners"); params.put("predicateTargetEntityId", "person");
            assertEquals(2, mapper.count(params));
            assertEquals(2, mapper.selectPage(params).size());
            params.put("recordIds", List.of("r01", "r03")); params.put("multiFieldCodes", List.of("owners"));
            params.put("limitPlusOne", 2);
            var links = mapper.selectMultiValues(params);
            assertEquals(2, links.size());
            assertEquals(List.of("target", "other"), links.stream().map(row -> row.get("target_record_id")).toList());
            params.put("predicateValues", List.of());
            assertEquals(0, mapper.count(params));
            assertTrue(mapper.selectPage(params).isEmpty());
        }
    }

    @Test
    void unsafeDynamicIdentifiersFailBeforeExecution() throws Exception {
        try (var f = new Fixture(); var session = f.sessions.openSession(true)) {
            var mapper = session.getMapper(EntityDataDynamicMapper.class);
            assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> mapper.selectPage(f.table + "; DROP TABLE x", 0, 1));
            assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> mapper.selectByCondition(f.table, Map.of("order` OR 1=1 --", "needle")));
            assertEquals(7, mapper.count(f.table));
        }
    }

    private static List<String> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> (String) row.get("id")).toList();
    }

    @Test
    void mybatisPlusFactoryUsesConfiguredDatabaseIdAndLoadsTheRowResultMap() throws Exception {
        try (var f = new Fixture()) {
            var database = new DatabaseMybatisConfiguration();
            var dialect = new MySqlSchemaDdlDialect();
            var bean = new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
            bean.setDataSource(f.source);
            bean.setDatabaseIdProvider(database.databaseIdProvider(dialect));
            bean.setPlugins(database.mybatisPlusInterceptor(dialect));
            var factory = bean.getObject();
            assertNotNull(factory);
            assertEquals("MYSQL", factory.getConfiguration().getDatabaseId());
            // 与应用中的 Mapper 扫描顺序一致：先初始化工厂，再注册 Mapper。
            factory.getConfiguration().addMapper(EntityDataDynamicMapper.class);
            try (var session = factory.openSession(true)) {
                var mapper = session.getMapper(EntityDataDynamicMapper.class);
                assertEquals(List.of("r05", "r04"), ids(mapper.selectPage(f.table, 2, 2)));
                assertEquals("plain", mapper.selectById(f.table, "r01").get("order"));
                assertEquals(7, mapper.count(f.table));
            }
        }
    }

    @Test
    void systemReadServicePreservesWhitelistAndJdbcPaginationBindings() throws Exception {
        try (var f = new Fixture()) {
            var definitions = mock(EntityDefinitionMapper.class);
            var fields = mock(EntityFieldMapper.class);
            var definition = new EntityDefinition();
            definition.setId("catalog-user"); definition.setEntityCode("sys_user");
            definition.setPhysicalTableName("sys_user"); definition.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
            when(definitions.findByEntityCode("sys_user")).thenReturn(Optional.of(definition));
            when(fields.findByEntityId("catalog-user")).thenReturn(
                    List.of("id", "username", "nickname", "deleted", "create_time", "password", "token_version")
                            .stream().map(column -> {
                                var field = new EntityField(); field.setFieldCode(column); field.setDbColumnName(column); return field;
                            }).toList());
            var service = new SystemEntityReadService(f.systemReadJdbc(), definitions, fields, new SystemEntityFieldPolicy(),
                    com.workflow.integration.database.api.query.DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
            var page = service.findPage("sys_user", Map.of("username", List.of("user1", "user3", "user4", "user5")),
                    2, 2, "nickname", "DESC");
            assertEquals(4, page.getTotal());
            assertEquals(List.of("r04", "r05"), page.getRecords().stream().map(row -> row.getId()).toList());
            assertFalse(page.getRecords().get(0).getData().containsKey("password"));
            assertFalse(page.getRecords().get(0).getData().containsKey("token_version"));
            assertEquals("r03", service.findById("sys_user", "r03").getId());
            assertThrows(com.workflow.core.error.ForbiddenException.class, () -> service.findById("sys_user", "r06"));
            assertEquals(7, service.findSelectorPage("sys_user", "同名", 1, 3).getTotal());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final InitializedDriverDataSource source = new InitializedDriverDataSource(System.getenv("FLOW_MYSQL_TEST_URL"),
                System.getenv("FLOW_MYSQL_TEST_USER"), System.getenv("FLOW_MYSQL_TEST_PASSWORD"), null,
                DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.MYSQL));
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final String table = "biz_query_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        final String multi = table + "_multi";
        final SqlSessionFactory sessions;

        Fixture() throws Exception {
            try {
                // MySQL 也保留元数据列标签的大小写；使用大写列名验证 SELECT * 的业务键归一化。
                jdbc.execute("CREATE TABLE " + table + " (ID VARCHAR(256) PRIMARY KEY, `ORDER` VARCHAR(200), project_id VARCHAR(64),"
                        + " username VARCHAR(64), nickname VARCHAR(64), password VARCHAR(64), token_version INT,"
                        + " owner_id VARCHAR(64), status VARCHAR(20), deleted INT DEFAULT 0, create_time DATETIME, update_time DATETIME,"
                        + " current_task_id VARCHAR(64), current_task_name VARCHAR(64), current_task_assignee VARCHAR(64))"
                        + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
                jdbc.execute("CREATE TABLE " + multi + " (id VARCHAR(64) PRIMARY KEY, record_id VARCHAR(256), field_code VARCHAR(64),"
                        + " target_entity_id VARCHAR(64), target_record_id VARCHAR(64), sort_order INT, deleted INT DEFAULT 0)"
                        + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
                for (int i = 1; i <= 8; i++) {
                    jdbc.update("INSERT INTO " + table + " (id,`order`,project_id,owner_id,status,deleted,create_time) VALUES (?,?,?,?,?,?,?)",
                            "r0" + i, i == 4 || i == 5 ? "needle" : "plain", "p" + i % 2,
                            i == 2 || i == 7 ? "b" : "a", i == 3 || i == 7 ? "CLOSED" : "OPEN", i == 6 ? 1 : 0,
                            LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(i == 5 ? 4 : i));
                    jdbc.update("UPDATE " + table + " SET username=?,nickname='同名',password='hidden',token_version=7 WHERE id=?", "user" + i, "r0" + i);
                }
                for (int i = 1; i <= 4; i++) {
                    jdbc.update("INSERT INTO " + multi + " (id,record_id,field_code,target_entity_id,target_record_id,sort_order,deleted)"
                                    + " VALUES (?,?,'owners','person',?,0,?)", "m0" + i, i <= 2 ? "r01" : "r03", i == 2 ? "other" : "target", i == 4 ? 1 : 0);
                }
                var config = new Configuration(new Environment("mysql-query", new JdbcTransactionFactory(), source));
                config.setDatabaseId(new DatabaseMybatisConfiguration().databaseIdProvider(new MySqlSchemaDdlDialect()).getDatabaseId(source));
                config.addInterceptor(new DatabaseMybatisConfiguration().mybatisPlusInterceptor(new MySqlSchemaDdlDialect()));
                config.addMapper(EntityDataDynamicMapper.class);
                config.addMapper(EntityRelationProjectionMapper.class);
                sessions = new SqlSessionFactoryBuilder().build(config);
            } catch (Exception error) {
                close(); throw error;
            }
        }

        /** 只替换固定系统表名至随机测试表；被测服务仍执行完整的目录和字段安全检查。 */
        JdbcTemplate systemReadJdbc() {
            return new JdbcTemplate(new DelegatingDataSource(source) {
                @Override public Connection getConnection() throws SQLException {
                    var actual = super.getConnection();
                    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql) {
                                    args[0] = sql.replace("`sys_user`", "`" + table + "`");
                                }
                                try { return method.invoke(actual, args); }
                                catch (InvocationTargetException error) { throw error.getCause(); }
                            });
                }
            });
        }

        @Override public void close() {
            try { jdbc.execute("DROP TABLE IF EXISTS " + multi); }
            finally { jdbc.execute("DROP TABLE IF EXISTS " + table); }
        }
    }
}
