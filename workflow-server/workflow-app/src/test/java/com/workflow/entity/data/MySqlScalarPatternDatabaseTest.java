package com.workflow.entity.data;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.application.EntityQueryConditions;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.permission.application.PermissionSqlBuilder;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.SchemaType;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.*;

import static com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import static com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 对照 MySQL 原生 LIKE，验证数值完整精度、日期小数秒及权限结果，没有用 H2 模拟隐式转换。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlScalarPatternDatabaseTest {
    private static final String TABLE = "biz_scalar_patterns";
    private static final String BIG_DECIMAL = "12345678901234567890123456789012345.123456789012345678901234567890";
    private static final String TINY_DECIMAL = "0.000000000000000000000000000001";
    private static final String WHOLE_DECIMAL = "12345678901234567890123456789012345678901234567890123456789012345";
    private static final List<EntityField> FIELDS = List.of(
            field("i", EntityField.FieldType.INTEGER, null, null), field("l", EntityField.FieldType.LONG, null, null),
            field("amount", EntityField.FieldType.DECIMAL, 65, 30), field("tiny", EntityField.FieldType.DECIMAL, 30, 30),
            field("whole", EntityField.FieldType.DECIMAL, 65, 0), field("flag", EntityField.FieldType.BOOLEAN, null, null),
            field("day_value", EntityField.FieldType.DATE, null, null), field("instant_value", EntityField.FieldType.DATETIME, null, null),
            field("instant_seconds", EntityField.FieldType.DATETIME, null, null));

    @Test void fullScalarTextMatchesLegacyRepresentationWithoutPrecisionLoss() {
        try (var f = fixture(false)) {
            var h = new Harness(f, EntityDataDynamicMapper.class);
            var dialect = DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL);
            Map<String, String> expected = Map.of("i", "-2147483648", "l", "-9223372036854775808",
                    "amount", BIG_DECIMAL, "tiny", TINY_DECIMAL, "whole", WHOLE_DECIMAL,
                    "flag", "1", "day_value", "2026-09-23", "instant_value", "2026-09-23 01:02:03.120000",
                    "instant_seconds", "2026-09-23 01:02:03");
            for (var field : FIELDS) {
                SchemaType type = com.workflow.entity.data.application.EntityTableDefinitionFactory.fieldType(field);
                String expression = dialect.patternValueExpression(field.getDbColumnName(), type);
                assertEquals(expected.get(field.getFieldCode()), h.jdbc.queryForObject(
                        "SELECT " + expression + " FROM " + TABLE + " WHERE id='match'", String.class), field.getFieldCode());
                assertNull(h.jdbc.queryForObject("SELECT " + expression + " FROM " + TABLE + " WHERE id='null'", String.class));
            }
        }
    }

    @Test void containsAndNotContainsKeepLegacyScopesInBothConnectionModes() {
        for (boolean noBackslash : List.of(false, true)) try (var f = fixture(noBackslash)) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            h.tx.executeWithoutResult(status -> {
                assertEquals(noBackslash, Objects.requireNonNull(h.jdbc.queryForObject("SELECT @@SESSION.sql_mode", String.class)).contains("NO_BACKSLASH_ESCAPES"));
                String probe = "'\\!%_";
                assertEquals(probe, h.jdbc.queryForObject("SELECT ?", String.class, probe));
                record Check(String column, Object value) { }
                for (var check : List.of(new Check("i", "-2147483648"), new Check("i", 2147),
                        new Check("l", "9223372036854775808"), new Check("amount", BIG_DECIMAL),
                        new Check("tiny", TINY_DECIMAL), new Check("whole", WHOLE_DECIMAL),
                        new Check("flag", "1"), new Check("flag", "true"), new Check("day_value", "09-23"),
                        new Check("instant_value", "03.120000"), new Check("instant_value", "03.000000"),
                        new Check("instant_seconds", "01:02:03"), new Check("instant_seconds", ".000000"),
                        new Check("amount", "E+"), new Check("amount", "%"), new Check("l", "1' OR 1=1 --"),
                        new Check("instant_value", "\\"))) {
                    for (String operator : List.of("CONTAINS", "NOT_CONTAINS")) {
                        String pattern = "%" + String.valueOf(check.value()).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                        // 预期来自原生列 LIKE 的实际结果，避免测试重复新的 CAST 实现。
                        Set<String> expected = new HashSet<>(h.jdbc.queryForList("SELECT id FROM " + TABLE + " WHERE deleted=0 AND `"
                                + check.column() + "` " + (operator.equals("CONTAINS") ? "LIKE" : "NOT LIKE") + " ? ESCAPE '!'", String.class, pattern));
                        var filter = rule(check.column(), operator, check.value()); builder.validateFilter("asset", filter);
                        var params = new LinkedHashMap<String, Object>();
                        String predicate = builder.buildFilterSql("asset", filter, user(), params);
                        assertEquals(List.of(pattern), new ArrayList<>(params.values()));
                        assertScope(h, predicate, params, expected);
                        var bound = h.session.getConfiguration().getMappedStatement(EntityDataDynamicMapper.class.getName() + ".countWithPermission")
                                .getBoundSql(Map.of("tableName", TABLE, "permissionSql", predicate, "permissionParameters", params));
                        assertEquals(List.of(JdbcType.VARCHAR), bound.getParameterMappings().stream().map(value -> value.getJdbcType()).toList());
                    }
                }
            });
        }
    }

    @Test void scalarContainsKeepsGroupNegationNullAndDeletedRowSemantics() {
        try (var f = fixture(false)) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            var filter = rule("l", "CONTAINS", "9223372036854775808");
            var group = new EntityActionRuleDTO.RuleNode(); group.setType("GROUP"); group.setLogic("AND");
            group.setChildren(List.of(filter.getRoot(), rule("flag", "EQ", true).getRoot())); filter.setRoot(group);
            var params = new LinkedHashMap<String, Object>();
            String allowed = builder.buildFilterSql("asset", filter, user(), params);
            assertScope(h, allowed, params, Set.of("match"));
            String denied = builder.buildFilterSql("asset", rule("amount", "CONTAINS", BIG_DECIMAL), user(), params);
            assertScope(h, "(" + allowed + ") AND NOT (" + denied + ")", params, Set.of());
            // 既有编译入口对 NULL 包含值的常量语义保留，不能以 SQL NULL 规则偷偷替换。
            assertEquals("1=0", builder.buildFilterSql("asset", rule("i", "CONTAINS", null), user(), new LinkedHashMap<>()));
            assertEquals("1=1", builder.buildFilterSql("asset", rule("i", "NOT_CONTAINS", null), user(), new LinkedHashMap<>()));
        }
    }

    @Test void ordinaryConditionQueriesShareTypedFieldsAndKeepAliasesSeparateFromParameters() {
        try (var f = fixture(false)) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var mapper = h.mapper(EntityDataDynamicMapper.class);
            var alias = field("l", EntityField.FieldType.LONG, null, null); alias.setFieldCode("largeNumber");
            var fields = FIELDS.stream().filter(field -> !field.getFieldCode().equals("l")).collect(java.util.stream.Collectors.toCollection(ArrayList::new)); fields.add(alias);
            for (var values : List.of(Map.<String, Object>of("largeNumber", "9223372036854775808"),
                    Map.<String, Object>of("l", "9223372036854775808", "l_op", "LIKE"))) {
                var condition = EntityQueryConditions.fromPublishedFields(values, fields);
                assertEquals(values, condition); assertThrows(UnsupportedOperationException.class, () -> condition.put("i", 123));
                String permission = "id = #{permissionParameters.owner,jdbcType=VARCHAR}"; var params = Map.<String, Object>of("owner", "match");
                assertEquals(List.of("match"), ids(mapper.selectByCondition(TABLE, condition)));
                assertEquals(List.of("match"), ids(mapper.selectByConditionWithPermission(TABLE, condition, permission, params)));
                assertEquals(List.of("match"), ids(mapper.selectPageByCondition(TABLE, condition, 0, 1)));
                assertEquals(List.of("match"), ids(mapper.selectPageByConditionWithPermission(TABLE, condition, permission, params, 0, 1)));
                assertEquals(1, mapper.countByCondition(TABLE, condition));
                assertEquals(1, mapper.countByConditionWithPermission(TABLE, condition, permission, params));
                assertTrue(mapper.selectPageByCondition(TABLE, condition, 1, 1).isEmpty());
            }
            // 普通列表的 LIKE 本来允许通配符，与权限 CONTAINS 的字面量规则分别保留。
            var wildcard = EntityQueryConditions.fromPublishedFields(Map.of("i", "%", "i_op", "LIKE"), FIELDS);
            assertEquals(Set.of("match", "other"), new HashSet<>(ids(mapper.selectByCondition(TABLE, wildcard))));
        }
    }

    @Test void applicationListPlansCarryPublishedFieldTypesToCountAndPageQueries() {
        try (var f = fixture(false)) {
            var h = new Harness(f, EntityDataDynamicMapper.class);
            var definitions = mock(EntityDefinitionMapper.class); var definition = new EntityDefinition(); definition.setId("entity");
            when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(definition));
            var tables = mock(com.workflow.entity.data.application.DynamicTableService.class); when(tables.getTableName("asset")).thenReturn(TABLE);
            var multi = mock(com.workflow.entity.data.application.EntityMultiValueRuntimeService.class);
            when(multi.prepareConditions(eq(definition), anyMap())).thenAnswer(call ->
                    new com.workflow.entity.data.application.EntityMultiValueRuntimeService.PreparedConditions(call.getArgument(1), null));
            var snapshots = mock(com.workflow.entity.definition.application.EntityPublishedSnapshotService.class);
            var snapshot = new com.workflow.entity.definition.application.model.EntityPublishedSnapshot(); snapshot.setFields(FIELDS);
            when(snapshots.getLatestByEntityCode("asset")).thenReturn(snapshot);
            var service = new com.workflow.entity.data.application.EntityDataDynamicService(h.mapper(EntityDataDynamicMapper.class), definitions, tables,
                    new com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper(h.json),
                    mock(com.workflow.entity.data.application.EntityRelationRuntimeService.class), multi,
                    mock(com.workflow.entity.permission.application.DataPermissionEngine.class),
                    mock(com.workflow.admin.identity.user.application.SysUserService.class), snapshots);
            var plan = new com.workflow.contracts.entity.list.DataScopePlan(true, "1=1", Map.of(), List.of(), List.of(), "", 1);
            var page = service.findPageWithDataScopePlan("asset", Map.of("l", "9223372036854775808"), 1, 1, plan);
            assertEquals(1, page.getTotal()); assertEquals(List.of("match"), page.getRecords().stream().map(row -> row.getId()).toList());
            assertTrue(service.findPageWithDataScopePlan("asset", Map.of("l", "9223372036854775808"), 2, 1, plan).getRecords().isEmpty());
        }
    }

    @Test void unpublishedAndVirtualConditionsCannotSupplyTheirOwnTypeMetadata() {
        var subform = field("children", EntityField.FieldType.SUB_FORM, null, null);
        var multi = field("choices", EntityField.FieldType.MULTI_SELECT, null, null); multi.setDictType("category");
        var fields = new ArrayList<>(FIELDS); fields.add(subform); fields.add(multi);
        var values = new LinkedHashMap<String, Object>(); values.put("l", null); values.put("columns", Map.of("missing", "STRING"));
        var typed = EntityQueryConditions.fromPublishedFields(values, fields);
        assertNull(typed.get("l")); assertTrue(typed.containsKey("l"));
        for (String key : List.of("missing", "children", "choices", "columns")) assertThrows(IllegalArgumentException.class, () -> typed.column(key));
        values.put("l", "changed"); assertNull(typed.get("l"));
        var collision = field("id", EntityField.FieldType.INTEGER, null, null); collision.setFieldCode("fakeId");
        assertThrows(IllegalArgumentException.class, () -> EntityQueryConditions.fromPublishedFields(Map.of(), List.of(collision)));
    }

    private static List<String> ids(List<Map<String, Object>> rows) { return rows.stream().map(row -> (String) row.get("id")).toList(); }

    private static Fixture fixture(boolean noBackslash) {
        var f = new Fixture(noBackslash);
        f.table(TABLE, "id VARCHAR(64) PRIMARY KEY,i INT,l BIGINT,amount DECIMAL(65,30),tiny DECIMAL(30,30),whole DECIMAL(65,0),flag TINYINT,day_value DATE,instant_value DATETIME(6),instant_seconds DATETIME,deleted INT,create_time DATETIME");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(f.isolatedDataSource());
        for (String id : List.of("match", "deleted")) jdbc.update("INSERT INTO " + TABLE + " VALUES (?,-2147483648,-9223372036854775808,?,?,?,1,'2026-09-23','2026-09-23 01:02:03.120000','2026-09-23 01:02:03',?,NULL)",
                id, new java.math.BigDecimal(BIG_DECIMAL), new java.math.BigDecimal(TINY_DECIMAL), new java.math.BigDecimal(WHOLE_DECIMAL), id.equals("deleted") ? 1 : 0);
        jdbc.update("INSERT INTO " + TABLE + " VALUES ('other',12,9007199254740993,12.30,0,0,0,'2026-09-22','2026-09-23 01:02:03','2026-09-23 01:02:03',0,NULL),('null',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL)");
        return f;
    }

    private static PermissionSqlBuilder builder() {
        var definitions = mock(EntityDefinitionMapper.class); var fields = mock(EntityFieldMapper.class);
        var definition = new EntityDefinition(); definition.setId("entity"); when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(definition));
        when(fields.findByEntityId("entity")).thenReturn(FIELDS);
        return new PermissionSqlBuilder(definitions, fields, null, List.of(), DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
    }
    private static EntityField field(String column, EntityField.FieldType type, Integer precision, Integer scale) {
        var field = new EntityField(); field.setFieldCode(column); field.setDbColumnName(column); field.setFieldType(type); field.setFieldLength(precision); field.setFieldPrecision(scale); return field;
    }
    private static FilterConfigDTO rule(String field, String operator, Object value) {
        var filter = new FilterConfigDTO(); filter.setType("RULE"); var node = new EntityActionRuleDTO.RuleNode();
        node.setType("FIELD"); node.setField(field); node.setOperator(operator); node.setValue(value); filter.setRoot(node); return filter;
    }
    private static SysUser user() { var user = new SysUser(); user.setId("user"); return user; }
    private static void assertScope(Harness h, String predicate, Map<String, Object> params, Set<String> expected) {
        var mapper = h.mapper(EntityDataDynamicMapper.class);
        assertEquals(expected, new HashSet<>(mapper.selectPageWithPermission(TABLE, predicate, params, 0, 20).stream().map(row -> (String) row.get("id")).toList()), predicate);
        assertEquals(expected.size(), mapper.countWithPermission(TABLE, predicate, params), predicate);
    }
}
