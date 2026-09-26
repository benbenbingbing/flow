package com.workflow.entity.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.core.database.jdbc.JdbcIdempotentInsert;
import com.workflow.core.database.jdbc.JdbcLockedRow;
import com.workflow.embed.management.infrastructure.persistence.EmbedOperationsMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import static com.workflow.entity.data.MySqlNumericBooleanDatabaseTest.BindingHarness;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.ReflectionTestUtils.getField;

/** 真实数据库与 setter 观察同时验证 NULL 绑定，避免仅因 MySQL 容忍 OTHER 得到假通过。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlNullBindingDatabaseTest {
    private static final List<String> COLUMNS = List.of("text_value", "number_value", "day_value", "instant_value", "flag", "payload");

    @Test void mapNullsUseStandardNullWhileExplicitJdbcTypesRemainAuthoritative() throws Exception {
        try (var f = fixture()) {
            var h = new BindingHarness(f, ExecutorType.SIMPLE, NullMapper.class); var mapper = h.session.getMapper(NullMapper.class);
            var values = nullValues("nulls"); assertEquals(1, mapper.insertValues(values));
            assertEquals(Collections.nCopies(6, Types.NULL), h.audit.nullTypes());
            assertNullRow(h, "nulls");
            h.audit.clear(); assertEquals(1, mapper.updateTyped(values));
            assertEquals(List.of(Types.VARCHAR, Types.INTEGER, Types.DATE, Types.TIMESTAMP, Types.INTEGER, Types.VARBINARY), h.audit.nullTypes());
            assertNullRow(h, "nulls");
            assertEquals(1, mapper.insertValues(populated("filled")));
            var loaded = mapper.selectById("filled");
            assertEquals("中文'\\value", loaded.getTextValue()); assertEquals(12, loaded.getNumberValue());
            assertEquals(LocalDate.of(2026, 9, 23), loaded.getDayValue()); assertTrue(loaded.getFlag());
            assertEquals(LocalDateTime.of(2026, 9, 23, 1, 2, 3, 456000000), loaded.getInstantValue());
            assertArrayEquals(new byte[]{0, 1, -1}, loaded.getPayload());
        }
    }

    @Test void wrapperAndBatchNullUpdatesClearAllScalarKindsAndRollbackTogether() throws Exception {
        try (var f = fixture()) {
            var setup = new BindingHarness(f, ExecutorType.SIMPLE, NullMapper.class);
            setup.session.getMapper(NullMapper.class).insertValues(populated("kept"));
            var h = new BindingHarness(f, ExecutorType.BATCH, NullMapper.class); var mapper = h.session.getMapper(NullMapper.class);
            h.tx.executeWithoutResult(status -> {
                var update = new UpdateWrapper<NullRow>(); COLUMNS.forEach(column -> update.set(column, null)); update.eq("id", "kept");
                mapper.update(null, update); mapper.insertValues(nullValues("rolled-back")); h.session.flushStatements();
                assertNullRow(h, "kept"); assertNullRow(h, "rolled-back");
                assertEquals(Collections.nCopies(12, Types.NULL), h.audit.nullTypes()); status.setRollbackOnly();
            });
            assertEquals("中文'\\value", setup.session.getMapper(NullMapper.class).selectById("kept").getTextValue());
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM biz_null_values", Integer.class));
        }
    }

    @Test void dynamicFieldClearingAndCurrentTaskNullsRetainDifferentWriteSemantics() throws Exception {
        try (var f = fixture()) {
            var h = new BindingHarness(f, ExecutorType.SIMPLE, NullMapper.class, EntityDataDynamicMapper.class);
            h.session.getMapper(NullMapper.class).insertValues(populated("record"));
            h.jdbc.update("UPDATE biz_null_values SET current_task_id='task',current_task_name='name',current_task_assignee='user' WHERE id='record'");
            var mapper = h.session.getMapper(EntityDataDynamicMapper.class); h.audit.clear();
            assertEquals(1, mapper.updateCurrentTask("biz_null_values", "record", null, null, null));
            assertEquals(Collections.nCopies(3, Types.VARCHAR), h.audit.nullTypes());
            var row = h.jdbc.queryForMap("SELECT current_task_id,current_task_name,current_task_assignee FROM biz_null_values WHERE id='record'");
            assertTrue(row.values().stream().allMatch(Objects::isNull));
            h.audit.clear();
            // 动态更新已有 NULL 字面量路径，保留它；插入仍省略未提供值，不能改成覆盖默认值。
            assertEquals(1, mapper.update("biz_null_values", nullValues("record"))); assertTrue(h.audit.nullTypes().isEmpty());
            assertNullRow(h, "record");
        }
    }

    @Test void jdbcNullWritesKeepIdempotencyLockedInitializationAndOuterRollback() throws Exception {
        try (var f = fixture()) {
            var h = new BindingHarness(f, ExecutorType.SIMPLE, NullMapper.class);
            var inserts = new JdbcIdempotentInsert(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL));
            var locks = new JdbcLockedRow(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL));
            assertTrue(inserts.insertIfAbsent("biz_null_values", nullValues("kept"))); assertNullRow(h, "kept");
            assertFalse(inserts.insertIfAbsent("biz_null_values", populated("kept"))); assertNullRow(h, "kept");
            h.tx.executeWithoutResult(status -> {
                locks.ensureAndLock("biz_null_values", nullValues("new"), List.of("id")); assertNullRow(h, "new");
                assertTrue(inserts.insertIfAbsent("biz_null_values", nullValues("after"))); status.setRollbackOnly();
            });
            assertEquals(List.of("kept"), h.jdbc.queryForList("SELECT id FROM biz_null_values ORDER BY id", String.class));
            assertFalse(h.audit.nullTypes().isEmpty()); assertFalse(h.audit.nullTypes().contains(Types.OTHER));
        }
    }

    @Test void nullableElementParentAndCursorParametersKeepTheirOriginalScopes() throws Exception {
        try (var f = new Fixture()) {
            f.table("process_action", "id VARCHAR(64),process_config_id VARCHAR(64),version_id VARCHAR(64),scope_type VARCHAR(32),element_id VARCHAR(64),trigger_timing VARCHAR(32),sort_order INT,status VARCHAR(32),deleted INT,execution_mode VARCHAR(32),failure_policy VARCHAR(32),retry_config TEXT,action_definition_id VARCHAR(64),action_name VARCHAR(128),description TEXT,interface_name VARCHAR(128),params_json TEXT,enabled INT,create_time DATETIME,update_time DATETIME,created_by VARCHAR(64)");
            f.table("entity_form_node", "id VARCHAR(64),form_id VARCHAR(64),parent_id VARCHAR(64),deleted INT,order_key BIGINT,create_time DATETIME,node_key VARCHAR(64),node_type VARCHAR(32),binding_type VARCHAR(32),binding_ref VARCHAR(64),component_name VARCHAR(64),component_version INT,snapshot_version INT,props_document TEXT,rules_document TEXT,data_source_bindings_document TEXT,legacy_props_document TEXT,revision INT,template_id VARCHAR(64),template_version INT,local_overrides_document TEXT,update_time DATETIME");
            f.table("embed_session", "id VARCHAR(64),view_id VARCHAR(64),application_id VARCHAR(64),status VARCHAR(32),slot_released INT");
            var h = new BindingHarness(f, ExecutorType.SIMPLE, FlowActionMapper.class, EntityFormNodeMapper.class, EmbedOperationsMapper.class);
            h.jdbc.update("INSERT INTO process_action(id,process_config_id,version_id,scope_type,element_id,trigger_timing,sort_order,status,deleted) VALUES ('draft','p','v','PROCESS',NULL,'BEFORE',0,'DRAFT',0),('node','p','v','PROCESS','node','BEFORE',1,'DRAFT',0),('published','p','v','PROCESS',NULL,'BEFORE',2,'PUBLISHED',0),('deleted','p','v','PROCESS',NULL,'BEFORE',3,'DRAFT',1)");
            h.jdbc.update("INSERT INTO entity_form_node(id,form_id,parent_id,deleted,order_key,create_time) VALUES ('root','f',NULL,0,0,NULL),('child','f','root',0,1,NULL),('deleted','f',NULL,1,2,NULL),('other','other',NULL,0,0,NULL)");
            h.jdbc.update("INSERT INTO embed_session VALUES ('a','view','app','ACTIVE',0),('b','view','app','ACTIVE',0),('released','view','app','ACTIVE',1),('revoked','view','app','REVOKED',0),('other','other','other','ACTIVE',0)");
            var actions = h.session.getMapper(FlowActionMapper.class);
            assertEquals(List.of("draft"), actions.findDraftActionsByBinding("p", "PROCESS", null).stream().map(row -> row.getId()).toList());
            assertEquals(List.of("published"), actions.findPublishedActionsByBinding("v", "PROCESS", null, "BEFORE").stream().map(row -> row.getId()).toList());
            assertEquals(List.of("node"), actions.findDraftActionsByBinding("p", "PROCESS", "node").stream().map(row -> row.getId()).toList());
            // Wrapper 的 isNull 直接表达流程级绑定，不再需要发送 NULL 参数。
            assertTrue(h.audit.nullTypes().isEmpty());
            var nodes = h.session.getMapper(EntityFormNodeMapper.class);
            assertEquals(List.of("root"), nodes.findSiblings("f", null).stream().map(row -> row.getId()).toList());
            assertEquals(List.of("child"), nodes.findSiblings("f", "root").stream().map(row -> row.getId()).toList());
            assertEquals(Collections.nCopies(2, Types.VARCHAR), h.audit.nullTypes());
            h.audit.clear();
            var operations = h.session.getMapper(EmbedOperationsMapper.class);
            assertEquals(List.of("a", "b"), operations.findActiveSessionIdsByView("view", null, 10));
            assertEquals(List.of("b"), operations.findActiveSessionIdsByView("view", "a", 10));
            assertEquals(List.of("a", "b"), operations.findActiveSessionIdsByApplication("app", null, 10));
            assertEquals(List.of("b"), operations.findActiveSessionIdsByApplication("app", "a", 10));
            assertEquals(Collections.nCopies(4, Types.VARCHAR), h.audit.nullTypes());
        }
    }

    @Test void optionalProviderIssuerKeepsLookupAndLockOnTheSameLogicalKey() throws Exception {
        try (var f = new Fixture()) {
            f.table("embed_identity_provider", "id VARCHAR(64) PRIMARY KEY,name VARCHAR(128),type VARCHAR(32),status VARCHAR(32),issuer VARCHAR(500),subject_namespace VARCHAR(128),audiences_json TEXT,algorithms_json TEXT,jwks_mode VARCHAR(32),jwks_json TEXT,jwks_url VARCHAR(500),clock_skew_seconds INT,max_assertion_lifetime_seconds INT,key_version BIGINT,lock_version BIGINT,security_version BIGINT,create_by VARCHAR(64),create_time DATETIME,update_by VARCHAR(64),update_time DATETIME,revoked_by VARCHAR(64),revoked_at DATETIME");
            // 调用包内 Mapper 的真实入口，覆盖默认方法对 Page 的委托及实际结果映射。
            String mapper = "com.workflow.embed.management.infrastructure.persistence.EmbedManagementMapper";
            var h = new BindingHarness(f, ExecutorType.SIMPLE, Class.forName(mapper));
            h.jdbc.update("INSERT INTO embed_identity_provider(id,type,issuer,subject_namespace) VALUES ('trusted','TRUSTED_EXTERNAL_ID',NULL,'n'),('jwt','SIGNED_JWT','issuer','n'),('other','TRUSTED_EXTERNAL_ID',NULL,'other')");
            h.jdbc.update("UPDATE embed_identity_provider SET clock_skew_seconds=30,max_assertion_lifetime_seconds=60,key_version=1,lock_version=1,security_version=1");
            var params = new HashMap<String, Object>(); params.put("issuer", null); params.put("namespace", "n");
            assertEquals("trusted", getField(MapperMethodCalls.<Object>call(h.session, mapper + ".findProviderByIssuerAndNamespace", params), "id"));
            h.tx.executeWithoutResult(status -> assertEquals("trusted",
                    getField(MapperMethodCalls.<Object>call(h.session, mapper + ".lockProviderByIssuerAndNamespace", params), "id")));
            assertEquals("jwt", getField(MapperMethodCalls.<Object>call(h.session, mapper + ".findProviderByIssuerAndNamespace",
                    Map.of("issuer", "issuer", "namespace", "n")), "id"));
            assertNull(MapperMethodCalls.call(h.session, mapper + ".findProviderByIssuerAndNamespace", Map.of("issuer", "missing", "namespace", "n")));
            assertEquals(Collections.nCopies(4, Types.VARCHAR), h.audit.nullTypes());
        }
    }

    @Test void openEndedAssignmentIntervalsBindDatesAndKeepTouchingBoundariesSeparate() throws Exception {
        try (var f = new Fixture()) {
            // BaseMapper 按实体选择全部列，测试表显式包含完整任职事实字段。
            f.table("sys_position_assignment", "id VARCHAR(64),position_id VARCHAR(64),organization_unit_id VARCHAR(64),effective_from DATETIME(6),effective_to DATETIME(6),revoked_at DATETIME,user_id VARCHAR(64),is_primary INT,sort_order INT,revoked_by VARCHAR(64),revoke_reason VARCHAR(200),revision INT,created_by VARCHAR(64),updated_by VARCHAR(64),create_time DATETIME,update_time DATETIME");
            var h = new BindingHarness(f, ExecutorType.SIMPLE, SysPositionAssignmentMapper.class);
            var capture = new PreparedSqlCapture();
            h.session.getConfiguration().addInterceptor(capture);
            var mapper = h.session.getMapper(SysPositionAssignmentMapper.class);
            h.jdbc.update("INSERT INTO sys_position_assignment(id,position_id,organization_unit_id,effective_from,effective_to,revoked_at) VALUES ('early','p','u','2026-01-01','2026-02-01',NULL),('open','p','u','2026-02-01',NULL,NULL),('revoked','p','u','2026-01-01',NULL,'2026-01-02'),('other','p','other','2026-01-01',NULL,NULL)");
            var jan = LocalDateTime.of(2026, 1, 10, 0, 0); var feb = LocalDateTime.of(2026, 2, 1, 0, 0);
            assertEquals(List.of("early"), mapper.selectOverlaps("p", "u", jan, feb, null).stream().map(row -> row.getId()).toList());
            assertEquals(List.of("open"), mapper.selectOverlaps("p", "u", feb, null, null).stream().map(row -> row.getId()).toList());
            var openIntervalSql = capture.last;
            assertEquals(List.of("early", "open"), mapper.selectOverlaps("p", "u", feb.minusNanos(1000), feb.plusNanos(1000), null).stream().map(row -> row.getId()).toList());
            assertTrue(mapper.selectOverlaps("p", "u", feb, null, "open").isEmpty());
            assertTrue(mapper.selectOverlaps("p", "u", LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999999000), null, null).isEmpty());
            assertTrue(mapper.selectOverlaps("p", "u", null, null, null).isEmpty());
            assertEquals(List.of(Types.TIMESTAMP), h.audit.nullTypes());
            assertNotNull(openIntervalSql, "必须捕获实际执行的 Wrapper SQL，不能只读取已删除的旧映射");
            assertFalse(openIntervalSql.getSql().contains("9999-12-31"));
            assertFalse(openIntervalSql.getSql().contains("COALESCE"));
            var timestampMappings = openIntervalSql.getParameterMappings().stream()
                    .filter(mapping -> mapping.getJdbcType() == JdbcType.TIMESTAMP).toList();
            assertEquals(2, timestampMappings.size(), "区间两端都必须按 TIMESTAMP 绑定");
            var parameters = h.session.getConfiguration().newMetaObject(openIntervalSql.getParameterObject());
            assertEquals(List.of(LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999999000), feb),
                    timestampMappings.stream().map(mapping -> parameters.getValue(mapping.getProperty())).toList());
        }
    }

    @Test void singleHolderValidationCountsOnlyOverlappingPairsInTheSameOrganization() throws Exception {
        try (var f = new Fixture()) {
            f.table("sys_position_assignment", "id VARCHAR(64),position_id VARCHAR(64),organization_unit_id VARCHAR(64),effective_from DATETIME(6),effective_to DATETIME(6),revoked_at DATETIME");
            f.table("sys_organization", "id VARCHAR(64),type VARCHAR(32),deleted INT");
            var h = new BindingHarness(f, ExecutorType.SIMPLE, SysPositionMapper.class); var mapper = h.session.getMapper(SysPositionMapper.class);
            h.jdbc.update("INSERT INTO sys_organization VALUES ('u','DEPT',0),('other','COMPANY',0),('deleted','COMPANY',1)");
            h.jdbc.update("INSERT INTO sys_position_assignment VALUES ('a','p','u','2026-01-01','2026-02-01',NULL),('b','p','u','2026-02-01',NULL,NULL),('revoked','p','u','2026-01-01',NULL,'2026-01-02'),('other','p','other','2026-01-01',NULL,NULL),('deleted-unit','p','deleted','2026-01-01',NULL,NULL),('other-position','q','u','2026-01-01',NULL,NULL)");
            assertEquals(0, mapper.countOverlapsPreventingSingleMode("p"));
            h.jdbc.update("INSERT INTO sys_position_assignment VALUES ('c','p','u','2026-01-15','2026-01-20',NULL),('d','p','u','2026-02-15','2026-03-01',NULL),('e','p','u','2026-03-01',NULL,NULL),('maximum','p','u','9999-12-31 23:59:59.999999',NULL,NULL)");
            assertEquals(3, mapper.countOverlapsPreventingSingleMode("p"));
            assertEquals(0, mapper.countOverlapsPreventingSingleMode("q"));
            // 类型切换检查保留现有组织删除过滤，不把 XML 实体文本发送给 JDBC。
            assertEquals(1, mapper.countAssignmentsOutsideUnitType("p", "DEPT"));
            assertEquals(0, mapper.countAssignmentsOutsideUnitType("q", "DEPT"));
        }
    }

    /** 在 JDBC prepare 前观察实际 SQL；不替换执行器、SQL 或参数。 */
    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(
            type = org.apache.ibatis.executor.statement.StatementHandler.class,
            method = "prepare", args = {java.sql.Connection.class, Integer.class}))
    static final class PreparedSqlCapture implements org.apache.ibatis.plugin.Interceptor {
        org.apache.ibatis.mapping.BoundSql last;

        @Override
        public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            last = ((org.apache.ibatis.executor.statement.StatementHandler) invocation.getTarget()).getBoundSql();
            return invocation.proceed();
        }
    }

    private static Fixture fixture() {
        var f = new Fixture(); f.table("biz_null_values", "id VARCHAR(64) PRIMARY KEY,text_value VARCHAR(200),number_value INT,day_value DATE,instant_value DATETIME(6),flag SMALLINT,payload BLOB,current_task_id VARCHAR(64),current_task_name VARCHAR(100),current_task_assignee VARCHAR(64),update_time DATETIME"); return f;
    }
    private static Map<String, Object> nullValues(String id) {
        var values = new LinkedHashMap<String, Object>(); values.put("id", id); COLUMNS.forEach(column -> values.put(column, null)); return values;
    }
    private static Map<String, Object> populated(String id) {
        var values = nullValues(id); values.put("text_value", "中文'\\value"); values.put("number_value", 12);
        values.put("day_value", LocalDate.of(2026, 9, 23)); values.put("instant_value", LocalDateTime.of(2026, 9, 23, 1, 2, 3, 456000000));
        values.put("flag", true); values.put("payload", new byte[]{0, 1, -1}); return values;
    }
    private static void assertNullRow(BindingHarness h, String id) {
        var row = h.jdbc.queryForMap("SELECT text_value,number_value,day_value,instant_value,flag,payload FROM biz_null_values WHERE id=?", id);
        assertEquals(6, row.size()); assertTrue(row.values().stream().allMatch(Objects::isNull));
    }
    @Data @TableName("biz_null_values")
    public static class NullRow {
        @TableId(type = IdType.INPUT) private String id;
        private String textValue; private Integer numberValue; private LocalDate dayValue;
        private LocalDateTime instantValue; private Boolean flag; private byte[] payload;
    }
    public interface NullMapper extends BaseMapper<NullRow> {
        @Insert("INSERT INTO biz_null_values(id,text_value,number_value,day_value,instant_value,flag,payload) VALUES (#{values.id},#{values.text_value},#{values.number_value},#{values.day_value},#{values.instant_value},#{values.flag},#{values.payload})")
        int insertValues(@Param("values") Map<String, Object> values);
        @Update("UPDATE biz_null_values SET text_value=#{text_value,jdbcType=VARCHAR},number_value=#{number_value,jdbcType=INTEGER},day_value=#{day_value,jdbcType=DATE},instant_value=#{instant_value,jdbcType=TIMESTAMP},flag=#{flag,jdbcType=INTEGER},payload=#{payload,jdbcType=VARBINARY} WHERE id=#{id}")
        int updateTyped(Map<String, Object> values);
    }
}
