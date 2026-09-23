package com.workflow.entity.data;

import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.integration.database.api.query.DatabaseQuerySql;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.lang.reflect.Proxy;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.currentTable;
import static org.junit.jupiter.api.Assertions.*;

/** 与原 MySQL 表达式对照，并验证真实 Mapper 绑定、索引锁及资源关联；只使用随机测试表。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlIdentifierConversionDatabaseTest {
    private static final String EMBED = "com.workflow.embed.management.infrastructure.persistence.EmbedManagementMapper";
    private static final String LEGACY_REVISION = """
            SELECT CONCAT(p.revision, ':', DATE_FORMAT(o.update_time, '%Y%m%d%H%i%s.%f'), ':',
              COUNT(a.id), ':', COALESCE(MAX(a.revision), 0), ':',
              COALESCE(DATE_FORMAT(MAX(a.update_time), '%Y%m%d%H%i%s.%f'), '0'))
            FROM sys_position p JOIN sys_organization o ON o.id = ?
            LEFT JOIN sys_position_assignment a ON a.position_id=p.id AND a.organization_unit_id=o.id
            WHERE p.id = ? GROUP BY p.revision, o.update_time
            """;

    @Test void directoryTokenMatchesLegacyAggregationForFractionsScopesAndMissingFacts() {
        try (var f = new Fixture()) {
            revisionTables(f);
            var h = new Harness(f, SysPositionAssignmentMapper.class);
            var mapper = h.mapper(SysPositionAssignmentMapper.class);
            h.jdbc.update("INSERT INTO sys_position VALUES ('p',3),('other',99),('null-revision',NULL)");
            h.jdbc.update("INSERT INTO sys_organization VALUES ('o','2026-09-22 01:02:03.000004'),('other','2026-01-01'),('null-time',NULL)");
            assertEquals("3:20260922010203.000004:0:0:0", mapper.selectDirectoryRevision("p", "o"));
            assertRevisionMatches(h, "p", "o");
            h.jdbc.update("INSERT INTO sys_position_assignment VALUES "
                    + "('a','p','o',4,'2026-09-22 02:03:04.123456'),('b','p','o',7,'2026-09-22 02:03:04.100000'),"
                    + "('c','p','o',NULL,NULL),('d','other','o',99,'2099-01-01'),('e','p','other',99,'2099-01-01')");
            assertEquals("3:20260922010203.000004:3:7:20260922020304.123456", mapper.selectDirectoryRevision("p", "o"));
            for (String p : List.of("p", "other", "null-revision")) {
                for (String o : List.of("o", "other", "null-time")) assertRevisionMatches(h, p, o);
            }
            assertNull(mapper.selectDirectoryRevision("absent", "o"));
            assertNull(mapper.selectDirectoryRevision("p", "absent"));
            assertNull(mapper.selectDirectoryRevision("p' OR 1=1 --", "o"));
        }
    }

    @Test void directoryTokenChangesForEachVersionFactAndRollsBackWithItsTransaction() {
        try (var f = new Fixture()) {
            revisionTables(f);
            var h = new Harness(f, SysPositionAssignmentMapper.class);
            var mapper = h.mapper(SysPositionAssignmentMapper.class);
            h.jdbc.update("INSERT INTO sys_position VALUES ('p',1)");
            h.jdbc.update("INSERT INTO sys_organization VALUES ('o','2026-09-22 00:00:00')");
            String initial = mapper.selectDirectoryRevision("p", "o");
            h.tx.executeWithoutResult(status -> {
                String previous = initial;
                for (String update : List.of("UPDATE sys_position SET revision=2",
                        "UPDATE sys_organization SET update_time='2026-09-22 00:00:00.000001'",
                        "INSERT INTO sys_position_assignment VALUES ('a','p','o',1,'2026-09-22')",
                        "UPDATE sys_position_assignment SET revision=2",
                        "UPDATE sys_position_assignment SET update_time='2026-09-22 00:00:00.999999'")) {
                    h.jdbc.update(update);
                    // 测试直接 JDBC 写入，因此清理 MyBatis 一级缓存后比较真实聚合结果。
                    h.session.clearCache();
                    String current = mapper.selectDirectoryRevision("p", "o");
                    assertNotEquals(previous, current); assertRevisionMatches(h, "p", "o");
                    previous = current;
                }
                status.setRollbackOnly();
            });
            assertEquals(initial, mapper.selectDirectoryRevision("p", "o"));
        }
    }

    @Test void processBindingUsesActualGeneratedIndexAndLongParametersWithoutPermissiveCasts() throws Exception {
        try (var f = new Fixture()) {
            var h = bindingHarness(f);
            var bindingAudit = new ProcessBindingAudit();
            h.session.getConfiguration().addInterceptor(bindingAudit);
            var mapper = h.mapper(EntityDefinitionMapper.class);
            h.jdbc.update("INSERT INTO entity_definition(id,entity_code,entity_name,process_definition_id,deleted) VALUES "
                    + "(1,'a','a','12',0),(2,'b','b',' 0012 ',0),(3,'c','c','12',1),"
                    + "(4,'d','d','9007199254740993',0),(5,'e','e','9223372036854775807',0),"
                    + "(6,'f','f','12abc',0),(7,'g','g','9223372036854775808',0),(8,'h','h','0',0)");
            for (String input : List.of("12", "00012", " 00012 ", "9007199254740993", "0009223372036854775807")) {
                var legacy = h.jdbc.queryForList("SELECT id FROM entity_definition WHERE active_process_definition_key="
                        + "CAST(COALESCE(NULLIF(TRIM(LEADING '0' FROM TRIM(?)),''),'0') AS UNSIGNED) ORDER BY id", String.class, input);
                assertEquals(legacy, mapper.findAllByProcessDefinitionId(input).stream().map(row -> row.getId()).toList());
                assertEquals(legacy, h.tx.execute(status -> mapper.findAllByProcessDefinitionIdForUpdate(input).stream().map(row -> row.getId()).toList()));
            }
            assertEquals(List.of("1", "2"), mapper.findAllByProcessDefinitionId("12").stream().map(row -> row.getId()).toList());
            for (String input : new String[]{null, "", "0", "-12", "+12", "12abc", "12.0", "1e1", "１２", "12' OR 1=1 --", "9223372036854775808"}) {
                assertTrue(mapper.findAllByProcessDefinitionId(input).isEmpty());
                assertNull(bindingAudit.value, "非法流程编号必须绑定 NULL，不能交给数据库宽松转换");
                assertEquals("setNull", bindingAudit.setter);
                Boolean lockedEmpty = h.tx.execute(status -> mapper.findAllByProcessDefinitionIdForUpdate(input).isEmpty());
                assertEquals(Boolean.TRUE, lockedEmpty);
            }
            // 默认方法最终执行框架的 selectList；检查实际 Wrapper 路径生成的绑定，
            // 不再假定业务默认方法本身仍有独立的 MyBatis MappedStatement。
            assertEquals(List.of("4"), mapper.findAllByProcessDefinitionId("0009007199254740993")
                    .stream().map(row -> row.getId()).toList());
            assertEquals(9007199254740993L, bindingAudit.value);
            assertEquals("setLong", bindingAudit.setter);
            assertFalse(bindingAudit.sql.toUpperCase(java.util.Locale.ROOT).contains("CAST"));
            assertEquals(List.of("5"), mapper.findAllByProcessDefinitionId("0009223372036854775807")
                    .stream().map(row -> row.getId()).toList());
            assertEquals(Long.MAX_VALUE, bindingAudit.value);
            assertEquals("setLong", bindingAudit.setter);
        }
    }

    @Test void numericAliasCurrentReadLocksAllBindingsAndReleasesOnRollback() throws Exception {
        try (var f = new Fixture()) {
            var h = bindingHarness(f);
            h.jdbc.update("INSERT INTO entity_definition(id,entity_code,entity_name,process_definition_id) VALUES (1,'a','a','12'),(2,'b','b','0012'),(3,'c','c','34')");
            h.tx.executeWithoutResult(status -> {
                assertEquals(2, h.mapper(EntityDefinitionMapper.class).findAllByProcessDefinitionIdForUpdate(" 00012 ").size());
                // 第二个物理连接证明锁在真实索引命中的基表行上，而非仅验证 SQL 文本。
                try (var connection = f.isolatedDataSource().getConnection(); var statement = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    try {
                        statement.execute("SET SESSION innodb_lock_wait_timeout=1");
                        for (int id : new int[]{1, 2}) {
                            SQLException blocked = assertThrows(SQLException.class, () -> statement.executeUpdate(
                                    "UPDATE entity_definition SET entity_name='blocked' WHERE id=" + id));
                            assertEquals(1205, blocked.getErrorCode());
                        }
                        assertEquals(1, statement.executeUpdate("UPDATE entity_definition SET entity_name='other' WHERE id=3"));
                    } finally { connection.rollback(); }
                } catch (SQLException error) { throw new IllegalStateException(error); }
                status.setRollbackOnly();
            });
            assertEquals(2, h.jdbc.update("UPDATE entity_definition SET entity_name='released' WHERE id IN(1,2)"));
        }
    }

    @Test void integerTextExpressionPreservesAllSignedBigintDigitsAndNull() {
        try (var f = new Fixture()) {
            f.table("integer_identifier", "id BIGINT");
            var h = new Harness(f);
            for (Long id : new Long[]{Long.MIN_VALUE, -1L, 0L, 9007199254740993L, Long.MAX_VALUE, null}) {
                h.jdbc.update("DELETE FROM integer_identifier");
                h.jdbc.update("INSERT INTO integer_identifier VALUES (?)", id);
                String expression = DatabaseQuerySql.integerIdentifierText("MYSQL", "e.id");
                assertEquals(id == null ? null : id.toString(), h.jdbc.queryForObject("SELECT " + expression + " FROM integer_identifier e", String.class));
            }
        }
    }

    @Test void embedListAndFormDoNotCoerceMalformedOrRoundedTextIdentifiers() throws Exception {
        try (var f = new Fixture()) {
            var h = embedHarness(f);
            for (long id : new long[]{1, 9007199254740993L, Long.MAX_VALUE}) {
                h.jdbc.update("DELETE FROM entity_definition");
                h.jdbc.update("INSERT INTO entity_definition VALUES (?,'asset',0,'PUBLISHED')", id);
                for (String foreignId : List.of(Long.toString(id), "0" + id, id + "junk", Long.toString(id - 1))) {
                    h.jdbc.update("UPDATE entity_list_config SET entity_id=?", foreignId);
                    h.jdbc.update("UPDATE entity_form SET entity_id=?", foreignId);
                    for (boolean form : new boolean[]{false, true}) {
                        Object row = target(h, form, "asset", null);
                        if (foreignId.equals(Long.toString(id))) {
                            assertNotNull(row);
                            assertEquals(Long.toString(id), h.json.valueToTree(row).path("entityId").asText());
                        } else assertNull(row, foreignId);
                    }
                }
            }
        }
    }

    @Test void embedTargetsKeepPublishedResourceOwnershipReleaseAndDeletionGuards() throws Exception {
        try (var f = new Fixture()) {
            var h = embedHarness(f);
            h.jdbc.update("INSERT INTO entity_definition VALUES (1,'asset',0,'PUBLISHED')");
            for (boolean form : new boolean[]{false, true}) {
                assertNotNull(target(h, form, "asset", null));
                assertNotNull(target(h, form, "asset", form ? "rf" : "rl"));
                assertNull(target(h, form, "wrong-entity", null));
                assertNull(target(h, form, "asset' OR 1=1 --", null));
                assertNull(target(h, form, "asset", "absent"));
                assertNull(target(h, form, "asset", form ? "rl" : "rf"));
            }
            for (String update : List.of("UPDATE entity_definition SET deleted=1", "UPDATE entity_definition SET status='DRAFT'",
                    "UPDATE ui_config_release SET config_id='another-owner'", "UPDATE ui_config_release SET config_type='WRONG'")) {
                h.tx.executeWithoutResult(status -> {
                    h.jdbc.update(update);
                    assertNull(target(h, false, "asset", null)); assertNull(target(h, true, "asset", null));
                    status.setRollbackOnly();
                });
            }
            h.jdbc.update("UPDATE entity_list_config SET deleted=1");
            assertNull(target(h, false, "asset", null)); assertNotNull(target(h, true, "asset", null));
            h.jdbc.update("UPDATE entity_form SET status=0");
            assertNull(target(h, true, "asset", null));
            h.jdbc.update("UPDATE entity_form SET status=1,deleted=1");
            assertNull(target(h, true, "asset", null));
        }
    }

    /** 捕获真实 Mapper 的框架语句，并用同一 ParameterHandler 验证 JDBC 数值绑定。 */
    @Intercepts(@Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    static final class ProcessBindingAudit implements Interceptor {
        String sql;
        String setter;
        Object value;

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            if ((EntityDefinitionMapper.class.getName() + ".selectList").equals(statement.getId())) {
                Object parameters = invocation.getArgs()[1];
                var bound = statement.getBoundSql(parameters);
                sql = bound.getSql();
                setter = null;
                value = null;
                assertEquals(1, bound.getParameterMappings().size(), "活动绑定查询仅绑定规范化后的整数键");
                // 在记录型 PreparedStatement 上执行框架绑定器，确保 Object 参数仍选择 setLong，
                // 防止回归为字符串/浮点绑定后依赖 MySQL 隐式类型转换。
                PreparedStatement recorder = (PreparedStatement) Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().startsWith("set")) {
                                setter = method.getName();
                                value = "setNull".equals(setter) ? null : arguments[1];
                            }
                            return null;
                        });
                statement.getConfiguration().newParameterHandler(statement, parameters, bound).setParameters(recorder);
            }
            return invocation.proceed();
        }
    }

    private static void revisionTables(Fixture f) {
        f.table("sys_position", "id VARCHAR(64) PRIMARY KEY, revision BIGINT");
        f.table("sys_organization", "id VARCHAR(64) PRIMARY KEY, update_time DATETIME(6)");
        f.table("sys_position_assignment", "id VARCHAR(64) PRIMARY KEY, position_id VARCHAR(64), organization_unit_id VARCHAR(64), revision BIGINT, update_time DATETIME(6)");
    }

    private static void assertRevisionMatches(Harness h, String position, String organization) {
        assertEquals(h.jdbc.queryForObject(LEGACY_REVISION, String.class, organization, position),
                h.mapper(SysPositionAssignmentMapper.class).selectDirectoryRevision(position, organization));
    }

    /** 复制现有表定义和 V073 生成列到隔离表，不修改、重放生产迁移。 */
    private static Harness bindingHarness(Fixture f) throws Exception {
        currentTable(f, "entity_definition");
        var h = new Harness(f, EntityDefinitionMapper.class);
        h.jdbc.execute(Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration/V073__index_entity_workflow_binding.sql")));
        return h;
    }

    private static Harness embedHarness(Fixture f) throws Exception {
        f.table("entity_definition", "id BIGINT PRIMARY KEY, entity_code VARCHAR(100), deleted INT, status VARCHAR(30)");
        f.table("entity_list_config", "id VARCHAR(64) PRIMARY KEY, entity_id VARCHAR(64), entity_code VARCHAR(100), list_key VARCHAR(64), active_release_id VARCHAR(64), deleted INT");
        f.table("entity_form", "id VARCHAR(64) PRIMARY KEY, entity_id VARCHAR(64), active_release_id VARCHAR(64), deleted INT, status INT");
        f.table("ui_config_release", "id VARCHAR(64) PRIMARY KEY, config_type VARCHAR(30), config_id VARCHAR(64), version BIGINT, snapshot_document TEXT, content_hash VARCHAR(64)");
        var h = new Harness(f, Class.forName(EMBED));
        h.jdbc.update("INSERT INTO entity_list_config VALUES ('list','1','asset','main','rl',0)");
        h.jdbc.update("INSERT INTO entity_form VALUES ('form','1','rf',0,1)");
        h.jdbc.update("INSERT INTO ui_config_release VALUES ('rl','LIST','list',1,'{}','list-hash'),('rf','FORM','form',2,'{}','form-hash')");
        return h;
    }

    private static Object target(Harness h, boolean form, String entityCode, String releaseId) {
        var parameters = new HashMap<String, Object>();
        parameters.put("entityCode", entityCode); parameters.put("releaseId", releaseId);
        parameters.put("listKey", "main"); parameters.put("formId", "form");
        return MapperMethodCalls.call(h.session, EMBED + (form ? ".findFormTarget" : ".findListTarget"), parameters);
    }
}
