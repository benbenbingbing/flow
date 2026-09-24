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
