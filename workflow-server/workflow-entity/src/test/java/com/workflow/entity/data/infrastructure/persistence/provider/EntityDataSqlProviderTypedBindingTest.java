package com.workflow.entity.data.infrastructure.persistence.provider;

import com.workflow.entity.data.application.EntityQueryConditions;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.application.PermissionSqlParameters;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.schema.SchemaType;
import org.apache.ibatis.builder.annotation.ProviderContext;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.io.Reader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 通过实际 MyBatis ParameterHandler 验证类型绑定；其他数据库仅渲染，不模拟实库兼容性。 */
class EntityDataSqlProviderTypedBindingTest {
    private final EntityDataSqlProvider provider = new EntityDataSqlProvider();

    @Test
    void comparisonsAndListsBindConvertedJavaValuesOnEveryDialect() throws Exception {
        record Case(String field, String raw, Object value, JdbcType jdbc, String setter) { }
        var cases = List.of(
                new Case("quantity", "12", 12, JdbcType.INTEGER, "setInt"),
                new Case("serial", "9223372036854775807", Long.MAX_VALUE, JdbcType.BIGINT, "setLong"),
                new Case("amount", "12345678901234.1234", new BigDecimal("12345678901234.1234"), JdbcType.DECIMAL, "setBigDecimal"),
                new Case("enabled", "true", 1, JdbcType.INTEGER, "setInt"),
                new Case("day", "2026-09-23", LocalDate.of(2026, 9, 23), JdbcType.DATE, "setObject"),
                new Case("instant", "2026-09-23 10:20:30.123456", LocalDateTime.of(2026, 9, 23, 10, 20, 30, 123456000), JdbcType.TIMESTAMP, "setObject"));
        for (var vendor : DatabaseVendor.values()) for (var test : cases) {
            for (String operator : List.of("EQ", "NE", "GT", "LT", "IN", "NOT_IN")) {
                Object raw = operator.endsWith("IN") ? Arrays.asList(test.raw(), null, " ") : test.raw();
                Map<String, Object> source = Map.of(test.field(), raw, test.field() + "_op", operator);
                var rendered = render(vendor, typed(source));
                assertEquals(List.of(test.value()), rendered.values(), vendor + "/" + operator);
                assertEquals(List.of(test.jdbc()), rendered.jdbcTypes());
                assertEquals(List.of(test.setter()), rendered.setters());
                assertEquals(raw, source.get(test.field()), "转换不能修改复用的请求条件");
            }
        }
    }

    @Test
    void rangesUseTheFieldTypeForBothAndSingleBoundsIncludingSystemTime() throws Exception {
        for (var vendor : DatabaseVendor.values()) {
            var range = render(vendor, typed(Map.of("amount_start", "1.25", "amount_end", "2.50")));
            assertEquals(List.of(new BigDecimal("1.25"), new BigDecimal("2.50")), range.values());
            assertEquals(List.of(JdbcType.DECIMAL, JdbcType.DECIMAL), range.jdbcTypes());
            assertTrue(range.sql().contains(">= ?")); assertTrue(range.sql().contains("<= ?"));
            assertEquals(List.of(LocalDate.of(2026, 9, 23)),
                    render(vendor, typed(Map.of("day_start", "2026-09-23"))).values());
            var end = render(vendor, typed(Map.of("createTime_end", "2026-09-23 10:20:30")));
            assertEquals(List.of(LocalDateTime.of(2026, 9, 23, 10, 20, 30)), end.values());
            assertEquals(List.of(JdbcType.TIMESTAMP), end.jdbcTypes());
        }
    }

    @Test
    void defaultStringLikeRemainsLikeWhileNumericDefaultRemainsEquality() throws Exception {
        for (var vendor : DatabaseVendor.values()) {
            for (Map<String, Object> condition : List.of(
                    Map.<String, Object>of("quantity", "12tail"),
                    Map.<String, Object>of("quantity", "12tail", "quantity_op", "LIKE"))) {
                var result = render(vendor, typed(condition));
                assertTrue(result.sql().contains(" LIKE ?"));
                assertEquals(List.of("%12tail%"), result.values());
                assertEquals(List.of(JdbcType.VARCHAR), result.jdbcTypes());
                assertEquals(List.of("setString"), result.setters());
            }
            var equal = render(vendor, typed(Map.of("quantity", 12L)));
            assertFalse(equal.sql().contains(" LIKE "));
            assertEquals(List.of(12), equal.values());
            assertEquals(List.of("setInt"), equal.setters());
        }
    }

