package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 按当前表单节点过滤失去目标的事件；仍存在的字段与历史发布继续严格校验。 */
class UiFormFieldEventSnapshotTest {
    private final JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
    private final UiEventBindingMapper bindings = mock(UiEventBindingMapper.class);
    private final UiExtensionDefinitionMapper definitions = mock(UiExtensionDefinitionMapper.class);
    private final UiInterfaceExtensionService interfaces = mock(UiInterfaceExtensionService.class);
    private final UiEventBindingSnapshotService snapshots = new UiEventBindingSnapshotService(
            bindings, definitions, interfaces, codec);
    private final UiConfigInterfaceReferenceValidator validator = new UiConfigInterfaceReferenceValidator(definitions, codec);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removedFieldDoesNotResolveEvenAMissingInterface(boolean pin) {
        UiEventBinding orphan = binding("FORM", "FIELD", "myUser1", "ENTITY_SELECTED");
        orphan.setStepsDocument("[{\"extensionId\":\"missing-interface\"}]");
        when(bindings.findForSnapshot("FORM", "form-1", "entity-1")).thenReturn(List.of(orphan));

        assertEquals(List.of(), snapshots.snapshotForm("form-1", "entity-1", List.of(), pin));
        verifyNoInteractions(interfaces, definitions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIELD", "SUB_FORM", "REPEATER"})
    void hiddenOrReadonlyExistingFieldStillRejectsWrongInterfaceContext(String nodeType) {
        UiEventBinding binding = binding("FORM", "FIELD", "myUser1", "ENTITY_SELECTED");
        binding.setStepsDocument("[{\"extensionId\":\"list-interface\"}]");
        when(bindings.findForSnapshot("FORM", "form-1", "entity-1")).thenReturn(List.of(binding));
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("list-interface");
        definition.setExtensionType("INTERFACE");
        definition.setEnabled(true);
        definition.setDeleted(0);
        definition.setInterfaceContextType("LIST");
        when(interfaces.resolveDefinitionReference("list-interface", null)).thenReturn(definition);
        when(definitions.selectById("list-interface")).thenReturn(definition);
        EntityFormNode node = node(nodeType, "layout_slot", 0);
        node.setPropsDocument("{\"fieldCode\":\"myUser1\",\"hidden\":true,\"readonly\":true}");
        node.setRulesDocument("{\"visibility\":{\"enabled\":true}}");

        List<Map<String, Object>> result = snapshots.snapshotForm("form-1", "entity-1", List.of(node), true);

        assertEquals(1, result.size());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(envelope(result)));
        assertTrue(error.getMessage().contains("LIST 与发布类型 FORM 不一致"));
    }

    @Test
    void deletedNodeAndSameNamedLayoutAreNotFieldTargets() {
        when(bindings.findForSnapshot("FORM", "form-1", "entity-1"))
                .thenReturn(List.of(binding("FORM", "FIELD", "myUser1", "ENTITY_SELECTED")));

        List<Map<String, Object>> result = snapshots.snapshotForm("form-1", "entity-1",
                List.of(node("FIELD", "myUser1", 1), node("SECTION", "myUser1", 0)), true);

        assertEquals(List.of(), result);
    }

    @Test
    void legacyNodeKeyRemainsAFieldTargetWithoutExplicitFieldCode() {
        when(bindings.findForSnapshot("FORM", "form-1", "entity-1"))
                .thenReturn(List.of(binding("FORM", "FIELD", "myUser1", "ENTITY_SELECTED")));

        assertEquals(1, snapshots.snapshotForm("form-1", "entity-1",
                List.of(node("FIELD", "myUser1", 0)), true).size());
    }

    @Test
    void fieldFilteringPreservesDefaultAndButtonChains() {
        when(bindings.findForSnapshot("FORM", "form-1", "entity-1")).thenReturn(List.of(
                binding("ENTITY", "OWNER", "", "ENTITY_SELECTED"),
                binding("FORM", "OWNER", "", "ENTITY_SELECTED"),
                binding("FORM", "BUTTON", "submit", "FORM_BUTTON_CLICK"),
                binding("FORM", "FIELD", "removed", "ENTITY_SELECTED")));

        List<Map<String, Object>> result = snapshots.snapshotForm("form-1", "entity-1", List.of(), true);

        assertEquals(List.of("OWNER", "OWNER", "BUTTON"), result.stream().map(row -> row.get("targetType")).toList());
    }

    @Test
    void activatingHistoricalSnapshotRetainsItsFieldBindingsAfterDraftDeletion() {
        Map<String, Object> historical = envelope(List.of(Map.of(
                "ownerType", "FORM", "ownerId", "form-1", "targetType", "FIELD",
                "targetKey", "myUser1", "eventCode", "ENTITY_SELECTED", "steps", List.of())));

        // 激活路径使用已发布快照，不应查询或套用当前草稿的字段集合。
        assertEquals(historical, snapshots.activationReferenceSnapshot(historical));
        verifyNoInteractions(bindings, interfaces, definitions);
    }

    private Map<String, Object> envelope(List<Map<String, Object>> events) {
        return Map.of("configType", "FORM", "form", Map.of("id", "form-1", "entityId", "entity-1"),
                "eventBindings", events);
    }

    private UiEventBinding binding(String owner, String target, String key, String event) {
        UiEventBinding binding = new UiEventBinding();
        binding.setOwnerType(owner);
        binding.setOwnerId("ENTITY".equals(owner) ? "entity-1" : "form-1");
        binding.setTargetType(target);
        binding.setTargetKey(key);
        binding.setEventCode(event);
        binding.setStepsDocument("[]");
        return binding;
    }

    private EntityFormNode node(String type, String key, int deleted) {
        EntityFormNode node = new EntityFormNode();
        node.setNodeType(type);
        node.setNodeKey(key);
        node.setDeleted(deleted);
        return node;
    }
}
