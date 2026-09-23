package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.schema.SchemaType;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.ClobTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 权限实际编译入口使用全文 LOB 方言；比较值始终保留 String，SQL NULL 不转成空 LOB。 */
class PermissionLobComparisonTest {
    @ParameterizedTest
    @EnumSource(DatabaseVendor.class)
    void equalityAndRangeKeepTheEntireStringAndFrameworkBinding(DatabaseVendor vendor) {
        var builder = builder(vendor); String value = "长文本".repeat(20000) + "'末尾不同";
        boolean clob = "CLOB".equals(DatabaseQueryDialects.forVendor(vendor).comparisonJdbcType(SchemaType.Kind.TEXT));
        for (String operator : List.of("EQ", "NE", "GT", "GTE", "LT", "LTE")) {
            var parameters = new LinkedHashMap<String, Object>();
            String sql = builder.buildFilterSql("asset", rule(operator, value), user(), parameters);
            assertEquals(value, parameters.get("permissionValue0"));
            assertFalse(sql.contains(value)); assertFalse(sql.contains("SUBSTR"));
            assertEquals(clob, sql.contains("DBMS_LOB.COMPARE"), vendor + " " + sql);
            var source = new XMLLanguageDriver().createSqlSource(new Configuration(), "SELECT id FROM sample WHERE " + sql, Map.class);
            var bindings = source.getBoundSql(Map.of("permissionParameters", parameters)).getParameterMappings();
            assertEquals(1, bindings.size());
            assertEquals(clob ? JdbcType.CLOB : JdbcType.VARCHAR, bindings.get(0).getJdbcType());
            if (clob) assertInstanceOf(ClobTypeHandler.class, bindings.get(0).getTypeHandler());
        }
    }

    @ParameterizedTest
    @EnumSource(DatabaseVendor.class)
    void setsKeepNullElementsAndCorrectBooleanConnectors(DatabaseVendor vendor) {
        var builder = builder(vendor); var dialect = DatabaseQueryDialects.forVendor(vendor);
        boolean clob = "CLOB".equals(dialect.comparisonJdbcType(SchemaType.Kind.TEXT));
        for (String operator : List.of("IN", "NOT_IN")) {
            var parameters = new LinkedHashMap<String, Object>();
            String sql = builder.buildFilterSql("asset", rule(operator, Arrays.asList("full-value", null)), user(), parameters);
            assertEquals(2, parameters.size()); assertEquals("full-value", parameters.get("permissionValue0"));
            assertTrue(parameters.containsKey("permissionValue1")); assertNull(parameters.get("permissionValue1"));
            assertFalse(sql.contains("EMPTY_CLOB"));
            if (clob) {
                assertEquals(2, sql.split("DBMS_LOB.COMPARE", -1).length - 1);
                assertTrue(sql.contains(operator.equals("IN") ? " OR " : " AND "));
                assertEquals(2, sql.split(operator.equals("IN") ? " = 0" : " <> 0", -1).length - 1);
            } else {
                assertTrue(sql.contains(operator.equals("IN") ? " IN (" : " NOT IN ("));
            }
            var source = new XMLLanguageDriver().createSqlSource(new Configuration(), "SELECT id FROM sample WHERE " + sql, Map.class);
            var bindings = source.getBoundSql(Map.of("permissionParameters", parameters)).getParameterMappings();
            assertEquals(2, bindings.size());
            assertTrue(bindings.stream().allMatch(binding -> binding.getJdbcType() == (clob ? JdbcType.CLOB : JdbcType.VARCHAR)));
        }
        for (String operator : List.of("EQ", "NE")) {
            var parameters = new LinkedHashMap<String, Object>();
            String sql = builder.buildFilterSql("asset", rule(operator, null), user(), parameters);
            assertEquals(dialect.quoteIdentifier("body") + (operator.equals("EQ") ? " IS NULL" : " IS NOT NULL"), sql);
            assertTrue(parameters.isEmpty());
        }
    }

    private static PermissionSqlBuilder builder(DatabaseVendor vendor) {
        var definitions = mock(EntityDefinitionMapper.class); var fields = mock(EntityFieldMapper.class);
        var entity = new EntityDefinition(); entity.setId("entity");
        var field = new EntityField(); field.setFieldCode("body"); field.setDbColumnName("body"); field.setFieldType(EntityField.FieldType.TEXT);
        when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(entity)); when(fields.findByEntityId("entity")).thenReturn(List.of(field));
        return new PermissionSqlBuilder(definitions, fields, null, List.of(), DatabaseQueryDialects.forVendor(vendor));
    }
    private static FilterConfigDTO rule(String operator, Object value) {
        var filter = new FilterConfigDTO(); filter.setType("RULE"); var node = new EntityActionRuleDTO.RuleNode();
        node.setType("FIELD"); node.setField("body"); node.setOperator(operator); node.setValue(value); filter.setRoot(node); return filter;
    }
    private static SysUser user() { var user = new SysUser(); user.setId("u"); return user; }
}
