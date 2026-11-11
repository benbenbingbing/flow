package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.extension.action.infrastructure.persistence.mapper.FlowActionDefinitionMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 扩展契约、代码阻断和旧包校验必须在 ID 撞号时保持同样行为，不能猜测目标主键。 */
class ConfigMigrationOpaqueReferencesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final ConfigMigrationReferenceService references = new ConfigMigrationReferenceService(users,
            mock(SysRoleMapper.class), mock(SysGroupMapper.class), mock(SysOrganizationMapper.class),
            mock(FlowActionDefinitionMapper.class), mock(FlowActionCatalogPort.class));

    @Test
    void declaredNestedParametersResolveOnTargetWithoutInstallingTheSourceProvider() throws Exception {
        sourceUser();
        references.configureParameterReferences(List.of((configuration, name) ->
                "notify".equals(configuration.get("actionCode"))
                        ? Map.of("/recipients", "USER", "/businessText", "VALUE") : Map.of()));
        Map<String, Object> source = Map.of("actionConfigDocument", json.writeValueAsString(Map.of(
                "actionCode", "notify", "paramsJson", "{\"recipients\":[\"source-u\"],\"businessText\":\"source-u\"}")));
        Map<String, Object> portable = references.exportReferences(source);
        assertTrue(portable.toString().contains("wf-ref://USER/alice"));
        assertTrue(portable.toString().contains(ConfigMigrationOpaqueReferences.PARAMETER_TYPES));
        assertFalse(source.toString().contains("wf-ref://"));
        references.configureParameterReferences(List.of());
        reset(users);
        when(users.selectByUsername("alice")).thenReturn(user("target-u", "alice"));
        Map<String, Object> target = references.importReferences(portable, (type, code) -> code);
        Map<String, Object> action = object(json.readValue((String) target.get("actionConfigDocument"), Map.class));
        Map<String, Object> params = object(json.readValue((String) action.get("paramsJson"), Map.class));
        assertEquals(List.of("target-u"), params.get("recipients"));
        assertEquals("source-u", params.get("businessText"));
        assertFalse(action.containsKey(ConfigMigrationOpaqueReferences.PARAMETER_TYPES));
        verify(users, never()).selectById(any());
    }

    @Test
    void undeclaredSourceDirectoryIdHasAnExactPointerAndActionableAdvice() {
        sourceUser();
        var error = assertThrows(ConfigMigrationValueReferences.ReferenceException.class,
                () -> references.exportReferences(Map.of("businessKey", "expense", "paramsJson",
                        "{\"nested\":{\"reviewers\":[\"source-u\"]}}")));
        assertTrue(error.getMessage().contains("paramsJson/nested/reviewers/0"));
        assertTrue(error.getMessage().contains("ConfigMigrationParameterReferenceProvider"));
        assertTrue(error.getMessage().contains("VALUE"));
        assertEquals("BLOCKING", error.risk().get("level"));
        assertFalse(error.risk().get("suggestion").toString().isBlank());
    }

    @Test
    void legacyStringNodeDocumentsAlsoInspectResolverParameters() throws Exception {
        sourceUser();
        String assignment = json.writeValueAsString(Map.of("resolverCode", "customResolver",
                "paramsJson", "{\"owner\":\"source-u\"}"));
        Map<String, Object> snapshot = Map.of("nodes", List.of(Map.of("nodeId", "review", "configJson",
                json.writeValueAsString(Map.of("assigneeConfig", assignment)))));
        var error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(snapshot));
        assertTrue(error.getMessage().contains("assigneeConfig.paramsJson/owner"));
        assertTrue(error.getMessage().contains("建议"));
    }

    @Test
    void sqlAndBpmnExpressionsBlockLiteralIdsWithoutChangingTheCode() {
        sourceUser();
        Map<String, Object> sql = Map.of("interfaceExtensions", List.of(Map.of("extensionKey", "owners", "sql",
                "select * from entity_expense where submitter_id = 'source-u'")));
        var error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(sql));
        assertTrue(error.getMessage().contains("interfaceExtensions[0].sql"));
        assertTrue(error.getMessage().contains("#{userId}"));
        String bpmn = ConfigMigrationAssignmentSupportTest.bpmn("<bpmn:sequenceFlow id=\"route\" sourceRef=\"start\" targetRef=\"end\">"
                + "<bpmn:conditionExpression>${owner == 'source-u'}</bpmn:conditionExpression></bpmn:sequenceFlow>");
        error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(Map.of("bpmnXml", bpmn)));
        assertTrue(error.getMessage().contains("[route].conditionExpression"));
        assertTrue(error.getMessage().contains("运行时参数"));
        assertTrue(bpmn.contains("'source-u'"));
    }

    @Test
    void dynamicSqlAndCodeBasedQueriesRemainUnchangedAfterSourceCheck() {
        sourceUser();
        String sql = "select * from entity_expense where submitter_id = #{userId} and code = 'stable-code'";
        Map<String, Object> snapshot = Map.of("sql", sql, "paramsJson", "{\"businessCode\":\"stable-code\"}");
        Map<String, Object> portable = references.exportReferences(snapshot);
        reset(users);
        Map<String, Object> target = references.importReferences(portable, (type, code) -> code);
        assertEquals(sql, target.get("sql"));
        assertEquals(snapshot.get("paramsJson"), target.get("paramsJson"));
        verifyNoInteractions(users);
    }

    @Test
    void oldUncheckedCodeAndParametersBlockWithReexportAdvice() {
        for (Map<String, Object> legacy : List.of(Map.<String, Object>of("sql", "select 1"),
                Map.<String, Object>of("paramsJson", "{\"owner\":\"source-u\"}"))) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> references.importReferences(legacy, (type, code) -> code));
            assertTrue(error.getMessage().contains("重新导出"));
            assertTrue(error.getMessage().contains("建议"));
        }
        verifyNoInteractions(users);
    }

    @Test
    void invalidAndOverlappingProviderPointersAreRejectedWithRepairSteps() {
        for (Map<String, String> declaration : List.of(Map.of("/missing", "USER"),
                Map.of("", "USER", "/owner", "USER"))) {
            references.configureParameterReferences(List.of((configuration, name) -> declaration));
            var error = assertThrows(IllegalArgumentException.class,
                    () -> references.exportReferences(Map.of("extraParams", Map.of("owner", "source-u"))));
            assertTrue(error.getMessage().contains("建议"));
            assertTrue(error.getMessage().contains("路径"));
        }
    }

    private void sourceUser() { when(users.selectById("source-u")).thenReturn(user("source-u", "alice")); }
    private SysUser user(String id, String name) { var value = new SysUser(); value.setId(id); value.setUsername(name); return value; }
}
