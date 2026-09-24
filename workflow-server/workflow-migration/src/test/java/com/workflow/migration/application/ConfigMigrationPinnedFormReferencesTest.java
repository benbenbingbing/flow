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

/** 固定子表单按内容定位；不同本地 ID、版本号及重复导入不能产生多余版本。 */
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
    void roundTripReusesMatchingTargetReleaseDespiteDifferentIdsAndVersion() {
        UiConfigRelease source = release("source-r", "source-f", 2);
        EntityForm sourceForm = form("source-f");
        when(releases.selectById("source-r")).thenReturn(source);
        when(forms.selectById("source-f")).thenReturn(sourceForm);
        when(assets.pinnedFormSnapshot(sourceForm, source)).thenReturn(pinnedDocument("source-node", 2));
        Map<String, Object> exported = service.exportReferences(Map.of("propsDocument",
                "{\"componentProps\":{\"subFormConfig\":{\"childFormId\":\"source-f\",\"childFormReleaseId\":\"source-r\"}}}",
                "dependencies", List.of(Map.of("type", "ENTITY", "key", "expense", "required", true,
                        "source", "实体引用字段"))));
        assertFalse(exported.toString().contains("source-r"));
        assertTrue(exported.toString().contains("wf-form://expense/details"));
        // 固定子表单与普通实体引用共享一个依赖，不能因 source 不同插入两条唯一键相同的记录。
        var dependencies = ConfigMigrationReferenceService.maps(exported.get("dependencies"));
        assertEquals(1, dependencies.size());
        assertEquals(List.of("实体引用字段", "固定子表单所属实体"), dependencies.get(0).get("sources"));
        prepareTarget();
        UiConfigRelease target = release("target-r", "target-f", 37);
        EntityForm targetForm = form("target-f");
        when(forms.selectByEntityIdAndFormKey("target-e", "details")).thenReturn(targetForm);
        when(releases.findReleases("FORM", "target-f")).thenReturn(List.of(target));
        when(assets.pinnedFormSnapshot(targetForm, target)).thenReturn(pinnedDocument("target-node", 37));
        clearInvocations(forms, releases);
        Map<String, Object> imported = service.materialize(exported, (type, key) -> key, pin -> fail("已有同内容版本不应新增"));
        String props = text(imported.get("propsDocument"));
        assertTrue(props.contains("target-f")); assertTrue(props.contains("target-r")); assertTrue(props.contains("37"));
        assertFalse(props.contains("source-f")); assertFalse(props.contains("childFormReleaseRef"));
        assertEquals(imported, service.materialize(exported, (type, key) -> key, pin -> fail("重复导入不能新增")));
        verify(forms, never()).selectById(any()); verify(releases, never()).selectById(any());
    }

    @Test
    void missingContentIsImportedOnceAndThenReused() {
        Map<String, Object> pinned = pinnedDocument("source-node", 1);
        pinned = new java.util.LinkedHashMap<>(pinned);
        pinned.put("formRef", "wf-form://expense/details");
        pinned.put("fingerprint", service.fingerprint(object(pinned.get("form"))));
        Map<String, Object> document = Map.of("childFormReleaseRef", pinned);
        prepareTarget();
        EntityForm targetForm = form("target-f");
        when(forms.selectByEntityIdAndFormKey("target-e", "details")).thenReturn(targetForm);
        when(releases.findReleases("FORM", "target-f")).thenReturn(List.of());
        AtomicInteger created = new AtomicInteger();
        UiConfigRelease generated = release("new-local-r", "target-f", 8);
        java.util.function.Function<Map<String, Object>, UiConfigRelease> importer = pin -> {
            created.incrementAndGet();
            when(releases.findReleases("FORM", "target-f")).thenReturn(List.of(generated));
            when(assets.pinnedFormSnapshot(targetForm, generated)).thenReturn(pinnedDocument("target-node", 8));
            return generated;
        };
        Map<String, Object> first = service.materialize(document, (type, key) -> key, importer);
        assertEquals(first, service.materialize(document, (type, key) -> key, importer));
        assertEquals(1, created.get());
        assertEquals("new-local-r", first.get("childFormReleaseId"));
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