    @Test
    void nullBlankAndEmptyCollectionSemanticsRemainUnchanged() throws Exception {
        Map<String, Object> absent = new LinkedHashMap<>();
        absent.put("quantity", null); absent.put("quantity_op", "EQ");
        absent.put("day_start", " "); absent.put("instant_end", null);
        assertTrue(render(DatabaseVendor.MYSQL, typed(absent)).values().isEmpty());
        var nullCheck = render(DatabaseVendor.MYSQL, typed(Map.of("quantity", true, "quantity_op", "IS_NULL")));
        assertTrue(nullCheck.sql().contains(" IS NULL")); assertTrue(nullCheck.values().isEmpty());
        for (Object empty : List.of(List.of(), new Object[]{null, " "}, ", ,")) {
            assertTrue(render(DatabaseVendor.MYSQL, typed(Map.of("quantity", empty, "quantity_op", "IN"))).sql().contains("AND 1 = 0"));
            assertTrue(render(DatabaseVendor.MYSQL, typed(Map.of("quantity", empty, "quantity_op", "NOT_IN"))).sql().contains("AND 1 = 1"));
        }
        for (Object list : List.of("1, 2", new Object[]{"1", null, " ", "2"}, Arrays.asList("1", null, "2"))) {
            assertEquals(List.of(1, 2), render(DatabaseVendor.MYSQL, typed(Map.of("quantity", list, "quantity_op", "IN"))).values());
        }
    }

    @Test
    void malformedOverflowAndTimezoneValuesFailBeforeBinding() {
        for (var test : List.of(
                Map.entry("quantity", "12tail"), Map.entry("quantity", "1.2"), Map.entry("quantity", "2147483648"),
                Map.entry("serial", "9223372036854775808"), Map.entry("amount", "NaN"), Map.entry("enabled", "2"),
                Map.entry("day", "2026-02-30"), Map.entry("instant", "2026-09-23T10:20:30+08:00"))) {
            for (String operator : List.of("EQ", "NE", "GT", "LT", "IN", "NOT_IN")) {
                assertThrows(IllegalArgumentException.class, () -> render(DatabaseVendor.MYSQL,
                        typed(Map.of(test.getKey(), test.getValue(), test.getKey() + "_op", operator))));
            }
            assertThrows(IllegalArgumentException.class, () -> render(DatabaseVendor.MYSQL,
                    typed(Map.of(test.getKey() + "_start", test.getValue()))));
            assertThrows(IllegalArgumentException.class, () -> render(DatabaseVendor.MYSQL,
                    typed(Map.of(test.getKey() + "_end", test.getValue()))));
        }
    }

    @Test
    void rawInternalMapsAreNotGuessedAndMetadataCannotBeForgedByKeys() throws Exception {
        var raw = render(DatabaseVendor.MYSQL, Map.of("quantity", "12", "quantity_op", "EQ"));
        assertEquals(List.of("12"), raw.values());
        assertEquals(Collections.singletonList(null), raw.jdbcTypes());
        assertEquals(List.of("setString"), raw.setters());
        assertThrows(IllegalArgumentException.class, () -> render(DatabaseVendor.MYSQL,
                typed(Map.of("unpublished", "12", "unpublished_op", "EQ"))));
    }

    @Test
    void permissionBindingsKeepNamespaceNullAndFailClosedConversion() {
        Map<String, Object> parameters = new LinkedHashMap<>(); parameters.put("permissionValue1", null);
        String bound = PermissionSqlParameters.bindScalar(parameters, "9007199254740993", SchemaType.Kind.LONG);
        assertEquals("#{permissionParameters.permissionValue2,jdbcType=BIGINT}", bound);
        assertEquals(9007199254740993L, parameters.get("permissionValue2"));
        assertEquals("#{permissionParameters.permissionValue3,jdbcType=DATE}",
                PermissionSqlParameters.bindScalar(parameters, null, SchemaType.Kind.DATE));
        assertTrue(parameters.containsKey("permissionValue3")); assertNull(parameters.get("permissionValue3"));
        var invalid = assertThrows(IllegalArgumentException.class,
                () -> PermissionSqlParameters.bindScalar(parameters, "12secret", SchemaType.Kind.INTEGER));
        assertEquals("权限比较值不符合字段类型 INTEGER", invalid.getMessage());
        assertEquals(3, parameters.size(), "失败转换不能向共享权限参数容器写入部分值");
    }

    @Test
    void longTextComparisonsUseFullClobBindingOnlyWhereRequired() throws Exception {
        String value = "x".repeat(40000) + "尾'部";
        for (var vendor : DatabaseVendor.values()) for (String field : List.of("body", "legacy")) {
            boolean clob = usesClob(vendor);
            for (var operator : Map.of("EQ", "=", "NE", "<>", "GT", ">", "LT", "<").entrySet()) {
                var result = render(vendor, typed(Map.of(field, value, field + "_op", operator.getKey())));
                assertEquals(List.of(value), result.values(), "全文必须经过实际框架处理器，不能只断言原参数还存在");
                assertEquals(List.of(clob ? JdbcType.CLOB : JdbcType.VARCHAR), result.jdbcTypes());
                assertEquals(List.of(clob ? "setCharacterStream" : "setString"), result.setters());
                assertEquals(clob, result.sql().contains("DBMS_LOB.COMPARE("));
                if (clob) assertTrue(result.sql().contains(") " + operator.getValue() + " 0"));
                assertFalse(result.sql().contains("SUBSTR")); assertFalse(result.sql().contains("TO_CHAR"));
                assertFalse(result.sql().contains(value));
            }
            var range = render(vendor, typed(Map.of(field + "_start", value, field + "_end", value + "z")));
            assertEquals(List.of(value, value + "z"), range.values());
            if (clob) { assertTrue(range.sql().contains(") >= 0")); assertTrue(range.sql().contains(") <= 0")); }
        }
    }

