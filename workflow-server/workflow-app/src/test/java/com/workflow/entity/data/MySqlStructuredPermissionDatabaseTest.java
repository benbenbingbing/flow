package com.workflow.entity.data;

import com.workflow.contracts.entity.permission.spi.EntityDataPermissionFilterProvider;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.contracts.entity.permission.model.EntityActionRule;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.permission.application.PermissionSqlBuilder;
import com.workflow.entity.permission.application.PermissionSqlParameters;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import static com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 随机隔离表验证结构化权限的真实绑定、匹配范围、精度和 SQL mode 边界。 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlStructuredPermissionDatabaseTest {
    private static final String TABLE = "biz_structured_scope";
    private static final String SPECIAL = "李\\' OR 1=1 -- !%_ ${value} #{value}";

    @Test void identityFiltersAndStatusLimitKeepValuesBoundUnderBothSqlModes() {
        for (boolean noBackslash : List.of(false, true)) try (var f = fixture(new Fixture(noBackslash))) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            h.jdbc.update("INSERT INTO biz_structured_scope(id,create_by,submitter_id,current_task_assignee,`order`,status,deleted) VALUES ('allowed',?,?,?,?,?,0),('deleted',?,?,?,?,?,1),('other','other','other','other','other','other',0)",
                    SPECIAL, SPECIAL, SPECIAL, SPECIAL, SPECIAL, SPECIAL, SPECIAL, SPECIAL, SPECIAL, SPECIAL);
            checkCurrentMode(h, () -> {
                for (String type : List.of("PERSONAL", "SUBMITTER", "CURRENT_ASSIGNEE")) {
                    var filter = filter(type); var limit = new FilterConfigDTO.StatusLimitDTO();
                    limit.setEnabled(true); limit.setValues(List.of(SPECIAL)); filter.setStatusLimit(limit);
                    var parameters = new LinkedHashMap<String, Object>();
                    String predicate = builder.buildFilterSql("asset", filter, user(SPECIAL, SPECIAL, null), parameters);
                    assertScope(h, predicate, parameters, "allowed");
                    assertEquals(List.of(SPECIAL, SPECIAL), new ArrayList<>(parameters.values()));
                    assertFalse(predicate.contains(SPECIAL));
                    assertEquals(List.of(JdbcType.VARCHAR, JdbcType.VARCHAR), jdbcTypes(h, predicate, parameters));
                }
                var filter = filter("PERSONAL"); var mapping = new FilterConfigDTO.FieldMappingDTO();
                mapping.setUserField("order"); filter.setFieldMapping(mapping);
                var parameters = new LinkedHashMap<String, Object>();
                String predicate = builder.buildFilterSql("asset", filter, user(SPECIAL, null, null), parameters);
                assertScope(h, predicate, parameters, "allowed");
                assertTrue(predicate.startsWith("`order` IN"));
                assertEquals("1=0", builder.buildFilterSql("asset", filter, user(null, null, null), new LinkedHashMap<>()));
            });
        }
    }

    @Test void containsTreatsWildcardsAndBackslashesAsLiteralTextAndReadsTheFullValue() {
        for (boolean noBackslash : List.of(false, true)) try (var f = fixture(new Fixture(noBackslash))) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            h.jdbc.update("INSERT INTO biz_structured_scope(id,`order`,body,deleted) VALUES ('exact',?,?,0),('other','ordinary','ordinary',0),('null',NULL,NULL,0),('deleted',?,?,1)",
                    "prefix" + SPECIAL + "tail", "x".repeat(17000) + SPECIAL, SPECIAL, SPECIAL);
            checkCurrentMode(h, () -> {
                for (String field : List.of("order", "body")) {
                    var parameters = new LinkedHashMap<String, Object>();
                    String predicate = builder.buildFilterSql("asset", rule(field, "CONTAINS", SPECIAL), user("u", null, null), parameters);
                    assertScope(h, predicate, parameters, "exact");
                    assertTrue(predicate.endsWith("ESCAPE '!'")); assertFalse(predicate.contains(SPECIAL));
                    parameters.clear();
                    predicate = builder.buildFilterSql("asset", rule(field, "NOT_CONTAINS", SPECIAL), user("u", null, null), parameters);
                    assertScope(h, predicate, parameters, "other");
                }
            });
        }
    }

    @Test void scalarComparisonsBindExactTypesAndKeepNullInListSemantics() {
        try (var f = fixture()) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            h.jdbc.update("INSERT INTO biz_structured_scope(id,i,l,d,b,day_value,instant_value,deleted) VALUES ('match',12,9007199254740993,12345678901234.1234,1,'2026-09-22','2026-09-22 12:13:14.123456',0),('other',2,9007199254740992,0,0,'2026-09-21','2026-09-21',0),('null',NULL,NULL,NULL,NULL,NULL,NULL,0),('deleted',12,9007199254740993,12345678901234.1234,1,'2026-09-22','2026-09-22 12:13:14.123456',1)");
            record Check(String field, String value, JdbcType type, Object converted) { }
            for (var test : List.of(
                    new Check("i", "12", JdbcType.INTEGER, 12),
                    new Check("l", "9007199254740993", JdbcType.BIGINT, 9007199254740993L),
                    new Check("d", "12345678901234.1234", JdbcType.DECIMAL, new BigDecimal("12345678901234.1234")),
                    new Check("b", "true", JdbcType.INTEGER, 1),
                    new Check("day", "2026-09-22", JdbcType.DATE, LocalDate.of(2026, 9, 22)),
                    new Check("instant", "2026-09-22 12:13:14.123456", JdbcType.TIMESTAMP,
                            LocalDateTime.of(2026, 9, 22, 12, 13, 14, 123456000)))) {
                for (String operator : List.of("EQ", "GTE", "IN")) {
                    Object value = operator.equals("IN") ? Arrays.asList(test.value(), null) : test.value();
                    var filter = rule(test.field(), operator, value); builder.validateFilter("asset", filter);
                    var parameters = new LinkedHashMap<String, Object>();
                    String predicate = builder.buildFilterSql("asset", filter, user("u", null, null), parameters);
                    assertScope(h, predicate, parameters, "match");
                    assertEquals(test.converted(), parameters.get("permissionValue0"));
                    assertTrue(jdbcTypes(h, predicate, parameters).stream().allMatch(type -> type == test.type()));
                    if (operator.equals("IN")) {
                        assertTrue(parameters.containsKey("permissionValue1")); assertNull(parameters.get("permissionValue1"));
                    }
                }
                var parameters = new LinkedHashMap<String, Object>();
                String predicate = builder.buildFilterSql("asset", rule(test.field(), "NOT_IN", Arrays.asList(test.value(), null)), user("u", null, null), parameters);
                assertScope(h, predicate, parameters);
            }
            var parameters = new LinkedHashMap<String, Object>();
            assertScope(h, builder.buildFilterSql("asset", rule("b", "EQ", false), user("u", null, null), parameters), parameters, "other");
            parameters.clear();
            assertScope(h, builder.buildFilterSql("asset", rule("i", "EQ", null), user("u", null, null), parameters), parameters, "null");
            assertTrue(parameters.isEmpty());
        }
    }

    @Test void departmentTreeEscapesIdsWithoutGrantingWildcardSiblings() {
        for (boolean noBackslash : List.of(false, true)) try (var f = fixture(new Fixture(noBackslash))) {
            f.table("sys_organization", "id VARCHAR(64),path VARCHAR(500)");
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            String department = "dept!%_\\'";
            h.jdbc.update("INSERT INTO sys_organization VALUES (?,NULL),('child',?),('sibling',?),('parent','/')",
                    department, "/parent/" + department + "/child/", "/parent/dept!anythingX\\'/sibling/");
            h.jdbc.update("INSERT INTO biz_structured_scope(id,dept_id,deleted) VALUES ('self',?,0),('child','child',0),('sibling','sibling',0),('parent','parent',0),('deleted','child',1)", department);
            checkCurrentMode(h, () -> {
                for (String type : List.of("DEPT", "DEPT_TREE")) {
                    var parameters = new LinkedHashMap<String, Object>();
                    String predicate = builder.buildFilterSql("asset", filter(type), user("u", null, department), parameters);
                    assertScope(h, predicate, parameters, type.equals("DEPT") ? new String[]{"self"} : new String[]{"self", "child"});
                    assertFalse(predicate.contains(department));
                    assertEquals("1=0", builder.buildFilterSql("asset", filter(type), user("u", null, null), new LinkedHashMap<>()));
                }
            });
        }
    }

    @Test void nestedAllowsDeniesAndDelegatedIdentitiesShareOneBindingNamespace() {
        try (var f = fixture()) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            h.jdbc.update("INSERT INTO biz_structured_scope(id,create_by,i,status,deleted) VALUES ('own',?,12,'OPEN',0),('delegated','delegator',12,'OPEN',0),('denied',?,12,'SECRET',0),('small',?,2,'OPEN',0),('other','other',12,'OPEN',0),('deleted',?,12,'OPEN',1)", SPECIAL, SPECIAL, SPECIAL, SPECIAL);
            var own = new EntityActionRule.RuleNode(); own.setType("RELATION"); own.setRelation("CURRENT_USER_IS_CREATOR");
            var amount = rule("i", "GTE", "10").getRoot();
            var root = new EntityActionRule.RuleNode(); root.setType("GROUP"); root.setLogic("AND"); root.setChildren(List.of(own, amount));
            var allowFilter = filter("RULE"); allowFilter.setRoot(root);
            var parameters = new LinkedHashMap<String, Object>(); parameters.put("permissionValue1", null);
            String allowed = builder.buildFilterSql("asset", allowFilter, user(SPECIAL, "reader", null), parameters);
            String delegated = builder.buildFilterSql("asset", allowFilter, user("delegator", null, null), parameters);
            String denied = builder.buildFilterSql("asset", rule("status", "EQ", "SECRET"), user("u", null, null), parameters);
            String combined = "((" + allowed + ") OR (" + delegated + ")) AND NOT (" + denied + ")";
            assertScope(h, combined, parameters, "own", "delegated");
            assertEquals(7, parameters.size()); assertNull(parameters.get("permissionValue1"));
            assertEquals(1, parameters.values().stream().filter(SPECIAL::equals).count());
            assertFalse(combined.contains(SPECIAL));
        }
    }

    @Test void invalidTypedRulesRejectBeforeAnyDatabaseQueryAndCustomProvidersCanBind() {
        try (var f = fixture()) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            for (var invalid : List.of(rule("i", "EQ", "12tail"), rule("i", "EQ", "1.2"),
                    rule("i", "EQ", "2147483648"), rule("l", "EQ", "9223372036854775808"),
                    rule("d", "EQ", "NaN"), rule("b", "EQ", "2"), rule("day", "EQ", "2026-02-30"),
                    rule("instant", "EQ", "2026-09-22T12:13:14+08:00"))) {
                assertThrows(IllegalArgumentException.class, () -> builder.validateFilter("asset", invalid));
                assertThrows(IllegalArgumentException.class, () -> builder.buildFilterSql("asset", invalid, user("u", null, null), new LinkedHashMap<>()));
            }
            h.jdbc.update("INSERT INTO biz_structured_scope(id,`order`,deleted) VALUES ('match',?,0),('other','other',0)", SPECIAL);
            var provider = new com.workflow.contracts.entity.permission.spi.EntityDataPermissionFilterProvider() {
                public String getType() { return "CUSTOM:BOUND"; }
                public String toSql(String entity, EntityActionRule.RuleNode node, com.workflow.contracts.identity.model.IdentityUser user) { throw new AssertionError("Expected shared bindings"); }
                public String toSql(String entity, EntityActionRule.RuleNode node, com.workflow.contracts.identity.model.IdentityUser user, Map<String, Object> parameters) {
                    return "`order` = " + PermissionSqlParameters.bindText(parameters, user.id());
                }
            };
            var custom = new PermissionSqlBuilder(null, null, null, List.of(provider), DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
            var node = new EntityActionRule.RuleNode(); node.setType("CUSTOM:BOUND"); var filter = filter("RULE"); filter.setRoot(node);
            var parameters = new LinkedHashMap<String, Object>();
            assertScope(h, custom.buildFilterSql("asset", filter, user(SPECIAL, null, null), parameters), parameters, "match");
        }
    }

    @Test void customListPageKeepsNullableTypedPermissionParameters() {
        try (var f = fixture()) {
            var h = new Harness(f, EntityDataDynamicMapper.class);
            h.jdbc.update("INSERT INTO biz_structured_scope(id,i,deleted) VALUES ('match',12,0),('other',2,0),('null',NULL,0),('deleted',12,1)");
            var parameters = new LinkedHashMap<String, Object>();
            String predicate = builder().buildFilterSql("asset", rule("i", "IN", Arrays.asList("12", null)), user("u", null, null), parameters);
            var plan = new com.workflow.contracts.entity.list.model.DataScopePlan(true, predicate, parameters, List.of(), List.of("number"), "", 1);
            var page = service(h).findPageWithDataScopePlan("asset", Map.of(), 1, 10, plan);
            assertEquals(1, page.getTotal());
            assertEquals(List.of("match"), page.getRecords().stream().map(row -> row.getId()).toList());
            assertTrue(parameters.containsKey("permissionValue1")); assertNull(parameters.get("permissionValue1"));
        }
    }

    @Test void missingIdentityCannotReadLegacyRecordsWithEmptyCreator() {
        try (var f = fixture()) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var service = service(h);
            h.jdbc.update("INSERT INTO biz_structured_scope(id,create_by,deleted) VALUES ('empty','',0),('null',NULL,0),('other','other',0)");
            com.workflow.admin.security.context.UserContext.clear();
            try {
                assertTrue(service.findByEntityCodeSimple("asset").isEmpty());
                assertTrue(service.findByEntityCode("asset").isEmpty());
                assertEquals(0, service.findPage("asset", "list", Map.of(), 1, 10).getTotal());
                // 已登录但用户记录不存在时，不能退回匹配空创建人的 SQL。
                com.workflow.admin.security.context.UserContext.setCurrentUser("missing", "missing");
                assertTrue(service.findByEntityCodeSimple("asset").isEmpty());
            } finally { com.workflow.admin.security.context.UserContext.clear(); }
            assertEquals(3, h.mapper(EntityDataDynamicMapper.class).count(TABLE));
        }
    }

    private static com.workflow.entity.data.application.EntityDataDynamicService service(Harness h) {
        var definitions = mock(EntityDefinitionMapper.class); var definition = new EntityDefinition(); definition.setId("entity");
        when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(definition));
        var tables = mock(com.workflow.entity.data.application.DynamicTableService.class); when(tables.getTableName("asset")).thenReturn(TABLE);
        var multi = mock(com.workflow.entity.data.application.EntityMultiValueRuntimeService.class);
        when(multi.prepareConditions(eq(definition), anyMap())).thenReturn(
                new com.workflow.entity.data.application.EntityMultiValueRuntimeService.PreparedConditions(Map.of(), null));
        var snapshots = mock(com.workflow.entity.definition.application.EntityPublishedSnapshotService.class);
        var snapshot = new com.workflow.entity.definition.application.model.EntityPublishedSnapshot(); snapshot.setFields(List.of());
        when(snapshots.getLatestByEntityCode("asset")).thenReturn(snapshot);
        return new com.workflow.entity.data.application.EntityDataDynamicService(h.mapper(EntityDataDynamicMapper.class), definitions, tables,
                new com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper(h.json),
                mock(com.workflow.entity.data.application.EntityRelationRuntimeService.class), multi,
                mock(com.workflow.entity.permission.application.DataPermissionEngine.class),
                mock(com.workflow.admin.identity.user.application.SysUserService.class), snapshots);
    }

    private static Fixture fixture() { return fixture(new Fixture()); }
    private static Fixture fixture(Fixture f) {
        f.table(TABLE, "id VARCHAR(64) PRIMARY KEY,`order` VARCHAR(500),body LONGTEXT,create_by VARCHAR(300),submitter_id VARCHAR(300),current_task_assignee VARCHAR(300),dept_id VARCHAR(64),status VARCHAR(300),i INT,l BIGINT,d DECIMAL(20,4),b TINYINT(1),day_value DATE,instant_value DATETIME(6),deleted INT,create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return f;
    }
    private static PermissionSqlBuilder builder() {
        var definitions = mock(EntityDefinitionMapper.class); var fields = mock(EntityFieldMapper.class);
        var definition = new EntityDefinition(); definition.setId("entity"); when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(definition));
        when(fields.findByEntityId("entity")).thenReturn(List.of(field("order", "order", EntityField.FieldType.STRING),
                field("body", "body", EntityField.FieldType.TEXT), field("i", "i", EntityField.FieldType.INTEGER),
                field("l", "l", EntityField.FieldType.LONG), field("d", "d", EntityField.FieldType.DECIMAL),
                field("b", "b", EntityField.FieldType.BOOLEAN), field("day", "day_value", EntityField.FieldType.DATE),
                field("instant", "instant_value", EntityField.FieldType.DATETIME)));
        return new PermissionSqlBuilder(definitions, fields, null, List.of(), DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
    }
    private static EntityField field(String code, String column, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setDbColumnName(column); field.setFieldType(type); return field;
    }
    private static FilterConfigDTO rule(String field, String operator, Object value) {
        var filter = filter("RULE"); var node = new EntityActionRule.RuleNode();
        node.setType("FIELD"); node.setField(field); node.setOperator(operator); node.setValue(value); filter.setRoot(node); return filter;
    }
    private static FilterConfigDTO filter(String type) { var filter = new FilterConfigDTO(); filter.setType(type); return filter; }
    private static SysUser user(String id, String name, String dept) { var user = new SysUser(); user.setId(id); user.setUsername(name); user.setDeptId(dept); return user; }
    private static void assertScope(Harness h, String predicate, Map<String, Object> parameters, String... expected) {
        var mapper = h.mapper(EntityDataDynamicMapper.class);
        assertEquals(Set.of(expected), new HashSet<>(mapper.selectPageWithPermission(TABLE, predicate, parameters, 0, 20).stream().map(row -> (String) row.get("id")).toList()), predicate);
        assertEquals(expected.length, mapper.countWithPermission(TABLE, predicate, parameters), predicate);
    }
    private static List<JdbcType> jdbcTypes(Harness h, String predicate, Map<String, Object> parameters) {
        var args = Map.of("tableName", TABLE, "permissionSql", predicate, "permissionParameters", parameters);
        return h.session.getConfiguration().getMappedStatement(EntityDataDynamicMapper.class.getName() + ".countWithPermission")
                .getBoundSql(args).getParameterMappings().stream().map(mapping -> mapping.getJdbcType()).toList();
    }
    /** 各测试模式分别新建驱动连接；清缓存后验证实际 JDBC 值与查询结果。 */
    private static void checkCurrentMode(Harness h, Runnable action) {
        h.tx.executeWithoutResult(status -> {
            String mode = h.jdbc.queryForObject("SELECT @@SESSION.sql_mode", String.class);
            assertEquals(SPECIAL, h.jdbc.queryForObject("SELECT ?", String.class, SPECIAL), "JDBC round trip in " + mode);
            h.session.clearCache();
            try { action.run(); } catch (AssertionError failure) { throw new AssertionError("sql_mode=" + mode, failure); }
        });
    }
}
