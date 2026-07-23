package com.workflow.service.migration;

import com.workflow.entity.EntityFormNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ConfigMigrationImportApplyService#resolveNodeIds} 单元测试。
 *
 * <p>验证表单节点导入时按 nodeKey 复用已有ID、拒绝重复 nodeKey、空入参产出空保留集，
 * 以及重复导入保持稳定节点ID的行为。</p>
 */
class ConfigMigrationFormImportSupportTest {

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
