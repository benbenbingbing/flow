package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiConfigSemanticPatchServiceTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private final JsonDocumentCodec codec =
            new JsonDocumentCodec(objectMapper);
    private final UiConfigSemanticPatchService service =
            new UiConfigSemanticPatchService(codec);

    @Test
    void classifiesDisplayOnlyChangeAsSafeAndAppliesIt() {
        Map<String, Object> source = formSnapshot(node(
                null,
                "原标签",
                false));
        Map<String, Object> target = formSnapshot(node(
                null,
                "新标签",
                false));

        UiConfigSemanticPatchService.PatchAnalysis analysis =
                service.build("FORM", source, target);
        UiConfigSemanticPatchService.PatchApplication application =
                service.apply(
                        source,
                        analysis.operations(),
                        false);

        assertEquals(
                UiConfigSemanticPatchService.SAFE,
                analysis.riskLevel());
        assertTrue(application.compatible());
        assertEquals(
                "新标签",
                nodeProps(application.snapshot())
                        .get("label"));
    }

    @Test
    void classifiesReadonlyChangeAsReviewAndRequiresOverride() {
        Map<String, Object> source = formSnapshot(node(
                null,
                "标签",
                false));
        Map<String, Object> target = formSnapshot(node(
                null,
                "标签",
                true));

        UiConfigSemanticPatchService.PatchAnalysis analysis =
                service.build("FORM", source, target);

        assertEquals(
                UiConfigSemanticPatchService.REVIEW,
                analysis.riskLevel());
        assertFalse(service.apply(
                source,
                analysis.operations(),
                false).compatible());
        assertTrue(service.apply(
                source,
                analysis.operations(),
                true).compatible());
    }

    @Test
    void ignoresMissingVersusEmptyComponentPropsForDisplayOnlyChange() {
        Map<String, Object> sourceNode = node(
                null,
                "原标签",
                false);
        Map<String, Object> sourceProps = codec.readObject(
                String.valueOf(sourceNode.get("propsDocument")),
                "测试节点属性");
        sourceProps.put("componentProps", new LinkedHashMap<>());
        sourceNode.put(
                "propsDocument",
                codec.write(sourceProps, "测试节点属性"));

        Map<String, Object> targetNode = node(
                null,
                "新标签",
                false);
        Map<String, Object> source = formSnapshot(
                sourceNode,
                legacyField("原标签", "{}"));
        Map<String, Object> target = formSnapshot(
                targetNode,
                legacyField("新标签", null));

        UiConfigSemanticPatchService.PatchAnalysis analysis =
                service.build("FORM", source, target);

        assertEquals(
                UiConfigSemanticPatchService.SAFE,
                analysis.riskLevel());
        assertEquals(2, analysis.operations().size());
        assertTrue(analysis.operations().stream().allMatch(item ->
                UiConfigSemanticPatchService.SAFE.equals(
                        item.getRiskLevel())));
        assertTrue(analysis.operations().stream().noneMatch(item ->
                item.getPath().endsWith("/componentProps")));
    }

    @Test
    void reviewsStructuralMoveAndComponentTypeChange() {
        Map<String, Object> source = formSnapshot(node(
                null,
                "标签",
                false));
        Map<String, Object> targetNode = node(
                "section-2",
                "标签",
                false);
        Map<String, Object> props =
                codec.readObject(
                        String.valueOf(targetNode.get("propsDocument")),
                        "测试节点属性");
        props.put("componentType", "dangerous-custom");
        targetNode.put(
                "propsDocument",
                codec.write(props, "测试节点属性"));

        UiConfigSemanticPatchService.PatchAnalysis analysis =
                service.build(
                        "FORM",
                        source,
                        formSnapshot(targetNode));
        UiConfigSemanticPatchService.PatchApplication application =
                service.apply(
                        source,
                        analysis.operations(),
                        true);

        assertEquals(
                UiConfigSemanticPatchService.REVIEW,
                analysis.riskLevel());
        assertTrue(analysis.operations().stream().anyMatch(item ->
                "/parentId".equals(item.getPath())
                        && UiConfigSemanticPatchService.REVIEW.equals(
                                item.getRiskLevel())));
        assertTrue(analysis.operations().stream().anyMatch(item ->
                item.getPath().endsWith("/componentType")
                        && UiConfigSemanticPatchService.REVIEW.equals(
                                item.getRiskLevel())));
        assertFalse(service.apply(
                source,
                analysis.operations(),
                false).compatible());
        assertTrue(application.compatible());
        assertEquals(
                "section-2",
                firstNode(application.snapshot()).get("parentId"));
        assertEquals(
                "dangerous-custom",
                nodeProps(application.snapshot()).get("componentType"));
    }

    @Test
    void reviewsAndAppliesRemovingStableNode() {
        Map<String, Object> source = formSnapshot(node(
                null,
                "标签",
                false));
        Map<String, Object> target =
                new LinkedHashMap<>(source);
        target.put("nodes", List.of());

        UiConfigSemanticPatchService.PatchAnalysis analysis =
                service.build("FORM", source, target);
        UiConfigSemanticPatchService.PatchApplication application =
                service.apply(
                        source,
                        analysis.operations(),
                        true);

        assertEquals(
                UiConfigSemanticPatchService.REVIEW,
                analysis.riskLevel());
        assertTrue(analysis.operations().stream().anyMatch(item ->
                "REMOVED".equals(item.getChangeType())
                        && UiConfigSemanticPatchService.REVIEW.equals(
                                item.getRiskLevel())));
        assertFalse(service.apply(
                source,
                analysis.operations(),
                false).compatible());
        assertTrue(application.compatible());
        assertTrue(((List<?>) application.snapshot().get("nodes")).isEmpty());
    }

    @Test
    void reviewsAndAppliesAddingStableNode() {
        Map<String, Object> source = formSnapshotNodes(List.of());
        Map<String, Object> target = formSnapshot(node(
                null,
                "新增字段",
                false));

        UiConfigSemanticPatchService.PatchAnalysis analysis =
                service.build("FORM", source, target);
        UiConfigSemanticPatchService.PatchApplication application =
                service.apply(
                        source,
                        analysis.operations(),
                        true);

        assertEquals(
                UiConfigSemanticPatchService.REVIEW,
                analysis.riskLevel());
        assertTrue(analysis.operations().stream().anyMatch(item ->
                "ADDED".equals(item.getChangeType())
                        && UiConfigSemanticPatchService.REVIEW.equals(
                                item.getRiskLevel())));
        assertTrue(application.compatible());
        assertEquals(
                "node-1",
                firstNode(application.snapshot()).get("id"));
    }

    @Test
    void treatsListHeaderAsSafeButRendererIdentityAsBlocked() {
        Map<String, Object> source = listSnapshot(
                "原列名",
                "DefaultText");
        Map<String, Object> target = listSnapshot(
                "新列名",
                "RiskBadgeCell");

        UiConfigSemanticPatchService.PatchAnalysis analysis =
                service.build("LIST", source, target);

        assertTrue(analysis.operations().stream().anyMatch(item ->
                item.getPath().endsWith("/fieldName")
                        && UiConfigSemanticPatchService.SAFE.equals(
                                item.getRiskLevel())));
        assertTrue(analysis.operations().stream().anyMatch(item ->
                item.getPath().endsWith("/renderComponent")
                        && UiConfigSemanticPatchService.BLOCKED.equals(
                                item.getRiskLevel())));
        assertEquals(
                UiConfigSemanticPatchService.BLOCKED,
                analysis.riskLevel());
    }

    private Map<String, Object> formSnapshot(
            Map<String, Object> node) {
        return formSnapshot(node, null);
    }

    private Map<String, Object> formSnapshot(
            Map<String, Object> node,
            Map<String, Object> legacyField) {
        Map<String, Object> snapshot = formSnapshotNodes(List.of(node));
        snapshot.put(
                "legacyFields",
                legacyField == null ? List.of() : List.of(legacyField));
        return snapshot;
    }

    private Map<String, Object> formSnapshotNodes(
            List<Map<String, Object>> nodes) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("id", "form-1");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configType", "FORM");
        snapshot.put("form", form);
        snapshot.put("nodes", nodes);
        snapshot.put("legacyFields", List.of());
        return snapshot;
    }

    private Map<String, Object> legacyField(
            String label,
            String componentProps) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("id", "node-1");
        field.put("fieldCode", "name");
        field.put("fieldLabel", label);
        if (componentProps != null) {
            field.put("componentProps", componentProps);
        }
        return field;
    }

    private Map<String, Object> node(
            String parentId,
            String label,
            boolean readonly) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("label", label);
        props.put("readonly", readonly);
        props.put("componentType", "input");
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node-1");
        node.put("nodeKey", "name");
        node.put("nodeType", "FIELD");
        node.put("bindingType", "FIELD");
        node.put("bindingRef", "name");
        node.put("parentId", parentId);
        node.put("propsDocument", codec.write(
                props,
                "测试节点属性"));
        return node;
    }

    private Map<String, Object> listSnapshot(
            String fieldName,
            String renderComponent) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("id", "field-1");
        field.put("fieldCode", "status");
        field.put("fieldName", fieldName);
        field.put("renderComponent", renderComponent);
        Map<String, Object> list = new LinkedHashMap<>();
        list.put("id", "list-1");
        list.put("fields", List.of(field));
        list.put("toolbarConfig", List.of());
        list.put("rowActionConfig", List.of());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configType", "LIST");
        snapshot.put("list", list);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstNode(
            Map<String, Object> snapshot) {
        return (Map<String, Object>) ((List<?>) snapshot.get(
                "nodes")).get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nodeProps(
            Map<String, Object> snapshot) {
        Map<String, Object> node = firstNode(snapshot);
        return codec.readObject(
                String.valueOf(node.get("propsDocument")),
                "测试节点属性");
    }
}
