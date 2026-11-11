package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.extension.action.infrastructure.persistence.mapper.FlowActionDefinitionMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.setting.application.GlobalSettingService;
import com.workflow.contracts.entity.permission.model.EntityActionRule;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.permission.application.EntityActionRuleEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 使用两套目录及源 ID 撞号，验证默认值、条件求值和细粒度包的实际语义。 */
class ConfigMigrationValueReferencesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final SysOrganizationMapper organizations = mock(SysOrganizationMapper.class);
    private final ConfigMigrationReferenceService references = new ConfigMigrationReferenceService(users, roles,
            mock(SysGroupMapper.class), organizations, mock(FlowActionDefinitionMapper.class), mock(FlowActionCatalogPort.class));

    @Test
    void defaultsInEntityFormNodeAndListQueriesUseTargetIdsAndPreserveRepresentations() throws Exception {
        when(users.selectById("source-u")).thenReturn(user("source-u", "alice"));
        Map<String, Object> source = Map.of("businessKey", "expense", "fields", List.of(
                Map.of("fieldCode", "owner", "fieldType", "USER", "defaultValue", "source-u"),
                Map.of("fieldCode", "reviewers", "fieldType", "MULTI_REFERENCE", "refEntityType", "USER", "defaultValue", "[\"source-u\"]"),
                Map.of("fieldCode", "note", "fieldType", "STRING", "defaultValue", "source-u")),
                "forms", List.of(Map.of("formKey", "main", "fields", List.of(Map.of("fieldCode", "owner", "defaultValue", "source-u")),
                        "nodes", List.of(Map.of("nodeKey", "owner", "bindingType", "ENTITY_FIELD", "bindingRef", "owner",
                                "propsDocument", "{\"fieldCode\":\"owner\",\"defaultValue\":\"source-u\"}")))),
                "lists", List.of(Map.of("listKey", "default", "fixedFilterConfig", Map.of("owner", "source-u"),
                        "fields", List.of(Map.of("fieldCode", "owner", "queryConfig", "{\"defaultValue\":\"source-u\"}")))));
        String original = json.writeValueAsString(source);
        Map<String, Object> portable = references.exportReferences(source);
        assertEquals(original, json.writeValueAsString(source));
        assertEquals(portable, references.exportReferences(portable));
        reset(users);
        when(users.selectById("source-u")).thenReturn(user("source-u", "bob"));
        when(users.selectByUsername("alice")).thenReturn(user("target-u", "alice"));
        Map<String, Object> target = references.importReferences(portable, (type, key) -> key);
        List<Map<String, Object>> fields = ConfigMigrationReferenceService.maps(target.get("fields"));
        assertEquals("target-u", fields.get(0).get("defaultValue"));
        assertEquals("[\"target-u\"]", fields.get(1).get("defaultValue"));
        assertEquals("source-u", fields.get(2).get("defaultValue"));
        assertTrue(json.writeValueAsString(target.get("forms")).contains("target-u"));
        assertFalse(json.writeValueAsString(target.get("forms")).contains("source-u"));
        assertFalse(json.writeValueAsString(target.get("lists")).contains("source-u"));
        verify(users, never()).selectById(any());
    }

    @Test
    void fieldRulesKeepTheirTruthValueAcrossDifferentIdsIncludingSetOperators() throws Exception {
        when(users.selectById("source-u")).thenReturn(user("source-u", "alice"));
        when(users.selectByUsername("alice")).thenReturn(user("target-u", "alice"));
        EntityActionRuleEvaluator evaluator = new EntityActionRuleEvaluator(List.of());
        EntityDataDTO sourceRow = new EntityDataDTO(); sourceRow.setSubmitterId("source-u");
        EntityDataDTO targetRow = new EntityDataDTO(); targetRow.setSubmitterId("target-u");
        for (String operator : List.of("EQ", "NE", "IN", "NOT_IN")) {
            Map<String, Object> rule = Map.of("type", "FIELD", "field", "submitterId", "operator", operator, "value", "source-u");
            Map<String, Object> snapshot = Map.of("availabilityRule", Map.of("visibleWhen", rule));
            Map<String, Object> target = references.importReferences(references.exportReferences(snapshot), (type, key) -> key);
            var before = json.convertValue(rule, EntityActionRule.RuleNode.class);
            var after = json.convertValue(object(target.get("availabilityRule")).get("visibleWhen"), EntityActionRule.RuleNode.class);
            assertEquals(evaluator.evaluate(before, sourceRow, user("source-u", "alice"), null),
                    evaluator.evaluate(after, targetRow, user("target-u", "alice"), null), operator);
            assertEquals("target-u", after.getValue());
        }
    }

    @Test
    void departmentRoleAndFormVisibilityConstantsResolveUsingFieldMetadata() throws Exception {
        var department = new SysOrganization(); department.setId("source-d"); department.setOrgCode("HQ");
        var targetDepartment = new SysOrganization(); targetDepartment.setId("target-d"); targetDepartment.setOrgCode("HQ");
        when(organizations.selectById("source-d")).thenReturn(department);
        when(organizations.selectByCode("HQ")).thenReturn(targetDepartment);
        var role = new SysRole(); role.setId("source-r"); role.setRoleCode("reviewer");
        var targetRole = new SysRole(); targetRole.setId("target-r"); targetRole.setRoleCode("reviewer");
        when(roles.selectById("source-r")).thenReturn(role);
        when(roles.selectOne(any())).thenReturn(targetRole);
        Map<String, Object> source = Map.of("fields", List.of(
                Map.of("fieldCode", "team", "fieldType", "DEPT", "defaultValue", "source-d"),
                Map.of("fieldCode", "role", "fieldType", "REFERENCE", "refEntityType", "ROLE", "defaultValue", "source-r")),
                "forms", List.of(Map.of("nodes", List.of(Map.of("nodeKey", "condition", "propsDocument", json.writeValueAsString(Map.of(
                        "visibleWhen", Map.of("version", 1, "root", Map.of("type", "CONDITION", "property", "team", "operator", "==", "value", "source-d")))))))));
        Map<String, Object> target = references.importReferences(references.exportReferences(source), (type, key) -> key);
        String serialized = json.writeValueAsString(target);
        assertTrue(serialized.contains("target-d")); assertTrue(serialized.contains("target-r"));
        assertFalse(serialized.contains("source-d")); assertFalse(serialized.contains("source-r"));
    }

    @Test
    void fineGrainedUiExportRetainsReferenceMetadataEvenWhenFieldsAreOmitted() {
        when(users.selectById("source-u")).thenReturn(user("source-u", "alice"));
        when(users.selectByUsername("alice")).thenReturn(user("target-u", "alice"));
        Map<String, Object> portable = references.exportReferences(Map.of("assetType", "ENTITY", "businessKey", "expense",
                "definition", Map.of("entityCode", "expense"),
                "fields", List.of(Map.of("fieldCode", "owner", "fieldType", "USER")),
                "lists", List.of(Map.of("listKey", "default", "toolbarConfig", List.of(Map.of("availabilityRule", Map.of(
                        "visibleWhen", Map.of("type", "FIELD", "field", "owner", "operator", "EQ", "value", "source-u"))))))));
        ConfigMigrationPackageCodec codec = new ConfigMigrationPackageCodec(json, mock(GlobalSettingService.class));
        Map<String, Object> selected = codec.selectSnapshot(portable, Map.of("full", false, "sections", List.of("lists")));
        assertFalse(selected.containsKey("fields"));
        assertEquals("USER", object(selected.get(ConfigMigrationValueReferences.FIELD_TYPES)).get("owner"));
        assertTrue(jsonString(references.importReferences(selected, (type, key) -> key)).contains("target-u"));
        assertTrue(ConfigMigrationReferenceService.maps(selected.get("dependencies")).stream()
                .anyMatch(value -> "USER".equals(value.get("type")) && "alice".equals(value.get("key"))));
    }

    @Test
    void legacyRawDefaultsMissingTargetsAndCustomRecordsFailWithLocationAndAdvice() {
        Map<String, Object> legacy = Map.of("fields", List.of(Map.of("fieldCode", "owner", "fieldType", "USER", "defaultValue", "source-u")));
        var error = assertThrows(IllegalArgumentException.class, () -> references.importReferences(legacy, (type, key) -> key));
        assertTrue(error.getMessage().contains("defaultValue")); assertTrue(error.getMessage().contains("建议"));
        Map<String, Object> missing = Map.of("fields", List.of(Map.of("fieldCode", "owner", "fieldType", "USER", "defaultValue", reference("USER", "alice"))));
        error = assertThrows(IllegalArgumentException.class, () -> references.importReferences(missing, (type, key) -> key));
        assertTrue(error.getMessage().contains("编码映射"));
        Map<String, Object> record = Map.of("fields", List.of(Map.of("fieldCode", "customer", "fieldType", "REFERENCE", "refEntityType", "CUSTOM",
                "refEntityCode", "customer", "defaultValue", "record-1")));
        error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(record));
        assertTrue(error.getMessage().contains("唯一业务键")); assertTrue(error.getMessage().contains("动态默认值"));
        verify(users, never()).selectById(any());
    }

    @Test
    void nullFiltersAndPlainSystemValuesRemainLiteralButUnknownOrOrderDependentIdsBlock() {
        Map<String, Object> source = Map.of("fixedFilterConfig", Map.of("submitterId", true, "submitterId_op", "IS_NULL"),
                "availabilityRule", Map.of("type", "FIELD", "field", "status", "operator", "EQ", "value", "APPROVED"));
        Map<String, Object> target = references.importReferences(references.exportReferences(source), (type, key) -> key);
        assertEquals(source.get("fixedFilterConfig"), target.get("fixedFilterConfig"));
        assertEquals(source.get("availabilityRule"), target.get("availabilityRule"));
        for (Map<String, Object> unsafe : List.of(
                Map.<String, Object>of("forms", List.of(Map.of("fields", List.of(Map.of("fieldCode", "owner", "defaultValue", "old-id"))))),
                Map.<String, Object>of("fixedFilterConfig", Map.of("unknown", "old-id")),
                Map.<String, Object>of("fixedFilterConfig", Map.of("submitterId_start", "old-id")),
                Map.<String, Object>of("availabilityRule", Map.of("type", "FIELD", "field", "submitterId", "operator", "GT", "value", "old-id")))) {
            var error = assertThrows(IllegalArgumentException.class, () -> references.importReferences(unsafe, (type, key) -> key));
            assertTrue(error.getMessage().contains("建议"));
        }
        verifyNoInteractions(users);
    }

    @Test
    void legacyFieldNodePlaceholderUsesEntityReferenceTypeIncludingStringBackedSystemIds() {
        when(users.selectById("source-u")).thenReturn(user("source-u", "alice"));
        when(users.selectByUsername("alice")).thenReturn(user("target-u", "alice"));
        Map<String, Object> source = Map.of("fields", List.of(Map.of("fieldCode", "create_by", "fieldType", "STRING")),
                "forms", List.of(Map.of("nodes", List.of(Map.of("bindingType", "ENTITY_FIELD", "bindingRef", "create_by",
                        "fieldType", "FIELD", "propsDocument", "{\"fieldType\":\"STRING\",\"defaultValue\":\"source-u\"}")))));
        Map<String, Object> target = references.importReferences(references.exportReferences(source), (type, key) -> key);
        assertTrue(jsonString(target.get("forms")).contains("target-u"));
        assertFalse(jsonString(target.get("forms")).contains("source-u"));
    }

    private String jsonString(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private SysUser user(String id, String name) { var user = new SysUser(); user.setId(id); user.setUsername(name); return user; }
}
