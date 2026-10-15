package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.application.EntityFormFieldProjection;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.api.response.UiConfigPublishChangeDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 发布摘要必须解释业务对象，并且不能因为去重隐藏独有配置或敏感规则。 */
class UiConfigPublishChangePresenterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonDocumentCodec codec = new JsonDocumentCodec(mapper);
    private final UiConfigSnapshotSupport snapshots = new UiConfigSnapshotSupport(codec, mapper);
    private final UiConfigSemanticPatchService patches = new UiConfigSemanticPatchService(codec);
    private final UiConfigPublishChangePresenter presenter = new UiConfigPublishChangePresenter(codec, snapshots);

    @Test
    void identifiesAddedRemovedAndRenamedListFieldsWithOriginalValues() {
        Map<String, Object> removed = Map.of("id", "old", "fieldLabel", "联系电话");
        Map<String, Object> added = Map.of("id", "new", "fieldLabel", "联系邮箱");
        Map<String, Object> before = list(List.of(removed, Map.of("id", "name", "fieldLabel", "名称", "width", 100)));
        Map<String, Object> after = list(List.of(added, Map.of("id", "name", "fieldLabel", "需求名称", "width", 160)));
        List<UiConfigPublishChangeDTO> result = changes("LIST", before, after);
        UiConfigPublishChangeDTO deletion = find(result, "old", "/");
        assertEquals("REMOVED", deletion.getChangeType());
        assertEquals("联系电话", deletion.getItemLabel());
        assertEquals(removed, deletion.getBeforeValue());
        assertNull(deletion.getAfterValue());
        assertEquals(added, find(result, "new", "/").getAfterValue());
        UiConfigPublishChangeDTO renamed = find(result, "name", "/width");
        assertEquals("需求名称", renamed.getItemLabel());
        assertEquals("名称", renamed.getPreviousItemLabel());
        assertEquals(100, renamed.getBeforeValue());
        assertEquals(160, renamed.getAfterValue());
    }

    @Test
    void keepsIdentityChangesAndSameNamesInDifferentSectionsSeparate() {
        Map<String, Object> before = list(List.of(Map.of("id", "a", "fieldCode", "name", "fieldLabel", "名称")));
        Map<String, Object> after = list(List.of(Map.of("id", "b", "fieldCode", "name", "fieldLabel", "名称")));
        @SuppressWarnings("unchecked") Map<String, Object> config = (Map<String, Object>) after.get("list");
        config.put("toolbarConfig", List.of(Map.of("id", "b", "label", "新增")));
        List<UiConfigPublishChangeDTO> result = changes("LIST", before, after);
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(item -> "fields".equals(item.getSection()) && "名称".equals(item.getItemLabel())));
        assertTrue(result.stream().anyMatch(item -> "toolbarActions".equals(item.getSection()) && "新增".equals(item.getItemLabel())));
    }

    @Test
    void resolvesNestedNodeLabelAndKeepsOnlyProvenPropertyMirrors() {
        Map<String, Object> before = form(node("旧名称", false));
        Map<String, Object> after = form(node("新名称", true));
        List<UiConfigPublishChangeDTO> result = changes("FORM", before, after);
        assertFalse(result.stream().anyMatch(item -> "legacyFields".equals(item.getSection())));
        UiConfigPublishChangeDTO required = find(result, "node-1", "/propsDocument/required");
        assertEquals("新名称", required.getItemLabel());
        assertEquals("旧名称", required.getPreviousItemLabel());
        assertEquals(false, required.getBeforeValue());
        assertEquals(true, required.getAfterValue());
        assertEquals("REVIEW", required.getRiskLevel());
    }

    @Test
    void removesCompleteAdditionAndRemovalMirrorsOnlyWhenAllValuesAreCovered() {
        Map<String, Object> snapshot = form(node("名称", true));
        assertEquals(1, changes("FORM", Map.of(), snapshot).stream().filter(item -> "nodes".equals(item.getSection())).count());
        assertFalse(changes("FORM", Map.of(), snapshot).stream().anyMatch(item -> "legacyFields".equals(item.getSection())));
        List<UiConfigPublishChangeDTO> removed = changes("FORM", snapshot, Map.of());
        assertFalse(removed.stream().anyMatch(item -> "legacyFields".equals(item.getSection())));
        assertEquals("名称", removed.stream().filter(item -> "nodes".equals(item.getSection())).findFirst().orElseThrow().getItemLabel());
    }

    @Test
    void preservesLegacyOnlySensitiveRulesAndUnknownProperties() {
        Map<String, Object> before = form(node("名称", false));
        Map<String, Object> after = form(node("新名称", false));
        field(after).put("validationRules", "{\"permission\":\"finance:edit\"}");
        field(after).put("futureRule", Map.of("enabled", true));
        List<UiConfigPublishChangeDTO> result = changes("FORM", before, after);
        assertTrue(result.stream().anyMatch(item -> "legacyFields".equals(item.getSection()) && "/validationRules".equals(item.getPath())));
        assertTrue(result.stream().anyMatch(item -> "legacyFields".equals(item.getSection()) && "/futureRule".equals(item.getPath())));
        assertTrue(changes("FORM", Map.of(), after).stream().anyMatch(item -> "legacyFields".equals(item.getSection())));
    }

    @Test
    void preservesDivergentPreviousValueEvenWhenNewValueMatchesNodeProjection() {
        Map<String, Object> before = form(node("名称", false));
        field(before).put("isRequired", 2);
        Map<String, Object> after = form(node("新名称", true));
        List<UiConfigPublishChangeDTO> result = changes("FORM", before, after);
        assertTrue(result.stream().anyMatch(item -> "legacyFields".equals(item.getSection()) && "/isRequired".equals(item.getPath())));
    }

    @Test
    void keepsRiskInNodeWhenMatchingValidationMirrorIsRemoved() {
        Map<String, Object> oldNode = node("名称", false);
        oldNode.put("rulesDocument", "{\"validation\":{\"max\":10}}");
        Map<String, Object> newNode = node("名称", false);
        newNode.put("rulesDocument", "{\"validation\":{\"max\":20}}");
        List<UiConfigPublishChangeDTO> result = changes("FORM", form(oldNode), form(newNode));
        assertEquals(1, result.size());
        assertEquals("nodes", result.get(0).getSection());
        assertEquals("REVIEW", result.get(0).getRiskLevel());
        assertEquals("/rulesDocument/validation/max", result.get(0).getPath());
    }

    @Test
    void retainsLegacyOnlyItemsAndUnknownNodeStructures() {
        Map<String, Object> after = Map.of("nodes", List.of(Map.of("id", "unknown", "futureProperty", true)),
                "legacyFields", List.of(Map.of("id", "legacy", "fieldLabel", "历史字段")));
        List<UiConfigPublishChangeDTO> result = changes("FORM", Map.of(), after);
        assertEquals(2, result.size());
        assertEquals("历史字段", find(result, "legacy", "/").getItemLabel());
    }

    @Test
    void doesNotFailOnHistoricalNodeWithoutNodeType() {
        Map<String, Object> after = Map.of("nodes", List.of(Map.of("id", "legacy")),
                "legacyFields", List.of(Map.of("id", "legacy", "fieldLabel", "历史字段")));
        assertEquals(2, changes("FORM", Map.of(), after).size());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"fields", "toolbarConfig", "rowActionConfig"})
    void explainsSparseOrderKeysWithinEachListCollection(String collection) {
        Map<String, Object> before = Map.of("list", Map.of(collection, List.of(
                Map.of("id", "a", "orderKey", 3_000_000), Map.of("id", "b", "orderKey", 1_000_000),
                Map.of("id", "c", "orderKey", 2_000_000))));
        Map<String, Object> after = Map.of("list", Map.of(collection, List.of(
                Map.of("id", "a", "orderKey", 500_000), Map.of("id", "b", "orderKey", 1_000_000),
                Map.of("id", "c", "orderKey", 2_000_000))));
        UiConfigPublishChangeDTO order = find(changes("LIST", before, after), "a", "/orderKey");
        assertEquals("第 3 项", order.getBeforeDisplayValue());
        assertEquals("第 1 项", order.getAfterDisplayValue());
        assertEquals(3_000_000, order.getBeforeValue());
        assertEquals(500_000, order.getAfterValue());
    }

    @Test
    void explainsNodePositionsWithinEachParentAndNamesMovedContainers() {
        Map<String, Object> first = Map.of("id", "group-a", "nodeType", "CONTAINER", "orderKey", 1,
                "propsDocument", "{\"label\":\"基本信息\"}");
        Map<String, Object> second = Map.of("id", "group-b", "nodeType", "CONTAINER", "orderKey", 2,
                "propsDocument", "{\"label\":\"补充信息\"}");
        List<Map<String, Object>> siblings = List.of(first, second,
                orderedNode("a", "group-a", 1_000_000), orderedNode("b", "group-b", 1_000_000),
                orderedNode("c", "group-b", 2_000_000));
        List<Map<String, Object>> oldNodes = new java.util.ArrayList<>(siblings);
        oldNodes.add(orderedNode("moving", "group-a", 2_000_000));
        List<Map<String, Object>> newNodes = new java.util.ArrayList<>(siblings);
        newNodes.add(orderedNode("moving", "group-b", 3_000_000));
        List<UiConfigPublishChangeDTO> result = changes("FORM", Map.of("nodes", oldNodes), Map.of("nodes", newNodes));
        UiConfigPublishChangeDTO order = find(result, "moving", "/orderKey");
        assertEquals("第 2 项", order.getBeforeDisplayValue());
        assertEquals("第 3 项", order.getAfterDisplayValue());
        UiConfigPublishChangeDTO parent = find(result, "moving", "/parentId");
        assertEquals("基本信息", parent.getBeforeDisplayValue());
        assertEquals("补充信息", parent.getAfterDisplayValue());
    }

    @Test
    void usesLegacySortOrderAndReturnsNoGuessForMissingOrTiedOrderKeys() {
        Map<String, Object> before = list(List.of(Map.of("id", "a", "sortOrder", 0), Map.of("id", "b", "sortOrder", 1)));
        Map<String, Object> after = list(List.of(Map.of("id", "a", "sortOrder", 2), Map.of("id", "b", "sortOrder", 1)));
        UiConfigPublishChangeDTO legacy = find(changes("LIST", before, after), "a", "/sortOrder");
        assertEquals("第 1 项", legacy.getBeforeDisplayValue());
        assertEquals("第 2 项", legacy.getAfterDisplayValue());
        before = list(List.of(Map.of("id", "a", "orderKey", 1), Map.of("id", "b")));
        after = list(List.of(Map.of("id", "a", "orderKey", 2), Map.of("id", "b", "orderKey", 2)));
        UiConfigPublishChangeDTO uncertain = find(changes("LIST", before, after), "a", "/orderKey");
        assertNull(uncertain.getBeforeDisplayValue());
        assertNull(uncertain.getAfterDisplayValue());
    }

    @Test
    void namesRootLayoutButDoesNotInventMissingParentName() {
        Map<String, Object> before = Map.of("nodes", List.of(orderedNode("moving", "", 1)));
        Map<String, Object> after = Map.of("nodes", List.of(orderedNode("moving", "missing", 1)));
        UiConfigPublishChangeDTO parent = find(changes("FORM", before, after), "moving", "/parentId");
        assertEquals("表单根布局", parent.getBeforeDisplayValue());
        assertNull(parent.getAfterDisplayValue());
        assertEquals("missing", parent.getAfterValue());
    }

    private List<UiConfigPublishChangeDTO> changes(String type, Map<String, Object> before, Map<String, Object> after) {
        var patch = patches.build(type, before, after);
        return presenter.present(type, before, after, patch.operations());
    }

    private UiConfigPublishChangeDTO find(List<UiConfigPublishChangeDTO> values, String id, String path) {
        return values.stream().filter(value -> id.equals(value.getItemId()) && path.equals(value.getPath())).findFirst().orElseThrow();
    }

    private Map<String, Object> list(List<Map<String, Object>> fields) {
        return Map.of("list", new LinkedHashMap<>(Map.of("fields", fields)));
    }

    private Map<String, Object> node(String label, boolean required) {
        return new LinkedHashMap<>(Map.of("id", "node-1", "formId", "form-1", "nodeType", "FIELD", "nodeKey", "name",
                "propsDocument", codec.write(Map.of("label", label, "fieldCode", "name", "required", required), "节点属性")));
    }

    private Map<String, Object> orderedNode(String id, String parent, int orderKey) {
        Map<String, Object> result = node("名称", false);
        result.put("id", id);
        result.put("parentId", parent);
        result.put("orderKey", orderKey);
        return result;
    }

    private Map<String, Object> form(Map<String, Object> node) {
        List<EntityFormNode> nodes = codec.read(codec.write(List.of(node), "节点"), new TypeReference<List<EntityFormNode>>() {}, "节点");
        return Map.of("form", Map.of("id", "form-1"), "nodes", List.of(node), "legacyFields",
                snapshots.stableValue(new EntityFormFieldProjection(codec).derive("form-1", nodes)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> field(Map<String, Object> snapshot) {
        return ((List<Map<String, Object>>) snapshot.get("legacyFields")).get(0);
    }
}
