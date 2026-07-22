package com.workflow.service.migration;

import com.workflow.entity.EntityFormNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigMigrationFormImportSupportTest {

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
