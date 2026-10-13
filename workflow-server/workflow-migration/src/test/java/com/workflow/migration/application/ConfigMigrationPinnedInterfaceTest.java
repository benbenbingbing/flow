package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.admin.setting.application.GlobalSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 历史表单的冻结接口必须独立于当前可编辑接口配置；本地修订号不用于匹配。 */
class ConfigMigrationPinnedInterfaceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ConfigMigrationAssetService assets = mock(ConfigMigrationAssetService.class, CALLS_REAL_METHODS);

    ConfigMigrationPinnedInterfaceTest() {
        ReflectionTestUtils.setField(assets, "objectMapper", json);
    }

    @Test
    void frozenInterfaceOverridesLaterDraftButKeepsPortableVersionIdentity() throws Exception {
        Map<String, Object> current = Map.of("extensionKey", "expense.submit", "version", 3,
                "snapshotVersion", 2, "status", "DISABLED", "implementationConfigDocument", "{\"mode\":\"new\"}");
        Map<String, Object> form = Map.of("_inheritedEventBindings", List.of(Map.of("steps", List.of(step("old")))));
        var result = assets.pinnedInterfaceDefinitions(form, List.of(current)).get(0);
        assertEquals("{\"mode\":\"old\"}", result.get("implementationConfigDocument"));
        assertEquals(3, result.get("version"));
        assertEquals(2, result.get("snapshotVersion"));
        assertEquals("ACTIVE", result.get("status"));
        assertFalse(result.containsKey("scopeId"));
        assertEquals("{\"mode\":\"new\"}", current.get("implementationConfigDocument"));
    }

    @Test
    void conflictingFrozenImplementationsCannotSilentlyPickOne() throws Exception {
        var form = Map.<String, Object>of("eventBindings", List.of(Map.of("steps", List.of(step("a"), step("b")))));
        assertThrows(IllegalArgumentException.class, () -> assets.pinnedInterfaceDefinitions(form,
                List.of(Map.of("extensionKey", "expense.submit"))));
    }

    @Test
    void exportedInterfaceIncludesVersionInsteadOfImportingAsVersionOne() {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("source-id"); definition.setExtensionType("INTERFACE");
        definition.setExtensionKey("expense.submit"); definition.setVersion(3); definition.setSnapshotVersion(2);
        definition.setScopeType("GLOBAL");
        UiExtensionDefinitionMapper mapper = mock(UiExtensionDefinitionMapper.class);
        when(mapper.selectById("source-id")).thenReturn(definition);
        ReflectionTestUtils.setField(assets, "extensionDefinitionMapper", mapper);
        List<Map<String, Object>> exported = ReflectionTestUtils.invokeMethod(assets, "interfaceExtensionSnapshots",
                Set.of("source-id"), "entity-id", "expense");
        assertEquals(3, exported.get(0).get("version"));
        assertEquals(2, exported.get(0).get("snapshotVersion"));
        assertFalse(exported.get(0).containsKey("id"));
    }

    @Test
    void exportsAllEntityScopesByCodeAndRecordsTheirDependencies() {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("source-interface"); definition.setExtensionType("INTERFACE");
        definition.setExtensionKey("shared.options"); definition.setScopeType("ENTITY");
        definition.setScopeEntityIds(List.of("source-order", "source-customer"));
        var interfaces = mock(UiExtensionDefinitionMapper.class);
        var entities = mock(EntityDefinitionMapper.class);
        when(interfaces.selectById("source-interface")).thenReturn(definition);
        for (String code : List.of("order", "customer")) {
            var entity = new EntityDefinition(); entity.setId("source-" + code); entity.setEntityCode(code);
            when(entities.selectById(entity.getId())).thenReturn(entity);
        }
        ReflectionTestUtils.setField(assets, "extensionDefinitionMapper", interfaces);
        ReflectionTestUtils.setField(assets, "entityMapper", entities);
        List<Map<String, Object>> exported = ReflectionTestUtils.invokeMethod(assets, "interfaceExtensionSnapshots",
                Set.of("source-interface"), "source-order", "order");
        assertEquals(List.of("customer", "order"), exported.get(0).get("scopeEntityRefs"));
        assertFalse(exported.toString().contains("source-"));
        var dependencies = new java.util.ArrayList<Map<String, Object>>();
        ReflectionTestUtils.invokeMethod(assets, "collectExtensionDependencies", exported, dependencies);
        assertEquals(Set.of("customer", "order"), dependencies.stream()
                .map(value -> value.get("key")).collect(java.util.stream.Collectors.toSet()));
        dependencies.clear();
        ReflectionTestUtils.invokeMethod(assets, "collectExtensionDependencies", Map.of(
                "assetType", "ENTITY", "businessKey", "order", "interfaceExtensions", exported), dependencies);
        assertEquals(List.of("customer"), dependencies.stream().map(value -> value.get("key")).toList(),
                "自身范围不能将细粒度导出扩大成整实体，但另一范围实体必须作为依赖保留");
    }

    @Test
    void missingScopeEntityStopsExportInsteadOfDroppingItsRestriction() {
        var entities = mock(EntityDefinitionMapper.class);
        ReflectionTestUtils.setField(assets, "entityMapper", entities);
        var executable = Map.<String, Object>of("extensionKey", "shared.options", "scopeType", "ENTITY",
                "scopeEntityIds", List.of("missing-source-entity"), "implementationType", "HTTP");
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(assets,
                "rewriteInterfaceReferences", executable, Map.of()));
    }

    @Test
    void frozenMultiEntityScopesContainOnlySortedPortableCodes() throws Exception {
        var entities = mock(EntityDefinitionMapper.class);
        ReflectionTestUtils.setField(assets, "entityMapper", entities);
        for (String code : List.of("order", "customer")) {
            var entity = new EntityDefinition(); entity.setId("source-" + code); entity.setEntityCode(code);
            when(entities.selectById(entity.getId())).thenReturn(entity);
        }
        var executable = Map.<String, Object>of("extensionKey", "expense.submit", "scopeType", "ENTITY",
                "scopeEntityIds", List.of("source-order", "source-customer"), "implementationType", "HTTP");
        var step = Map.of("executableSnapshot", json.writeValueAsString(executable));
        var portable = ReflectionTestUtils.<Map<String, Object>>invokeMethod(assets,
                "rewriteInterfaceReferences", step, Map.of());
        var scope = json.readValue((String) portable.get("executableSnapshot"), Map.class);
        assertEquals(List.of("customer", "order"), scope.get("scopeEntityRefs"));
        assertFalse(portable.toString().contains("source-"));
        assertFalse(scope.containsKey("scopeEntityIds"));
    }

    @Test
    void frozenLegacyFormScopeExportsAtEntityLevelAndRemovesLocalIds() throws Exception {
        var entities = mock(EntityDefinitionMapper.class);
        var forms = mock(EntityFormMapper.class);
        var entity = new EntityDefinition(); entity.setId("source-entity"); entity.setEntityCode("order");
        var form = new EntityForm(); form.setId("source-form"); form.setEntityId(entity.getId());
        when(forms.selectById("source-form")).thenReturn(form);
        when(entities.selectById("source-entity")).thenReturn(entity);
        ReflectionTestUtils.setField(assets, "entityMapper", entities);
        ReflectionTestUtils.setField(assets, "formMapper", forms);
        var executable = Map.<String, Object>of("extensionKey", "expense.submit", "scopeType", "FORM",
                "scopeId", "source-form", "implementationType", "HTTP");
        var step = Map.of("executableSnapshot", json.writeValueAsString(executable));
        var portable = ReflectionTestUtils.<Map<String, Object>>invokeMethod(assets,
                "rewriteInterfaceReferences", step, Map.of());
        assertFalse(portable.toString().contains("source-form"));
        var frozen = assets.pinnedInterfaceDefinitions(Map.of("eventBindings", List.of(
                Map.of("steps", List.of(portable)))), List.of(Map.of("extensionKey", "expense.submit"))).get(0);
        assertEquals("ENTITY", frozen.get("scopeType"));
        assertEquals(List.of("order"), frozen.get("scopeEntityRefs"));
        assertFalse(frozen.containsKey("scopeId"));
    }

    @Test
    void actualPinnedExportUsesReleasedFormAndFrozenInterfaceInsteadOfDrafts() throws Exception {
        // 使用真实快照构建器和分区选择器，只隔离数据库读操作。
        for (var field : ConfigMigrationAssetService.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || !java.lang.reflect.Modifier.isFinal(field.getModifiers())) continue;
            ReflectionTestUtils.setField(assets, field.getName(), mock(field.getType()));
        }
        ReflectionTestUtils.setField(assets, "objectMapper", json);
        ReflectionTestUtils.setField(assets, "packageCodec", new ConfigMigrationPackageCodec(json, mock(GlobalSettingService.class)));
        var entity = new EntityDefinition(); entity.setId("source-e"); entity.setEntityCode("expense");
        var form = new EntityForm(); form.setId("source-f"); form.setEntityId("source-e");
        form.setFormKey("details"); form.setFormName("later draft");
        when(((EntityDefinitionMapper) ReflectionTestUtils.getField(assets, "entityMapper")).selectById("source-e")).thenReturn(entity);
        when(((EntityFormMapper) ReflectionTestUtils.getField(assets, "formMapper")).selectByEntityId("source-e")).thenReturn(List.of(form));
        var definition = new UiExtensionDefinition(); definition.setId("source-interface");
        definition.setExtensionType("INTERFACE"); definition.setExtensionKey("expense.submit");
        definition.setVersion(3); definition.setScopeType("GLOBAL"); definition.setStatus("ACTIVE");
        definition.setImplementationConfigDocument("{\"mode\":\"new\"}");
        when(((UiExtensionDefinitionMapper) ReflectionTestUtils.getField(assets, "extensionDefinitionMapper"))
                .selectById("source-interface")).thenReturn(definition);
        var frozenStep = new java.util.LinkedHashMap<>(step("old"));
        frozenStep.put("extensionId", "source-interface");
        var release = new UiConfigRelease(); release.setId("source-release");
        release.setConfigType("FORM"); release.setConfigId("source-f"); release.setVersion(7);
        release.setSnapshotDocument(json.writeValueAsString(Map.of(
                "form", Map.of("formKey", "details", "formName", "published"), "nodes", List.of(),
                "legacyFields", List.of(), "viewCompositions", List.of(),
                "eventBindings", List.of(Map.of("ownerType", "ENTITY", "ownerId", "source-e", "steps", List.of(frozenStep))))));
        var exported = assets.pinnedFormSnapshot(form, release);
        var exportedForm = ConfigMigrationReferenceSupport.object(exported.get("form"));
        assertEquals("published", exportedForm.get("formName"));
        assertTrue(exportedForm.toString().contains("expense.submit"));
        assertFalse(exportedForm.toString().contains("source-interface"));
        var interfaceDefinition = ConfigMigrationReferenceService.maps(exported.get("interfaceExtensions")).get(0);
        assertEquals(3, interfaceDefinition.get("version"));
        assertEquals("{\"mode\":\"old\"}", interfaceDefinition.get("implementationConfigDocument"));
    }

    private Map<String, Object> step(String mode) throws Exception {
        return Map.of("operationSnapshotVersion", 2, "executableSnapshot", json.writeValueAsString(Map.of(
                "extensionKey", "expense.submit", "scopeType", "GLOBAL", "implementationType", "HTTP",
                "implementationConfigDocument", "{\"mode\":\"" + mode + "\"}")));
    }
}
