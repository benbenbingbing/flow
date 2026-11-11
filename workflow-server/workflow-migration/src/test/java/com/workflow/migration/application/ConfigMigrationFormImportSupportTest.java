package com.workflow.migration.application;

import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.application.EntityFormFieldProjection;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link ConfigMigrationImportApplyService#resolveNodeIds} 单元测试。
 *
 * <p>验证表单节点导入时按 nodeKey 复用已有ID、拒绝重复 nodeKey、空入参产出空保留集，
 * 以及重复导入保持稳定节点ID的行为。</p>
 */
class ConfigMigrationFormImportSupportTest {

    @Test
    void relationProjectionFlagsAreRemovedForUpgradeAndRollbackButOrdinaryFieldsKeepThem() {
        EntityFormNode node = new EntityFormNode(); node.setNodeType("SUB_FORM");
        Map<String, Object> props = new LinkedHashMap<>(Map.of("required", false, "readonly", false,
                "hidden", false, "label", "明细", "componentProps", Map.of("subFormConfig", Map.of("layout", "form"))));
        assertTrue(ConfigMigrationImportApplyService.normalizeImportedRelationNodeProps(node, props));
        assertEquals(Set.of("label", "componentProps"), props.keySet());
        assertFalse(ConfigMigrationImportApplyService.normalizeImportedRelationNodeProps(node, props));
        node.setNodeType("FIELD"); props.put("required", true);
        assertFalse(ConfigMigrationImportApplyService.normalizeImportedRelationNodeProps(node, props));
        assertEquals(true, props.get("required"));
    }

    /** 实际旧导出包已把缺省值投影成 FIELD，补全后再次 materialize 也必须保持真实数据类型。 */
    @Test
    void recoversMissingAndProjectedFieldTypesBeforeSnapshotMaterialization() {
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
        EntityField target = new EntityField();
        target.setFieldType(EntityField.FieldType.STRING);
        for (boolean projected : List.of(false, true)) {
            EntityFormNode node = new EntityFormNode();
            node.setId("target-node"); node.setNodeKey("note"); node.setNodeType("FIELD");
            Map<String, Object> props = new LinkedHashMap<>(Map.of("fieldCode", "note", "componentType", "input"));
            if (projected) props.put("fieldType", "FIELD");
            assertTrue(ConfigMigrationImportApplyService.completeImportedFieldType(node, props, target));
            node.setPropsDocument(codec.write(props, "测试节点"));
            EntityFormField previous = new EntityFormField();
            previous.setFieldCode("note"); previous.setFieldType("FIELD");
            var restored = new EntityFormFieldProjection(codec).materialize("target-form", List.of(previous), List.of(node));
            assertEquals("STRING", codec.readObject(restored.get(0).getPropsDocument(), "恢复节点").get("fieldType"));
        }
    }

    @Test
    void preservesExplicitTypeAndStructuralNodesAndDoesNotGuessMissingEntityField() {
        EntityField target = new EntityField(); target.setFieldType(EntityField.FieldType.STRING);
        EntityFormNode node = new EntityFormNode(); node.setNodeType("FIELD");
        Map<String, Object> props = new LinkedHashMap<>(Map.of("fieldType", "DECIMAL"));
        assertFalse(ConfigMigrationImportApplyService.completeImportedFieldType(node, props, target));
        assertEquals("DECIMAL", props.get("fieldType"));
        props.clear();
        assertFalse(ConfigMigrationImportApplyService.completeImportedFieldType(node, props, null));
        node.setNodeType("SUB_FORM");
        assertFalse(ConfigMigrationImportApplyService.completeImportedFieldType(node, props, target));
    }

    /** 入参节点的 nodeKey 与已有节点匹配时，应复用已有节点ID，其余生成新ID。 */
    @Test
    void reusesExistingNodeIdByNodeKey() {
        EntityFormNode existing = new EntityFormNode();
        existing.setId("stable-node-id");
        existing.setNodeKey("amount");
        AtomicInteger generated = new AtomicInteger();

        Map<String, String> result =
                ConfigMigrationImportApplyService.resolveNodeIds(
                        List.of(existing),
                        List.of(
                                Map.of("nodeKey", "amount"),
                                Map.of("nodeKey", "remark")),
                        () -> "generated-" + generated.incrementAndGet());

        assertEquals("stable-node-id", result.get("amount"));
        assertEquals("generated-1", result.get("remark"));
        assertEquals(1, generated.get());
    }

    /** 入参节点中存在重复 nodeKey 时应抛出 IllegalStateException。 */
    @Test
    void rejectsDuplicateIncomingNodeKeys() {
        assertThrows(
                IllegalStateException.class,
                () -> ConfigMigrationImportApplyService.resolveNodeIds(
                        List.of(),
                        List.of(
                                Map.of("nodeKey", "amount"),
                                Map.of("nodeKey", "amount")),
                        () -> "new-id"));
    }

    /** 入参节点为空时，应产出空保留集(不保留任何已有节点)。 */
    @Test
    void emptyIncomingNodesProduceEmptyRetainedSet() {
        EntityFormNode existing = new EntityFormNode();
        existing.setId("old-node-id");
        existing.setNodeKey("old");

        Map<String, String> result =
                ConfigMigrationImportApplyService.resolveNodeIds(
                        List.of(existing),
                        List.of(),
                        () -> "unused");

        assertEquals(Map.of(), result);
    }

    /** 重复导入相同节点时，第二次应直接复用第一次生成的节点ID，不再生成新ID。 */
    @Test
    void repeatedImportKeepsPreviouslyGeneratedNodeIds() {
        AtomicInteger generated = new AtomicInteger();
        List<Map<String, Object>> incoming = List.of(
                Map.of("nodeKey", "amount"),
                Map.of("nodeKey", "remark"));

        Map<String, String> first =
                ConfigMigrationImportApplyService.resolveNodeIds(
                        List.of(),
                        incoming,
                        () -> "generated-" + generated.incrementAndGet());
        List<EntityFormNode> existing = first.entrySet().stream()
                .map(entry -> {
                    EntityFormNode node = new EntityFormNode();
                    node.setId(entry.getValue());
                    node.setNodeKey(entry.getKey());
                    return node;
                })
                .toList();
        Map<String, String> second =
                ConfigMigrationImportApplyService.resolveNodeIds(
                        existing,
                        incoming,
                        () -> "unexpected-" + generated.incrementAndGet());

        assertEquals(first, second);
        assertEquals(2, generated.get());
    }
}
