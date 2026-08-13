package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentItemMigrationSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rewritesSnapshotAndRuleKeysByHistoricalName() throws Exception {
        String componentProps = objectMapper.writeValueAsString(Map.of(
                "fileItems", List.of(Map.of(
                        "itemKey", "afi_source",
                        "itemName", "合同初稿",
                        "nameAliases", List.of("合同"))),
                "attachmentItemRequiredRules", Map.of(
                        "version", 1,
                        "items", List.of(Map.of(
                                "itemKey", "afi_source",
                                "requiredConditionConfig", Map.of(
                                        "version", 1,
                                        "root", Map.of()))))));
        EntityFieldFileItem target = item(
                "afi_target",
                "合同终稿",
                "[\"合同初稿\"]");

        Object rewritten = AttachmentItemMigrationSupport
                .rewriteScopedConfiguration(
                        Map.of("componentProps", componentProps),
                        List.of(target),
                        objectMapper);
        Map<?, ?> root = (Map<?, ?>) rewritten;
        Map<?, ?> props = objectMapper.readValue(
                String.valueOf(root.get("componentProps")),
                Map.class);
        Map<?, ?> snapshotItem = (Map<?, ?>) ((List<?>) props.get(
                "fileItems")).get(0);
        Map<?, ?> rules = (Map<?, ?>) props.get(
                "attachmentItemRequiredRules");
        Map<?, ?> rule = (Map<?, ?>) ((List<?>) rules.get("items"))
                .get(0);

        assertEquals("afi_target", snapshotItem.get("itemKey"));
        assertEquals("afi_target", rule.get("itemKey"));
    }

    @Test
    void rejectsAmbiguousHistoricalNameMatches() {
        List<Map<String, Object>> source = List.of(Map.of(
                "itemKey", "afi_source",
                "itemName", "合同"));

        assertThrows(
                IllegalStateException.class,
                () -> AttachmentItemMigrationSupport.resolveTargetKeys(
                        source,
                        List.of(
                                item("afi_one", "初稿", "[\"合同\"]"),
                                item("afi_two", "终稿", "[\"合同\"]")),
                        objectMapper));
    }

    private EntityFieldFileItem item(
            String itemKey,
            String itemName,
            String aliases) {
        EntityFieldFileItem item = new EntityFieldFileItem();
        item.setItemKey(itemKey);
        item.setItemName(itemName);
        item.setNameAliases(aliases);
        return item;
    }
}