    @Test
    void clobListsUseGroupedThreeValuedComparisonsWithoutChangingInputNullFiltering() throws Exception {
        String first = "x".repeat(40000) + "A", second = "x".repeat(40000) + "B";
        for (var vendor : DatabaseVendor.values()) for (String operator : List.of("IN", "NOT_IN")) {
            var result = render(vendor, typed(Map.of("body", Arrays.asList(first, null, " ", second), "body_op", operator)));
            assertEquals(List.of(first, second), result.values());
            if (usesClob(vendor)) {
                assertTrue(result.sql().contains(" AND (DBMS_LOB.COMPARE("));
                assertTrue(result.sql().contains(operator.equals("IN") ? ") = 0 OR DBMS_LOB.COMPARE(" : ") <> 0 AND DBMS_LOB.COMPARE("));
                assertFalse(result.sql().contains(" IN ("));
                // NULL 列仍产生 UNKNOWN，不能为了 CLOB 兼容把未匹配空值纳入 NOT_IN。
                assertFalse(result.sql().contains("COALESCE")); assertFalse(result.sql().contains(" IS NULL"));
            } else {
                assertTrue(result.sql().contains(operator.equals("IN") ? " IN (?, ?)" : " NOT IN (?, ?)"));
            }
        }
    }

    @Test
    void clobHandlingDoesNotChangeLikeOrGuessTypesForRawInternalMaps() throws Exception {
        for (var vendor : DatabaseVendor.values()) {
            var like = render(vendor, typed(Map.of("body", "needle")));
            assertTrue(like.sql().contains(" LIKE ?")); assertFalse(like.sql().contains("DBMS_LOB.COMPARE"));
            assertEquals(List.of(JdbcType.VARCHAR), like.jdbcTypes());
            var untyped = render(vendor, Map.of("body", "needle", "body_op", "EQ"));
            assertFalse(untyped.sql().contains("DBMS_LOB.COMPARE"));
            assertEquals(List.of("setString"), untyped.setters());
        }
    }

    private static boolean usesClob(DatabaseVendor vendor) {
        return vendor == DatabaseVendor.ORACLE || vendor == DatabaseVendor.DM || vendor == DatabaseVendor.OCEANBASE_ORACLE;
    }

    private EntityQueryConditions typed(Map<String, Object> condition) {
        return EntityQueryConditions.fromPublishedFields(condition, List.of(
                field("quantity", "quantity", EntityField.FieldType.INTEGER), field("serial", "serial", EntityField.FieldType.LONG),
                field("amount", "amount", EntityField.FieldType.DECIMAL), field("enabled", "enabled", EntityField.FieldType.BOOLEAN),
                field("day", "day_value", EntityField.FieldType.DATE), field("instant", "instant_value", EntityField.FieldType.DATETIME),
                field("body", "body_column", EntityField.FieldType.TEXT), field("legacy", "legacy_column", EntityField.FieldType.MULTI_REFERENCE)));
    }

    private static EntityField field(String code, String column, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setDbColumnName(column); field.setFieldType(type); return field;
    }

    /** 渲染生产 Provider，再调用实际 ParameterHandler，检查的不仅是占位符字符串。 */
    private Rendered render(DatabaseVendor vendor, Map<String, Object> condition) throws Exception {
        var configuration = new Configuration(); configuration.setDatabaseId(vendor.name());
        var params = new LinkedHashMap<String, Object>(); params.put("tableName", "biz_typed_query"); params.put("condition", condition);
        var constructor = ProviderContext.class.getDeclaredConstructor(Class.class, java.lang.reflect.Method.class, String.class);
        constructor.setAccessible(true);
        var context = constructor.newInstance(Object.class, Object.class.getMethod("toString"), vendor.name());
        String sql = provider.selectByCondition(params, context);
        var source = new XMLLanguageDriver().createSqlSource(configuration, sql, Map.class);
        var statement = new MappedStatement.Builder(configuration, "typed-query-test", source, SqlCommandType.SELECT).build();
        var bound = statement.getBoundSql(params);
        var setters = new ArrayList<String>(); var values = new ArrayList<Object>();
        PreparedStatement recorder = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("set")) {
                        setters.add(method.getName());
                        if (args[1] instanceof Reader reader) {
                            var fullValue = new StringWriter(); reader.transferTo(fullValue);
                            values.add(fullValue.toString());
                            assertEquals(fullValue.toString().length(), ((Number) args[2]).intValue(), "框架必须声明完整字符长度");
                        } else {
                            values.add(args[1]);
                        }
                    }
                    return null;
                });
        configuration.newParameterHandler(statement, params, bound).setParameters(recorder);
        return new Rendered(bound.getSql(), values, bound.getParameterMappings().stream().map(mapping -> mapping.getJdbcType()).toList(), setters);
    }

    private record Rendered(String sql, List<Object> values, List<JdbcType> jdbcTypes, List<String> setters) { }
}
