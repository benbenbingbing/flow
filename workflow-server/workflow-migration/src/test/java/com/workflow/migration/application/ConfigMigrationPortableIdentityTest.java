package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.extension.action.infrastructure.persistence.mapper.FlowActionDefinitionMapper;
import com.workflow.admin.extension.action.infrastructure.persistence.record.FlowActionDefinition;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用两套不同 ID 的目录验证导出→正文解析→目标物化，包含源 ID 撞号。 */
class ConfigMigrationPortableIdentityTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final SysGroupMapper groups = mock(SysGroupMapper.class);
    private final SysOrganizationMapper organizations = mock(SysOrganizationMapper.class);
    private final FlowActionDefinitionMapper actions = mock(FlowActionDefinitionMapper.class);
    private final FlowActionCatalogPort catalog = mock(FlowActionCatalogPort.class);
    private final ConfigMigrationReferenceService service = new ConfigMigrationReferenceService(users, roles, groups, organizations, actions, catalog);

    @Test
    void permissionTreesAndButtonConstantsResolveAllIdentityTypesByCode() throws Exception {
        SysUser source = user("source-u", "alice");
        when(users.selectById("source-u")).thenReturn(source);
        SysRole role = new SysRole(); role.setId("source-r"); role.setRoleCode("reviewer");
        when(roles.selectById("source-r")).thenReturn(role);
        SysGroup group = new SysGroup(); group.setId("source-g"); group.setGroupCode("finance");
        when(groups.selectById("source-g")).thenReturn(group);
        SysOrganization organization = new SysOrganization(); organization.setId("source-o"); organization.setOrgCode("HQ");
        when(organizations.selectById("source-o")).thenReturn(organization);
        Map<String, Object> input = Map.of(
                "scopeBindings", List.of(Map.of("matchConfig", json.writeValueAsString(Map.of("conditions", List.of(
                        scope("USER", "source-u"), scope("ROLE", "source-r"), scope("GROUP", "source-g"), scope("ORG", "source-o")))))),
                "scopePolicies", List.of(Map.of("filterConfig", json.writeValueAsString(Map.of("audience", Map.of("condition", scope("USER", "source-u")),
                        "root", Map.of("type", "USER_FIELD", "field", "orgId", "value", "source-o"))))),
                "lists", List.of(Map.of("toolbarConfig", json.writeValueAsString(List.of(Map.of("availabilityRule", Map.of(
                        "visibleWhen", Map.of("type", "USER_FIELD", "field", "id", "value", "source-u"),
                        "enabledWhen", Map.of("type", "USER_FIELD", "field", "roleIds", "value", List.of("source-r")))))))));
        Map<String, Object> portable = service.exportReferences(input);
        String encoded = json.writeValueAsString(portable);
        assertFalse(encoded.contains("source-u")); assertFalse(encoded.contains("source-r"));
        assertTrue(encoded.contains("wf-ref://USER/alice"));
        // 源 ID 在目标恰好属于其他用户；目标解析仍不能查询它。
        reset(users, roles, groups, organizations);
        when(users.selectById("source-u")).thenReturn(user("source-u", "intruder"));
        when(users.selectByUsername("alice")).thenReturn(user("target-u", "alice"));
        role.setId("target-r"); when(roles.selectOne(any())).thenReturn(role);
        group.setId("target-g"); when(groups.selectByGroupCode("finance")).thenReturn(group);
        organization.setId("target-o"); when(organizations.selectByCode("HQ")).thenReturn(organization);
        Map<String, Object> target = service.importReferences(portable, (type, key) -> key);
        String materialized = json.writeValueAsString(target);
        for (String id : List.of("target-u", "target-r", "target-g", "target-o")) assertTrue(materialized.contains(id));
        assertFalse(materialized.contains("source-u"));
        verify(users, never()).selectById(any()); verify(roles, never()).selectById(any());
        verify(groups, never()).selectById(any()); verify(organizations, never()).selectById(any());
        assertEquals(target, service.importReferences(portable, (type, key) -> key));
        verify(users, never()).insert(any(SysUser.class)); verify(roles, never()).insert(any(SysRole.class));
    }

    @Test
    void missingTargetAndUntypedIdsCannotFallBackToSourceId() {
        when(users.selectById("source-u")).thenReturn(user("source-u", "wrong"));
        assertThrows(IllegalArgumentException.class, () -> service.importReferences(
                Map.of("matchConfig", scope("USER", reference("USER", "missing"))), (type, key) -> key));
        assertThrows(IllegalArgumentException.class, () -> service.importReferences(
                Map.of("matchConfig", scope("USER", "source-u")), (type, key) -> key));
        verify(users, never()).selectById(any());
    }

    @Test
    void calendarOrganizationCannotFallBackToAnIdWhenItsCodeIsMissing() throws Exception {
        var importer = mock(ConfigMigrationImportApplyService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(importer, "objectMapper", json);
        ReflectionTestUtils.setField(importer, "organizationMapper", organizations);
        ReflectionTestUtils.setField(importer, "environmentMappingMapper", mock(com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper.class));
        var collision = new SysOrganization(); collision.setId("HQ"); collision.setOrgCode("OTHER");
        when(organizations.selectById("HQ")).thenReturn(collision);
        var item = new com.workflow.migration.infrastructure.persistence.record.ConfigImportItem();
        item.setSnapshotJson(json.writeValueAsString(Map.of("configuration", Map.of("calendarCode", "work",
                "bindings", List.of(Map.of("scopeType", "ORG", "scopeKey", "HQ"))))));
        var error = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(importer, "applyWorkCalendar", item, "test"));
        assertTrue(error.getMessage().contains("目标组织不存在"));
        verify(organizations, never()).selectById(any());
    }

    @Test
    void actionDefinitionIsSelectedByCodeEvenWhenSourceIdCollides() {
        FlowActionDefinition source = action("source-action", "SEND_NOTICE", "sourceHandler");
        when(actions.selectById("source-action")).thenReturn(source);
        Map<String, Object> portable = service.exportReferences(Map.of("flowActions", List.of(Map.of(
                "actionDefinitionId", "source-action", "interfaceName", "sourceHandler", "scopeType", "PROCESS"))));
        assertFalse(object(((List<?>) portable.get("flowActions")).get(0)).containsKey("actionDefinitionId"));
        reset(actions);
        when(actions.selectById("source-action")).thenReturn(action("source-action", "DELETE_ALL", "wrongHandler"));
        when(actions.selectOne(any())).thenReturn(action("target-action", "SEND_NOTICE", "targetHandler"));
        when(catalog.isConfiguredAndAvailable("targetHandler")).thenReturn(true);
        Map<String, Object> target = service.importReferences(portable, (type, key) -> key);
        Map<String, Object> saved = object(((List<?>) target.get("flowActions")).get(0));
        assertEquals("target-action", saved.get("actionDefinitionId"));
        assertEquals("targetHandler", saved.get("interfaceName"));
        verify(actions, never()).selectById(any()); verify(actions, never()).insert(any(FlowActionDefinition.class));
    }

    @Test
    void retiredTemplatesAreStrippedButCopiedPropertiesAndCustomParametersRemain() {
        Map<String, Object> source = Map.of("forms", List.of(Map.of("nodes", List.of(Map.of(
                "nodeKey", "amount", "templateId", "source-template", "templateVersion", 2,
                "propsDocument", "{\"label\":\"金额\",\"required\":true}")))),
                "paramsJson", "{\"recordId\":\"business-42\"}");
        Map<String, Object> result = service.exportReferences(source);
        Map<String, Object> node = object(((List<?>) object(((List<?>) result.get("forms")).get(0)).get("nodes")).get(0));
        assertFalse(node.containsKey("templateId")); assertFalse(node.containsKey("templateVersion"));
        assertEquals("{\"label\":\"金额\",\"required\":true}", node.get("propsDocument"));
        assertEquals(source.get("paramsJson"), result.get("paramsJson"));
    }

    @Test
    void nodeJsonAndBpmnCarryTypedReferencesForLegacyMultiCcAndFixedOperations() throws Exception {
        when(users.selectById("old-u")).thenReturn(user("old-u", "alice"));
        Map<String, Object> cc = Map.of("recipientRules", List.of(Map.of("type", "USER", "values", List.of("old-u"))));
        Map<String, Object> policy = Map.of("operations", Map.of("transfer", Map.of("targetScope", "FIXED", "targetIds", List.of("old-u"))));
        Map<String, Object> assignment = Map.of("multiInstanceUserIds", List.of("old-u"));
        String xml = ConfigMigrationAssignmentSupportTest.bpmn("<bpmn:userTask id=\"review\"><bpmn:extensionElements><flowable:properties>"
                + property("assigneeConfig", assignment) + property("ccConfig", cc) + property("nodeOperationPolicy", policy)
                + "</flowable:properties></bpmn:extensionElements></bpmn:userTask>");
        Map<String, Object> portable = service.exportReferences(Map.of("bpmnXml", xml, "nodes", List.of(Map.of(
                "nodeId", "review", "configJson", json.writeValueAsString(Map.of("assigneeConfig", assignment, "ccConfig", cc, "nodeOperationPolicy", policy))))));
        assertFalse(json.writeValueAsString(portable).contains("old-u"));
        when(users.selectByUsername("alice")).thenReturn(user("new-u", "alice"));
        ConfigMigrationImportApplyService importer = mock(ConfigMigrationImportApplyService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(importer, "referenceService", service);
        ReflectionTestUtils.setField(importer, "environmentMappingMapper", mock(com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper.class));
        String restored = ReflectionTestUtils.invokeMethod(importer, "resolvePortableBpmn", portable.get("bpmnXml"), Map.of());
        assertTrue(restored.contains("new-u")); assertFalse(restored.contains("wf-ref://"));
    }

    @Test
    void processAndStartEventCcAndStringNodeDocumentsAlsoResolveByCode() throws Exception {
        when(users.selectById("old-u")).thenReturn(user("old-u", "alice"));
        Map<String, Object> cc = Map.of("recipientRules", List.of(Map.of("type", "USER", "values", List.of("old-u"))));
        String properties = "<bpmn:extensionElements><flowable:properties>" + property("ccConfig", cc)
                + "</flowable:properties></bpmn:extensionElements>";
        String xml = ConfigMigrationAssignmentSupportTest.bpmn(properties
                + "<bpmn:startEvent id=\"start\">" + properties + "</bpmn:startEvent>");
        Map<String, Object> portable = service.exportReferences(Map.of("bpmnXml", xml, "nodes", List.of(Map.of(
                "nodeId", "start", "configJson", json.writeValueAsString(Map.of("ccConfig", json.writeValueAsString(cc)))))));
        assertFalse(json.writeValueAsString(portable).contains("old-u"));
        assertTrue(json.writeValueAsString(portable).contains("wf-ref://USER/alice"));
        when(users.selectByUsername("alice")).thenReturn(user("new-u", "alice"));
        service.validateAssignments(portable, (type, key) -> key);
        verify(users, atLeast(1)).selectByUsername("alice");
    }

    private String property(String name, Object value) throws Exception {
        return "<flowable:property name=\"" + name + "\" value=\"" + json.writeValueAsString(value).replace("\"", "&quot;") + "\"/>";
    }
    private Map<String, Object> scope(String type, String id) { return Map.of("scopeType", type, "targetIds", List.of(id)); }
    private SysUser user(String id, String username) { SysUser value = new SysUser(); value.setId(id); value.setUsername(username); return value; }
    private FlowActionDefinition action(String id, String code, String handler) {
        FlowActionDefinition value = new FlowActionDefinition(); value.setId(id); value.setActionCode(code); value.setHandlerName(handler); value.setEnabled(true); return value;
    }
}
