package com.workflow.entity.data;

import com.workflow.entity.data.application.EntityQueryConditions;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import static org.junit.jupiter.api.Assertions.*;

/** 随机隔离表执行生产动态 Mapper，验证比较类型、精度、权限及 count/page 一致性。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlTypedQueryBindingDatabaseTest {
    private static final String TABLE = "biz_typed_query";
    private static final String SCOPE = "create_by = #{permissionParameters.owner,jdbcType=VARCHAR}"
            + " AND #{permissionParameters.missing,jdbcType=VARCHAR} IS NULL";
    private static final List<EntityField> FIELDS = List.of(
            field("quantity", "quantity", EntityField.FieldType.INTEGER), field("serial", "serial", EntityField.FieldType.LONG),
            field("amount", "amount", EntityField.FieldType.DECIMAL), field("enabled", "enabled", EntityField.FieldType.BOOLEAN),
            field("day", "day_value", EntityField.FieldType.DATE), field("instant", "instant_value", EntityField.FieldType.DATETIME),
            field("body", "body", EntityField.FieldType.TEXT), field("legacy", "legacy_body", EntityField.FieldType.MULTI_REFERENCE));

    @Test
    void typedComparisonsPreservePrecisionBooleanMeaningAndPermissionAcrossAllReadMethods() {
        record Case(String field, String raw, Object converted, JdbcType jdbc) { }
        try (var f = fixture(); var session = f.session(EntityDataDynamicMapper.class)) {
            var mapper = session.getMapper(EntityDataDynamicMapper.class);
            for (var test : List.of(
                    new Case("quantity", "12", 12, JdbcType.INTEGER),
                    new Case("serial", "9223372036854775807", Long.MAX_VALUE, JdbcType.BIGINT),
                    new Case("amount", "12345678901234.1234", new BigDecimal("12345678901234.1234"), JdbcType.DECIMAL),
                    new Case("enabled", "true", 1, JdbcType.INTEGER),
                    new Case("day", "2026-09-23", LocalDate.of(2026, 9, 23), JdbcType.DATE),
                    new Case("instant", "2026-09-23 10:20:30.123456", LocalDateTime.of(2026, 9, 23, 10, 20, 30, 123456000), JdbcType.TIMESTAMP))) {
                for (String operator : List.of("EQ", "NE", "GT", "LT", "IN", "NOT_IN")) {
                    Object value = operator.endsWith("IN") ? Arrays.asList(test.raw(), null, " ") : test.raw();
                    var condition = typed(Map.of(test.field(), value, test.field() + "_op", operator));
                    Set<String> expected = switch (operator) {
                        case "EQ", "IN" -> Set.of("match", "shadow");
                        case "NE", "LT", "NOT_IN" -> Set.of("other");
                        default -> Set.of();
                    };
                    assertReads(mapper, condition, expected, expected.contains("match") ? Set.of("match") : expected);
                    assertBinding(session, condition, test.converted(), test.jdbc());
                }
            }
            // 两个相邻 BIGINT 均超出双精度整数的精确范围，旧字符串比较可能把它们混为同值。
            assertReads(mapper, typed(Map.of("serial", "9223372036854775806", "serial_op", "EQ")), Set.of("other"), Set.of("other"));
            assertReads(mapper, typed(Map.of("enabled", "false", "enabled_op", "EQ")), Set.of("other"), Set.of("other"));
        }
    }

    @Test
    void rangesBindPublishedAliasesAndSystemTimestampsAndKeepPageOffsets() {
        try (var f = fixture(); var session = f.session(EntityDataDynamicMapper.class)) {
            var mapper = session.getMapper(EntityDataDynamicMapper.class);
            for (Map<String, Object> source : List.of(
                    Map.<String, Object>of("amount_start", "12345678901234.1234", "amount_end", "12345678901234.1234"),
                    Map.<String, Object>of("day_start", "2026-09-23", "day_end", "2026-09-23"),
                    Map.<String, Object>of("instant_start", "2026-09-23 10:20:30.123456"),
                    Map.<String, Object>of("createTime_end", "2026-09-23 00:00:02"))) {
                assertReads(mapper, typed(source), Set.of("match", "shadow"), Set.of("match"));
            }
            var condition = typed(Map.of("quantity", "12", "quantity_op", "EQ"));
            assertEquals(List.of("shadow"), ids(mapper.selectPageByCondition(TABLE, condition, 0, 1)));
            assertEquals(List.of("match"), ids(mapper.selectPageByCondition(TABLE, condition, 1, 1)));
            assertTrue(mapper.selectPageByCondition(TABLE, condition, 2, 1).isEmpty());
            assertTrue(mapper.selectPageByCondition(TABLE, condition, 0, 0).isEmpty());
            assertEquals("12", condition.get("quantity"), "count/page 不能改写原条件，后续默认 LIKE 仍依赖输入类型");
        }
    }

    @Test
    void defaultLikeNullBlankAndEmptyInKeepTheirExistingResults() {
        try (var f = fixture(); var session = f.session(EntityDataDynamicMapper.class)) {
            var mapper = session.getMapper(EntityDataDynamicMapper.class);
            assertReads(mapper, typed(Map.of("quantity", "2")), Set.of("match", "shadow", "other"), Set.of("match", "other"));
            assertReads(mapper, typed(Map.of("quantity", "2", "quantity_op", "EQ")), Set.of("other"), Set.of("other"));
            assertReads(mapper, typed(Map.of("quantity", "12, ,", "quantity_op", "IN")), Set.of("match", "shadow"), Set.of("match"));
            assertReads(mapper, typed(Map.of("quantity", Arrays.asList(null, " "), "quantity_op", "IN")), Set.of(), Set.of());
            Set<String> all = Set.of("match", "shadow", "other", "null");
            Set<String> visible = Set.of("match", "other", "null");
            assertReads(mapper, typed(Map.of("quantity", List.of(), "quantity_op", "NOT_IN")), all, visible);
            var absent = new LinkedHashMap<String, Object>();
            absent.put("quantity", null); absent.put("quantity_op", "EQ"); absent.put("day_start", " ");
            assertReads(mapper, typed(absent), all, visible);
        }
    }

    @Test
    void malformedTypedInputsAreRejectedInsteadOfUsingMysqlCoercion() {
        try (var f = fixture(); var session = f.session(EntityDataDynamicMapper.class)) {
            var mapper = session.getMapper(EntityDataDynamicMapper.class);
            for (var invalid : List.of(Map.entry("quantity", "12tail"), Map.entry("quantity", "1.5"),
                    Map.entry("quantity", "2147483648"), Map.entry("serial", "9223372036854775808"),
                    Map.entry("enabled", "2"), Map.entry("amount", "NaN"), Map.entry("day", "2026-02-30"),
                    Map.entry("instant", "2026-09-23T10:20:30+08:00"))) {
                assertThrows(RuntimeException.class, () -> mapper.countByCondition(TABLE,
                        typed(Map.of(invalid.getKey(), invalid.getValue(), invalid.getKey() + "_op", "EQ"))));
                assertThrows(RuntimeException.class, () -> mapper.selectPageByCondition(TABLE,
                        typed(Map.of(invalid.getKey(), List.of(invalid.getValue()), invalid.getKey() + "_op", "IN")), 0, 10));
            }
            assertEquals(4, mapper.count(TABLE));
        }
    }

    @Test
    void textComparisonsKeepFullSuffixOrderingListsAndNullRowsOnMysql() {
        String first = "x".repeat(40000) + "尾'A", second = "x".repeat(40000) + "尾'B";
        try (var f = new Fixture()) {
            String table = f.table(TABLE, "id VARCHAR(64) PRIMARY KEY,body TEXT,legacy_body LONGTEXT,"
                    + "create_by VARCHAR(64),deleted INT,create_time DATETIME(6)");
            f.jdbc.update("INSERT INTO " + table + " VALUES ('match',?,?,'reader',0,'2026-09-23 00:00:01'),"
                    + "('other',?,?,'reader',0,'2026-09-23 00:00:02'),('null',NULL,NULL,'reader',0,'2026-09-23 00:00:03'),"
                    + "('deleted',?,?,'reader',1,'2026-09-23 00:00:04')", first, first, second, second, first, first);
            try (var session = f.session(EntityDataDynamicMapper.class)) {
                var mapper = session.getMapper(EntityDataDynamicMapper.class);
                for (String field : List.of("body", "legacy")) {
                    for (String operator : List.of("EQ", "NE", "GT", "LT", "IN", "NOT_IN")) {
                        Object value = operator.endsWith("IN") ? Arrays.asList(first, null) : first;
                        Set<String> expected = switch (operator) {
                            case "EQ", "IN" -> Set.of("match");
                            case "NE", "GT", "NOT_IN" -> Set.of("other");
                            default -> Set.of();
                        };
                        var condition = typed(Map.of(field, value, field + "_op", operator));
                        assertReads(mapper, condition, expected, expected);
                        assertBinding(session, condition, first, JdbcType.VARCHAR);
                    }
                    assertReads(mapper, typed(Map.of(field + "_start", first, field + "_end", first)), Set.of("match"), Set.of("match"));
                    assertReads(mapper, typed(Map.of(field, List.of(first, second), field + "_op", "IN")),
                            Set.of("match", "other"), Set.of("match", "other"));
                    assertReads(mapper, typed(Map.of(field, List.of(first, second), field + "_op", "NOT_IN")), Set.of(), Set.of());
                    assertReads(mapper, typed(Map.of(field, "尾'A")), Set.of("match"), Set.of("match"));
                }
            }
        }
    }

    private static Fixture fixture() {
        var f = new Fixture();
        String table = f.table(TABLE, "id VARCHAR(64) PRIMARY KEY,quantity INT,serial BIGINT,amount DECIMAL(20,4),enabled TINYINT(1),"
                + "day_value DATE,instant_value DATETIME(6),create_by VARCHAR(64),deleted INT,create_time DATETIME(6)");
        f.jdbc.update("INSERT INTO " + table + " VALUES "
                + "('match',12,9223372036854775807,12345678901234.1234,1,'2026-09-23','2026-09-23 10:20:30.123456','reader',0,'2026-09-23 00:00:01'),"
                + "('shadow',12,9223372036854775807,12345678901234.1234,1,'2026-09-23','2026-09-23 10:20:30.123456','another',0,'2026-09-23 00:00:02'),"
                + "('other',2,9223372036854775806,2,0,'2026-09-22','2026-09-22 10:20:30','reader',0,'2026-09-23 00:00:03'),"
                + "('null',NULL,NULL,NULL,NULL,NULL,NULL,'reader',0,'2026-09-23 00:00:04'),"
                + "('deleted',12,9223372036854775807,12345678901234.1234,1,'2026-09-23','2026-09-23 10:20:30.123456','reader',1,'2026-09-23 00:00:05')");
        return f;
    }

    private static EntityField field(String code, String column, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setDbColumnName(column); field.setFieldType(type);
        if (type == EntityField.FieldType.DECIMAL) { field.setFieldLength(20); field.setFieldPrecision(4); }
        return field;
    }

    private static EntityQueryConditions typed(Map<String, Object> source) {
        return EntityQueryConditions.fromPublishedFields(source, FIELDS);
    }

    private static void assertReads(EntityDataDynamicMapper mapper, Map<String, Object> condition,
                                    Set<String> expected, Set<String> permitted) {
        Map<String, Object> parameters = new LinkedHashMap<>(); parameters.put("owner", "reader"); parameters.put("missing", null);
        assertEquals(expected.size(), mapper.countByCondition(TABLE, condition));
        assertEquals(expected, new HashSet<>(ids(mapper.selectByCondition(TABLE, condition))));
        assertEquals(expected, new HashSet<>(ids(mapper.selectPageByCondition(TABLE, condition, 0, 20))));
        assertEquals(permitted.size(), mapper.countByConditionWithPermission(TABLE, condition, SCOPE, parameters));
        assertEquals(permitted, new HashSet<>(ids(mapper.selectByConditionWithPermission(TABLE, condition, SCOPE, parameters))));
        assertEquals(permitted, new HashSet<>(ids(mapper.selectPageByConditionWithPermission(TABLE, condition, SCOPE, parameters, 0, 20))));
        assertEquals(2, parameters.size()); assertTrue(parameters.containsKey("missing")); assertNull(parameters.get("missing"));
    }

    private static List<String> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> (String) row.get("id")).toList();
    }

    private static void assertBinding(SqlSession session, Map<String, Object> condition, Object value, JdbcType type) {
        var args = new LinkedHashMap<String, Object>(); args.put("tableName", TABLE); args.put("condition", condition);
        var configuration = session.getConfiguration();
        var bound = configuration.getMappedStatement(EntityDataDynamicMapper.class.getName() + ".countByCondition").getBoundSql(args);
        assertEquals(List.of(type), bound.getParameterMappings().stream().map(mapping -> mapping.getJdbcType()).toList());
        assertEquals(List.of(value), bound.getParameterMappings().stream()
                .map(mapping -> configuration.newMetaObject(args).getValue(mapping.getProperty())).toList());
    }
}
