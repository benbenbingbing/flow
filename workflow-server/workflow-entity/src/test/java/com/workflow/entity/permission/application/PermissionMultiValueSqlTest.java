package com.workflow.entity.permission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.api.response.*;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeDelegationMapper;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证权限存储分类、参数绑定及真实 ALLOW/DENY 失败关闭路径，不为侧表字段伪造主表列。 */
class PermissionMultiValueSqlTest {
    private static final String SPECIAL = "李'\\%_! #{input} ${input}";

    @ParameterizedTest
    @EnumSource(DatabaseVendor.class)
    void everyDialectUsesCorrelatedSideTableAndBoundValues(DatabaseVendor vendor) {
        var builder = builder(vendor, List.of(reference("reviewers", "reviewer_alias", "users")));
        var parameters = new LinkedHashMap<String, Object>();
        var filter = rule("reviewer_alias", "CONTAINS", SPECIAL);
        builder.validateFilter("asset", filter);
        String sql = builder.buildFilterSql("asset", filter, user(), parameters);
        var dialect = DatabaseQueryDialects.forVendor(vendor);
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM " + dialect.quoteIdentifier("biz_multi_permission_multi")));
        assertFalse(sql.contains(dialect.quoteIdentifier("reviewer_alias")));
        assertFalse(sql.contains(SPECIAL));
        assertEquals(List.of("reviewers", "users", SPECIAL), new ArrayList<>(parameters.values()));
        assertTrue(sql.contains(dialect.quoteIdentifier("target_entity_id")));
        assertTrue(sql.contains(dialect.quoteIdentifier("deleted") + " = 0"));
        var configuration = new Configuration();
        var source = new XMLLanguageDriver().createSqlSource(configuration,
                "SELECT id FROM biz_multi_permission WHERE " + sql, Map.class);
        var bound = source.getBoundSql(Map.of("permissionParameters", parameters));
        assertEquals(3, bound.getParameterMappings().size());
        for (var binding : bound.getParameterMappings()) {
            assertNotNull(configuration.newMetaObject(Map.of("permissionParameters", parameters)).getValue(binding.getProperty()));
        }
    }

    @Test
    void everySupportedCollectionOperatorKeepsExistencePolarity() {
        var builder = builder(DatabaseVendor.MYSQL, List.of(reference("reviewers", "reviewers", "users")));
        for (String operator : List.of("EQ", "NE", "IN", "NOT_IN", "CONTAINS", "NOT_CONTAINS", "EMPTY", "NOT_EMPTY")) {
            Object value = Set.of("EMPTY", "NOT_EMPTY").contains(operator) ? null
                    : Set.of("IN", "NOT_IN").contains(operator) ? List.of("u", "other") : "u";
            var filter = rule("reviewers", operator, value);
            builder.validateFilter("asset", filter);
            String sql = builder.buildFilterSql("asset", filter, user());
            boolean negative = Set.of("NE", "NOT_IN", "NOT_CONTAINS", "EMPTY").contains(operator);
            assertTrue(sql.startsWith(negative ? "NOT EXISTS (" : "EXISTS ("), operator);
            assertEquals(!Set.of("EMPTY", "NOT_EMPTY").contains(operator), sql.contains("target_record_id"), operator);
        }
    }

    @Test
    void membersAreTrimmedAndDeduplicatedWithoutChangingScalarValues() {
        var builder = builder(DatabaseVendor.MYSQL, List.of(reference("reviewers", "reviewers", "users"),
                field("owner", "owner", EntityField.FieldType.USER)));
        for (String operator : List.of("EQ", "NE", "CONTAINS", "NOT_CONTAINS", "IN", "NOT_IN")) {
            Object value = Set.of("IN", "NOT_IN").contains(operator) ? List.of(" u ", "\tother\n", "u") : " u ";
            var filter = rule("reviewers", operator, value); builder.validateFilter("asset", filter);
            var parameters = new LinkedHashMap<String, Object>();
            builder.buildFilterSql("asset", filter, user(), parameters);
            assertEquals(Set.of("IN", "NOT_IN").contains(operator) ? List.of("reviewers", "users", "u", "other")
                    : List.of("reviewers", "users", "u"), new ArrayList<>(parameters.values()), operator);
        }
        var parameters = new LinkedHashMap<String, Object>();
        builder.buildFilterSql("asset", rule("owner", "EQ", " u "), user(), parameters);
        assertEquals(List.of(" u "), new ArrayList<>(parameters.values()), "标量字符串不跟随成员规范化");
        assertInvalid(builder, rule("reviewers", "NOT_CONTAINS", "\u0000"));
    }

    @Test
    void scalarUserDepartmentAndReferenceMappingsResolveBothAliases() {
        for (var type : List.of(EntityField.FieldType.USER, EntityField.FieldType.DEPT, EntityField.FieldType.REFERENCE)) {
            var field = field("ownerCode", "owner_ref", type);
            var builder = builder(DatabaseVendor.MYSQL, List.of(field));
            for (String alias : List.of("ownerCode", "owner_ref")) {
                var personal = mapping("PERSONAL", alias);
                builder.validateFilter("asset", personal);
                assertTrue(builder.buildFilterSql("asset", personal, user()).startsWith("`owner_ref` IN"));
                var department = mapping("DEPT", alias);
                builder.validateFilter("asset", department);
                assertTrue(builder.buildFilterSql("asset", department, user()).startsWith("`owner_ref` ="));
            }
        }
    }

