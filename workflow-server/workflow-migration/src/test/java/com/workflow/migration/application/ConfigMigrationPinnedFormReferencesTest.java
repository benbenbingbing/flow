package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 子表单按实体编码和表单键定位，旧固定版本包被明确拒绝。 */
class ConfigMigrationPinnedFormReferencesTest {
    private final EntityDefinitionMapper entities = mock(EntityDefinitionMapper.class);
    private final EntityFormMapper forms = mock(EntityFormMapper.class);
    private final UiConfigReleaseMapper releases = mock(UiConfigReleaseMapper.class);
    private final ConfigMigrationAssetService assets = mock(ConfigMigrationAssetService.class);
    private final ConfigMigrationReferenceService identities = new ConfigMigrationReferenceService(null, null, null, null, null, null);
    private final ConfigMigrationSubFormReferences service;

    ConfigMigrationPinnedFormReferencesTest() {
        ObjectProvider<ConfigMigrationAssetService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(assets);
        service = new ConfigMigrationSubFormReferences(entities, forms, releases, identities, provider, new ObjectMapper());
    }

    @Test
    void roundTripUsesStableBusinessIdentityAndNoFormVersion() {
        EntityForm sourceForm = form("source-f"); sourceForm.setEntityId("source-e");
        when(forms.selectById("source-f")).thenReturn(sourceForm);
        EntityDefinition sourceEntity = new EntityDefinition(); sourceEntity.setId("source-e"); sourceEntity.setEntityCode("expense");
        when(entities.selectById("source-e")).thenReturn(sourceEntity);
        Map<String, Object> exported = service.exportReferences(Map.of("propsDocument",
                "{\"componentProps\":{\"subFormConfig\":{\"childFormId\":\"source-f\",\"childFormReleaseId\":\"source-r\",\"childFormReleaseVersion\":9}}}",
                "dependencies", List.of(Map.of("type", "ENTITY", "key", "expense", "required", true, "source", "实体引用字段"))));
        assertFalse(exported.toString().contains("source-r"));
        assertTrue(exported.toString().contains("wf-form://expense/details"));
        assertEquals(1, ConfigMigrationReferenceService.maps(exported.get("dependencies")).size());
        prepareTarget();
        when(forms.selectByEntityIdAndFormKey("target-e", "details")).thenReturn(form("target-f"));
        Map<String, Object> imported = service.materialize(exported, (type, key) -> key, pin -> fail("不得恢复隐藏历史配置"));
        String props = text(imported.get("propsDocument"));
        assertTrue(props.contains("target-f")); assertFalse(props.contains("childFormRelease"));
        assertFalse(props.contains("source-f")); assertFalse(props.contains("childFormRef"));
        assertEquals(imported, service.materialize(exported, (type, key) -> key, pin -> fail("不应创建版本")));
        verifyNoInteractions(releases, assets);
    }

    @Test
    void missingDependencyRequiresPackageFormOrExistingTarget() {
        prepareTarget();
        Map<String, Object> document = Map.of("childFormRef", "wf-form://expense/details");
        assertThrows(IllegalArgumentException.class, () -> service.materialize(document,
                (type, key) -> key, pin -> fail("不得恢复隐藏历史配置")));
        assertDoesNotThrow(() -> service.validate(document, Set.of("wf-form://expense/details"), (type, key) -> key));
        assertThrows(IllegalArgumentException.class, () -> service.validate(document, Set.of(), (type, key) -> key));
        verifyNoInteractions(releases, assets);
    }

    @Test
    void tamperedContentAndRawSourceIdAreRejectedBeforeImporterRuns() {
        Map<String, Object> pinned = new java.util.LinkedHashMap<>(pinnedDocument("node", 1));
        pinned.put("formRef", "wf-form://expense/details"); pinned.put("fingerprint", "wrong");
        assertThrows(IllegalArgumentException.class, () -> service.validate(Map.of("childFormReleaseRef", pinned),
                Set.of("wf-form://expense/details"), (type, key) -> key));
        assertThrows(IllegalArgumentException.class, () -> service.materialize(Map.of("childFormId", "source-f"),
                (type, key) -> key, pin -> fail("不能使用裸 ID 导入")));
    }

    @Test
    void fingerprintIgnoresMetadataButKeepsActualConfiguration() {
        assertEquals(service.fingerprint(object(pinnedDocument("a", 1).get("form"))),
                service.fingerprint(object(pinnedDocument("b", 99).get("form"))));
        Map<String, Object> different = object(pinnedDocument("a", 1).get("form"));
        different.put("layoutType", "different");
        assertNotEquals(service.fingerprint(different), service.fingerprint(object(pinnedDocument("a", 1).get("form"))));
    }

    @Test
    void businessIdsAndNullDefaultsParticipateInFingerprint() {
        Map<String, Object> first = Map.of("formKey", "details", "nodes", List.of(Map.of(
                "nodeKey", "amount", "propsDocument", "{\"defaultValue\":{\"id\":\"record-a\",\"value\":null}}")));
        Map<String, Object> second = Map.of("formKey", "details", "nodes", List.of(Map.of(
                "nodeKey", "amount", "propsDocument", "{\"defaultValue\":{\"id\":\"record-b\",\"value\":null}}")));
        Map<String, Object> absent = Map.of("formKey", "details", "nodes", List.of(Map.of(
                "nodeKey", "amount", "propsDocument", "{\"defaultValue\":{\"id\":\"record-a\"}}")));
        assertNotEquals(service.fingerprint(first), service.fingerprint(second));
        assertNotEquals(service.fingerprint(first), service.fingerprint(absent));
    }

    private void prepareTarget() {
        EntityDefinition entity = new EntityDefinition(); entity.setId("target-e"); entity.setEntityCode("expense");
        when(entities.findByEntityCode("expense")).thenReturn(Optional.of(entity));
    }
    private EntityForm form(String id) { EntityForm form = new EntityForm(); form.setId(id); form.setFormKey("details"); return form; }
    private UiConfigRelease release(String id, String formId, int version) {
        UiConfigRelease release = new UiConfigRelease(); release.setId(id); release.setConfigId(formId); release.setConfigType("FORM"); release.setVersion(version); return release;
    }
    private Map<String, Object> pinnedDocument(String nodeId, int version) {
        return Map.of("entityCode", "expense", "form", Map.of("formKey", "details", "publishedVersion", version,
                "nodes", List.of(Map.of("id", nodeId, "revision", version, "nodeKey", "amount", "nodeType", "FIELD", "bindingRef", "amount",
                        "propsDocument", "{\"label\":\"金额\",\"fieldId\":\"" + nodeId + "\"}"))));
    }
}
