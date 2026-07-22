package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiConfigDiffDTO;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityFormNode;
import com.workflow.entity.UiComponentTemplate;
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.UiComponentTemplateMapper;
import com.workflow.mapper.UiComponentTemplateVersionMapper;
import com.workflow.mapper.UiConfigReleaseMapper;
import com.workflow.mapper.UiDataSourceDefinitionMapper;
import com.workflow.service.config.EntityListConfigurationValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiConfigReleaseServiceTest {

    @Test
    void ignoresDraftRevisionsAndTimestampsWhenComparingLegacyRelease() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        EntityFormService formService = mock(EntityFormService.class);
        EntityForm form = form();
        when(formService.getById("form-1")).thenReturn(form);

        UiConfigReleaseService service = new UiConfigReleaseService(
                releaseMapper,
                mock(UiDataSourceDefinitionMapper.class),
                mock(UiComponentTemplateMapper.class),
                mock(UiComponentTemplateVersionMapper.class),
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                formService,
                mock(EntityFormNodeService.class),
                mock(UiExtensionDefinitionService.class),
                mock(EntityListConfigService.class),
                mock(EntityListConfigurationValidator.class),
                codec,
                objectMapper);

        Map<String, Object> legacySnapshot = objectMapper.convertValue(
                service.draftSnapshot(UiConfigReleaseService.FORM, "form-1"),
                Map.class);
        ((Map<String, Object>) legacySnapshot.get("form"))
                .put("revision", 1);
        ((Map<String, Object>) legacySnapshot.get("form"))
                .put("activeReleaseId", null);
        ((Map<String, Object>) ((List<?>) legacySnapshot.get("nodes")).get(0))
                .put("revision", 1);
        ((Map<String, Object>) ((List<?>) legacySnapshot.get("nodes")).get(0))
                .put("updatedAt", "2026-01-01T00:00:00");
        ((Map<String, Object>) ((List<?>) legacySnapshot.get("legacyFields")).get(0))
                .put("updateTime", "2026-01-01T00:00:00");

        UiConfigRelease release = new UiConfigRelease();
        release.setSnapshotDocument(codec.write(legacySnapshot, "测试历史发布快照"));
        release.setContentHash("legacy-integrity-hash");
        when(releaseMapper.findActive(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(release);

        UiConfigDiffDTO diff = service.diff(
                UiConfigReleaseService.FORM, "form-1");
        Map<String, Object> draftField = (Map<String, Object>) (
                (List<?>) service.draftSnapshot(
                        UiConfigReleaseService.FORM, "form-1")
                        .get("legacyFields")).get(0);
        Map<String, Object> draftForm = (Map<String, Object>)
                service.draftSnapshot(
                        UiConfigReleaseService.FORM,
                        "form-1").get("form");

        assertFalse(diff.isChanged(), diff.toString());
        assertTrue(diff.getChangedSections().isEmpty());
        assertEquals("实体字段名称", draftField.get("fieldName"));
        assertEquals("STRING", draftField.get("fieldType"));
        assertEquals("{}", draftField.get("componentProps"));
        assertEquals(
                "{\"FORM_INIT\":{\"sourceId\":\"source-init\"}}",
                draftForm.get("dataSourceBindingsDocument"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsStableNodeMoveInDetailedDiff() {
        TestContext context = context();
        EntityForm form = form();
        when(context.formService().getById("form-1")).thenReturn(form);
        Map<String, Object> activeSnapshot = new LinkedHashMap<>(
                context.service().draftSnapshot(
                        UiConfigReleaseService.FORM, "form-1"));
        List<Map<String, Object>> activeNodes = new ArrayList<>(
                (List<Map<String, Object>>) activeSnapshot.get("nodes"));
        Map<String, Object> movedNode = new LinkedHashMap<>(activeNodes.get(0));
        movedNode.put("parentId", "section-1");
        movedNode.put("orderKey", 100L);
        activeNodes.set(0, movedNode);
        activeSnapshot.put("nodes", activeNodes);

        UiConfigRelease active = new UiConfigRelease();
        active.setSnapshotDocument(context.codec().write(
                activeSnapshot, "测试移动节点发布快照"));
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(active);

        UiConfigDiffDTO diff = context.service().diff(
                UiConfigReleaseService.FORM, "form-1");

        assertTrue(diff.getChangedItems().stream().anyMatch(item ->
                "nodes".equals(item.getSection())
                        && "node-1".equals(item.getId())
                        && "MOVED".equals(item.getChangeType())
                        && item.getChangedFields().contains("parentId")
                        && item.getChangedFields().contains("orderKey")));
    }

    @Test
    void rejectsActivationWhenSnapshotHashDoesNotMatch() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(node("field", null, "FIELD"))));
        release.setContentHash("tampered-hash");
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("完整性校验失败"));
    }

    @Test
    void resolvesPinnedInactiveFormReleaseWithRecursiveNodes() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-7",
                formSnapshot(List.of(node("section", null, "SECTION"))));
        release.setVersion(7);
        when(context.releaseMapper().selectById("release-7"))
                .thenReturn(release);

        ResolvedEntityFormRelease resolution =
                context.service().resolveRuntimeFormRelease(
                "form-1",
                "release-7",
                7);

        EntityForm form = resolution.form();
        assertEquals("form-1", form.getId());
        assertEquals(1, form.getNodes().size());
        assertEquals("section", form.getNodes().get(0).getId());
        assertTrue(resolution.pinned());
    }

    @Test
    void rejectsPinnedReleaseVersionMismatch() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-7",
                formSnapshot(List.of()));
        release.setVersion(7);
        when(context.releaseMapper().selectById("release-7"))
                .thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().resolveRuntimeForm(
                        "form-1",
                        "release-7",
                        8));

        assertTrue(exception.getMessage().contains("版本号与流程快照不一致"));
    }

    @Test
    void rejectsActivationWhenTabIsOutsideTabSet() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(node("tab", null, "TAB"))));
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("TAB 节点只能位于 TAB_SET"));
    }

    @Test
    void rejectsActivationWhenSnapshotExceedsEightLevels() {
        TestContext context = context();
        List<Map<String, Object>> nodes = new ArrayList<>();
        String parentId = null;
        for (int index = 1; index <= 9; index++) {
            String id = "section-" + index;
            nodes.add(node(id, parentId, "SECTION"));
            parentId = id;
        }
        UiConfigRelease release = release(
                context.codec(), "release-1", formSnapshot(nodes));
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("不能超过 8 层"));
    }

    @Test
    void rejectsActivationWhenLeafNodeContainsChild() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(
                        node("text", null, "TEXT"),
                        node("field", "text", "FIELD"))));
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("不能直接包含"));
    }

    @Test
    void rejectsActivationWhenPublishedFormReferencesCycle() {
        TestContext context = context();
        UiConfigRelease target = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(referenceNode(
                        "reference-form-2", "form-2"))));
        UiConfigRelease formTwo = release(
                context.codec(),
                "release-2",
                formSnapshot(List.of(referenceNode(
                        "reference-form-1", "form-1"))));
        formTwo.setConfigId("form-2");
        when(context.releaseMapper().selectById("release-1")).thenReturn(target);
        when(context.releaseMapper().findActive("FORM", "form-2"))
                .thenReturn(formTwo);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("存在循环"));
    }

    @Test
    void rejectsActivationWhenPublishedFormReferencesExceedEightLevels() {
        TestContext context = context();
        UiConfigRelease target = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(referenceNode(
                        "reference-form-2", "form-2"))));
        when(context.releaseMapper().selectById("release-1")).thenReturn(target);
        for (int index = 2; index <= 8; index++) {
            UiConfigRelease referenced = release(
                    context.codec(),
                    "release-" + index,
                    formSnapshot(List.of(referenceNode(
                            "reference-form-" + (index + 1),
                            "form-" + (index + 1)))));
            referenced.setConfigId("form-" + index);
            when(context.releaseMapper().findActive(
                    "FORM", "form-" + index))
                    .thenReturn(referenced);
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("跨表单嵌套层级不能超过 8 层"));
    }

    @Test
    void rejectsPublishWhenTemplateDoesNotExist() {
        TestContext context = context();
        EntityForm form = form();
        form.getNodes().get(0).setTemplateId("missing-template");
        form.getNodes().get(0).setTemplateVersion(1);
        when(context.formService().getById("form-1")).thenReturn(form);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().publish("FORM", "form-1", null));

        assertTrue(exception.getMessage().contains("模板不存在或未启用"));
    }

    @Test
    void rejectsActivationWhenTemplateTypeIsIncompatible() {
        TestContext context = context();
        Map<String, Object> field = node("field", null, "FIELD");
        field.put("templateId", "subform-template");
        field.put("templateVersion", 1);
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(field)));
        UiComponentTemplate template = new UiComponentTemplate();
        template.setId("subform-template");
        template.setTemplateType("SUB_FORM");
        template.setStatus("ACTIVE");
        template.setDeleted(0);
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);
        when(context.templateMapper().selectById("subform-template"))
                .thenReturn(template);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("与节点类型 FIELD 不兼容"));
    }

    @Test
    void rejectsActivationWhenLockedTemplateVersionDoesNotExist() {
        TestContext context = context();
        Map<String, Object> section = node("section", null, "SECTION");
        section.put("templateId", "section-template");
        section.put("templateVersion", 3);
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(section)));
        UiComponentTemplate template = new UiComponentTemplate();
        template.setId("section-template");
        template.setTemplateType("FORM_SECTION");
        template.setStatus("ACTIVE");
        template.setDeleted(0);
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);
        when(context.templateMapper().selectById("section-template"))
                .thenReturn(template);
        when(context.templateVersionMapper().selectOne(any())).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("模板版本不存在"));
    }

    private TestContext context() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        UiComponentTemplateMapper templateMapper =
                mock(UiComponentTemplateMapper.class);
        UiComponentTemplateVersionMapper templateVersionMapper =
                mock(UiComponentTemplateVersionMapper.class);
        EntityFormService formService = mock(EntityFormService.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        when(formMapper.selectByIdForUpdate("form-1"))
                .thenReturn(form());
        UiConfigReleaseService service = new UiConfigReleaseService(
                releaseMapper,
                mock(UiDataSourceDefinitionMapper.class),
                templateMapper,
                templateVersionMapper,
                formMapper,
                mock(EntityListConfigMapper.class),
                formService,
                mock(EntityFormNodeService.class),
                mock(UiExtensionDefinitionService.class),
                mock(EntityListConfigService.class),
                mock(EntityListConfigurationValidator.class),
                codec,
                objectMapper);
        return new TestContext(
                service,
                releaseMapper,
                templateMapper,
                templateVersionMapper,
                formService,
                codec);
    }

    private UiConfigRelease release(
            JsonDocumentCodec codec,
            String releaseId,
            Map<String, Object> snapshot) {
        String document = codec.canonicalize(
                codec.write(snapshot, "测试发布快照"), "测试发布快照");
        UiConfigRelease release = new UiConfigRelease();
        release.setId(releaseId);
        release.setConfigType("FORM");
        release.setConfigId("form-1");
        release.setVersion(1);
        release.setStatus("INACTIVE");
        release.setSnapshotDocument(document);
        release.setContentHash(sha256(document));
        return release;
    }

    private Map<String, Object> formSnapshot(List<Map<String, Object>> nodes) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("id", "form-1");
        form.put("entityId", "entity-1");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("configType", "FORM");
        snapshot.put("form", form);
        snapshot.put("nodes", nodes);
        snapshot.put("legacyFields", List.of());
        return snapshot;
    }

    private Map<String, Object> node(
            String id,
            String parentId,
            String nodeType) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("formId", "form-1");
        node.put("parentId", parentId);
        node.put("nodeKey", id.replace('-', '_'));
        node.put("nodeType", nodeType);
        node.put("bindingType", "NONE");
        node.put("orderKey", 1_000_000);
        return node;
    }

    private Map<String, Object> referenceNode(
            String id,
            String publishedFormId) {
        Map<String, Object> node = node(id, null, "SUB_FORM");
        node.put(
                "propsDocument",
                "{\"publishedFormId\":\"" + publishedFormId + "\"}");
        return node;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record TestContext(
            UiConfigReleaseService service,
            UiConfigReleaseMapper releaseMapper,
            UiComponentTemplateMapper templateMapper,
            UiComponentTemplateVersionMapper templateVersionMapper,
            EntityFormService formService,
            JsonDocumentCodec codec) {
    }

    private EntityForm form() {
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setFormName("测试表单");
        form.setFormKey("test_form");
        form.setLayoutType("grid");
        form.setRevision(9);
        form.setActiveReleaseId("release-1");
        form.setDataSourceBindingsDocument(
                "{\"FORM_INIT\":{\"sourceId\":\"source-init\"}}");

        EntityFormField field = new EntityFormField();
        field.setId("node-1");
        field.setFormId("form-1");
        field.setFieldId("field-1");
        field.setFieldCode("name");
        field.setFieldName("实体字段名称");
        field.setFieldLabel("名称");
        field.setFieldType("STRING");
        field.setComponentType("input");
        field.setComponentProps("{}");
        field.setGridSpan(24);
        field.setSortOrder(0);
        field.setUpdateTime(LocalDateTime.now());
        form.setFields(List.of(field));

        EntityFormNode node = new EntityFormNode();
        node.setId("node-1");
        node.setFormId("form-1");
        node.setNodeKey("name");
        node.setNodeType("FIELD");
        node.setBindingType("ENTITY_FIELD");
        node.setBindingRef("name");
        node.setPropsDocument(
                "{\"fieldId\":\"field-1\",\"fieldCode\":\"name\","
                        + "\"label\":\"名称\",\"componentType\":\"input\","
                        + "\"gridSpan\":24}");
        node.setOrderKey(1_000_000L);
        node.setRevision(7);
        node.setUpdatedAt(LocalDateTime.now());
        form.setNodes(List.of(node));
        return form;
    }
}