    @Test
    void componentsIncompleteStorageConflictsAndUndefinedComparisonsAreRejected() {
        for (var type : List.of(EntityField.FieldType.SUB_FORM, EntityField.FieldType.SUB_LIST)) {
            assertInvalid(builder(DatabaseVendor.MYSQL, List.of(field("component", "component", type))), rule("component", "EMPTY", null));
        }
        assertInvalid(builder(DatabaseVendor.MYSQL, List.of(reference("broken", "broken", null))), rule("broken", "NOT_EMPTY", null));
        var builder = builder(DatabaseVendor.MYSQL, List.of(reference("reviewers", "reviewers", "users")));
        for (String op : List.of("GT", "GTE", "LT", "LTE")) assertInvalid(builder, rule("reviewers", op, "u"));
        assertInvalid(builder, rule("reviewers", "NOT_IN", Arrays.asList("u", null)));
        assertInvalid(builder, rule("reviewers", "NOT_IN", List.of("u", "")));
        var conflicting = builder(DatabaseVendor.MYSQL, List.of(field("owner", "same", EntityField.FieldType.USER),
                reference("same", "other", "users")));
        assertInvalid(conflicting, rule("owner", "EMPTY", null));
        assertInvalid(builder, mapping("PERSONAL", "not_a_field"));
    }

    @Test
    void invalidNonPhysicalDenyCannotDisappearBehindAnAllowAllRule() {
        var builder = builder(DatabaseVendor.MYSQL, List.of(field("component", "component", EntityField.FieldType.SUB_FORM),
                reference("reviewers", "reviewers", "users")));
        for (var invalid : List.of(rule("component", "EMPTY", null), rule("reviewers", "LT", "u"),
                rule("reviewers", "NOT_IN", Arrays.asList("u", null)), mapping("DEPT", "not_a_field"), rule("unknown", "EMPTY", null))) {
            for (String effect : List.of("ALLOW", "DENY")) {
                var scopeService = mock(EntityListScopeService.class);
                var matcher = mock(PermissionRuleMatcher.class);
                when(matcher.matches(any(), any())).thenReturn(true);
                var snapshot = new EntityListScopeSnapshotDTO(); snapshot.setEntityCode("asset"); snapshot.setVersion(1);
                var policy = new EntityListScopePolicyDTO(); policy.setId("bad"); policy.setEnabled(1); policy.setFilterConfig(invalid);
                var binding = new EntityListScopeBindingDTO(); binding.setPolicyId("bad"); binding.setEnabled(1);
                binding.setListKey("default"); binding.setRuleEffect(effect);
                snapshot.setPolicies(List.of(policy)); snapshot.setBindings(List.of(binding));
                snapshot.setListModes(Map.of("default", "INHERIT")); snapshot.setSecureDefaultsVersion(1);
                var defaults = new EntityListScopeDefaultDTO(); defaults.setListKey("default"); defaults.setEnforcementMode("ENFORCE");
                defaults.setUnboundPolicy("EXPLICIT_ALL"); defaults.setConfirmed(true);
                snapshot.getListDefaults().put("default", defaults);
                when(scopeService.getActiveSnapshot("asset")).thenReturn(snapshot);
                var engine = new DataPermissionEngine(scopeService, mock(EntityListScopeDelegationMapper.class), new ObjectMapper(), matcher,
                        builder, mock(SysUserService.class), mock(EntityListScopeAuditService.class));
                var result = engine.calculatePermission("asset", "default", user());
                assertFalse(result.isHasPermission(), effect + " " + invalid.getType());
                assertEquals("1=0", result.getSqlCondition());
            }
        }
    }

    private static void assertInvalid(PermissionSqlBuilder builder, FilterConfigDTO filter) {
        assertThrows(IllegalArgumentException.class, () -> builder.validateFilter("asset", filter));
        assertThrows(IllegalArgumentException.class, () -> builder.buildFilterSql("asset", filter, user()));
    }

    private static PermissionSqlBuilder builder(DatabaseVendor vendor, List<EntityField> values) {
        var definitions = mock(EntityDefinitionMapper.class); var fields = mock(EntityFieldMapper.class);
        var entity = new EntityDefinition(); entity.setId("entity"); entity.setEntityCode("asset"); entity.setPhysicalTableName("biz_multi_permission");
        when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(entity));
        when(fields.findByEntityId("entity")).thenReturn(values);
        return new PermissionSqlBuilder(definitions, fields, null, List.of(), DatabaseQueryDialects.forVendor(vendor));
    }

    private static EntityField field(String code, String column, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setDbColumnName(column); field.setFieldType(type); return field;
    }
    private static EntityField reference(String code, String column, String target) {
        var field = field(code, column, EntityField.FieldType.MULTI_REFERENCE); field.setRefEntityId(target); return field;
    }
    private static SysUser user() { var user = new SysUser(); user.setId("u"); user.setDeptId("d"); return user; }
    private static FilterConfigDTO rule(String field, String operator, Object value) {
        var filter = new FilterConfigDTO(); filter.setType("RULE"); var node = new EntityActionRuleDTO.RuleNode();
        node.setType("FIELD"); node.setField(field); node.setOperator(operator); node.setValue(value); filter.setRoot(node); return filter;
    }
    private static FilterConfigDTO mapping(String type, String field) {
        var filter = new FilterConfigDTO(); filter.setType(type); var mapping = new FilterConfigDTO.FieldMappingDTO();
        mapping.setUserField(field); mapping.setDeptField(field); filter.setFieldMapping(mapping); return filter;
    }
}
