package com.workflow.entity.data;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.core.database.port.SchemaMetadataPort;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.data.application.SchemaDdlExecutor;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.permission.application.PermissionSqlBuilder;
import com.workflow.entity.permission.application.PermissionSqlFragmentCompiler;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.util.*;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 用户属性和团队身份经真实 MyBatis/JDBC 绑定；模拟业务元数据，SQL 在随机 MySQL 表执行。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlPermissionBindingDatabaseTest {
    private static final String TABLE = "biz_scope";
    private static final String SPECIAL = "李\\' OR 1=1 -- ${value} #{value} _%";

    @Test void controlledRecordSqlBindsIdentitiesAndQuotesReservedColumnsInBothSqlModes() {
        for (boolean noBackslash : List.of(false, true)) try (var f = fixture(new Fixture(noBackslash))) {
            var h = harness(f); var compiler = compiler(h); var mapper = h.mapper(EntityDataDynamicMapper.class);
            var user = user("u\\'1", SPECIAL);
            h.jdbc.update("INSERT INTO biz_scope(id,`order`,create_by,deleted) VALUES ('allowed',?,?,0),('different','other','victim',0),('deleted',?,?,1)",
                    SPECIAL, user.getId(), SPECIAL, user.getId());
            var builder = builder(compiler, null); var filter = new FilterConfigDTO(); filter.setType("SQL");
            filter.setSql("biz.`order` = #{username} AND biz.create_by = #{userId}");
            h.tx.executeWithoutResult(status -> {
                        String mode = h.jdbc.queryForObject("SELECT @@SESSION.sql_mode", String.class);
                        assertEquals(noBackslash, mode.contains("NO_BACKSLASH_ESCAPES"));
                        assertEquals(SPECIAL, h.jdbc.queryForObject("SELECT ?", String.class, SPECIAL));
                        h.session.clearCache();
                        Map<String, Object> values = new LinkedHashMap<>(); values.put("permissionValue1", null);
                        String sql = builder.buildFilterSql("asset", filter, user, values);
                        assertFalse(sql.contains(SPECIAL)); assertFalse(sql.contains(user.getId()));
                        assertTrue(sql.contains("`biz_scope`.`order`"));
                        assertEquals(Set.of("allowed"), ids(mapper.selectPageWithPermission(TABLE, sql, values, 0, 10)));
                        assertEquals(1, mapper.countWithPermission(TABLE, sql, values));
                        assertTrue(values.containsKey("permissionValue1")); assertNull(values.get("permissionValue1"));
                        var arguments = new HashMap<String, Object>(); arguments.put("tableName", TABLE);
                        arguments.put("permissionSql", sql); arguments.put("permissionParameters", values);
                        var bound = h.session.getConfiguration().getMappedStatement(EntityDataDynamicMapper.class.getName() + ".countWithPermission").getBoundSql(arguments);
                        assertTrue(bound.getParameterMappings().stream().allMatch(value -> value.getJdbcType() == JdbcType.VARCHAR));
            });
        }
    }

    @Test void audienceUsesJdbcValuesAndMissingAttributesRemainNull() {
        try (var f = fixture()) {
            f.table("scope_audience", "user_id VARCHAR(300)"); var h = harness(f); var compiler = compiler(h);
            h.jdbc.update("INSERT INTO scope_audience VALUES (?)", SPECIAL);
            assertTrue(compiler.matchesUser("#{username} IN (SELECT user_id FROM scope_audience)", user("u", SPECIAL)));
            assertFalse(compiler.matchesUser("#{username} IN (SELECT user_id FROM scope_audience)", user("u", "missing' OR 1=1")));
            assertTrue(compiler.matchesUser("#{deptId} IS NULL AND #{userId} = 'u'", user("u", SPECIAL)));
            assertFalse(compiler.matchesUser("#{deptId} = ''", user("u", SPECIAL)));
            assertFalse(compiler.matchesUser("1=1", null));
            assertFalse(compiler.matchesUser("1 = (", user("u", SPECIAL)));
            h.jdbc.update("INSERT INTO biz_scope(id,dept_id,deleted) VALUES ('null',NULL,0),('empty','',0),('dept','d',0)");
            var values = new LinkedHashMap<String, Object>();
            String sql = compiler.compileRecordSql("asset", "biz.dept_id = #{deptId}", user("u", SPECIAL), values);
            assertEquals(0, h.mapper(EntityDataDynamicMapper.class).countWithPermission(TABLE, sql, values));
            assertEquals(1, values.size()); assertNull(values.get("permissionValue0"));
        }
    }

    @Test void teamAndControlledRulesShareBindingsWithoutChangingAllowDenyResults() {
        try (var f = fixture()) {
            f.table("biz_scope_team", "record_id VARCHAR(64),user_id VARCHAR(300)");
            var h = harness(f); var resolver = resolver(); var metadata = mock(SchemaMetadataPort.class);
            when(metadata.tableExists("biz_scope_team")).thenReturn(true);
            var teams = new EntityRecordTeamService(h.jdbc, resolver, mock(EntityPublishedSnapshotService.class),
                    mock(SchemaDdlExecutor.class), DatabaseDialects.forVendor(DatabaseVendor.MYSQL), metadata);
            var builder = builder(compiler(h), teams); var mapper = h.mapper(EntityDataDynamicMapper.class);
            h.jdbc.update("INSERT INTO biz_scope(id,`order`,create_by,deleted) VALUES ('id-match','keep','x',0),('name-match','keep','x',0),('denied','keep','deny',0),('other','keep','x',0),('deleted','keep','x',1)");
            h.jdbc.update("INSERT INTO biz_scope_team VALUES ('id-match',?),('name-match',?),('denied',?),('deleted',?),('other','unrelated')",
                    "u'1", SPECIAL, "u'1", "u'1");
            var team = new FilterConfigDTO(); team.setType("TEAM");
            var controlled = new FilterConfigDTO(); controlled.setType("SQL"); controlled.setSql("biz.create_by = #{userId}");
            var values = new LinkedHashMap<String, Object>();
            String allowed = builder.buildFilterSql("asset", team, user("u'1", SPECIAL), values);
            String denied = builder.buildFilterSql("asset", controlled, user("deny", "denier"), values);
            assertFalse(allowed.contains(SPECIAL)); assertFalse(allowed.contains("u'1"));
            assertEquals(3, values.size());
            String combined = "(" + allowed + ") AND NOT (" + denied + ")";
            assertEquals(Set.of("id-match", "name-match"), ids(mapper.selectPageWithPermission(TABLE, combined, values, 0, 10)));
            assertEquals(2, mapper.countWithPermission(TABLE, combined, values));
            assertEquals("1=0", builder.buildFilterSql("asset", team, user(null, null), new LinkedHashMap<>()));
            when(metadata.tableExists("biz_scope_team")).thenReturn(false);
            assertEquals("1=0", builder.buildFilterSql("asset", team, user("u'1", SPECIAL), new LinkedHashMap<>()));
        }
    }

    @Test void aliasRewritePreservesLiteralTextAndRejectsQuotedOrUnboundPlaceholders() {
        try (var f = fixture()) {
            var h = harness(f); var compiler = compiler(h); var mapper = h.mapper(EntityDataDynamicMapper.class);
            h.jdbc.update("INSERT INTO biz_scope(id,`order`,deleted) VALUES ('literal','biz.order',0),('other','other',0)");
            for (String column : List.of("biz.order", "`biz`.`order`", "\"biz\".\"order\"")) {
                var values = new LinkedHashMap<String, Object>();
                String sql = compiler.compileRecordSql("asset", column + " = 'biz.order'", user("u", SPECIAL), values);
                assertTrue(sql.contains("= 'biz.order'")); assertTrue(values.isEmpty());
                assertEquals(1, mapper.countWithPermission(TABLE, sql, values));
            }
            for (String fragment : List.of("biz.id = '#{userId}'", "biz.id = \"#{userId}\"", "biz.id = `#{userId}`",
                    "biz.id = ${userId}", "biz.id = ?", "biz.id = 'unterminated", "biz.id = #{unknown}")) {
                assertThrows(IllegalArgumentException.class, () -> compiler.compileRecordSql("asset", fragment, user("u", SPECIAL), new LinkedHashMap<>()));
            }
            assertEquals(2, mapper.count(TABLE));
        }
    }

    private static Fixture fixture() { return fixture(new Fixture()); }
    private static Fixture fixture(Fixture f) {
        f.table(TABLE, "id VARCHAR(64) PRIMARY KEY,`order` VARCHAR(500),create_by VARCHAR(300),dept_id VARCHAR(64),deleted INT,create_time DATETIME DEFAULT CURRENT_TIMESTAMP"); return f;
    }
    private static Harness harness(Fixture f) { return new Harness(f, EntityDataDynamicMapper.class); }
    private static EntityPhysicalTableResolver resolver() {
        var resolver = mock(EntityPhysicalTableResolver.class); when(resolver.resolve("asset")).thenReturn(TABLE); return resolver;
    }
    private static PermissionSqlFragmentCompiler compiler(Harness h) {
        return new PermissionSqlFragmentCompiler(h.jdbc, resolver(), DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
    }
    private static PermissionSqlBuilder builder(PermissionSqlFragmentCompiler compiler, EntityRecordTeamService teams) {
        return new PermissionSqlBuilder(null, null, null, List.of(), teams, resolver(), compiler, DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
    }
    private static SysUser user(String id, String name) { var user = new SysUser(); user.setId(id); user.setUsername(name); return user; }
    private static Set<String> ids(List<Map<String, Object>> rows) { return new HashSet<>(rows.stream().map(row -> (String) row.get("id")).toList()); }
}
