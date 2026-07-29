package com.workflow.service;

import com.workflow.entity.ui.application.UiEventValueMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一事件字段映射和结构化条件测试。
 */
class UiEventValueMapperTest {

    private final UiEventValueMapper mapper = new UiEventValueMapper();

    @Test
    void mapsSelectionUsingNestedPathsAndTransforms() {
        Object mapped = mapper.apply(
                List.of(
                        Map.of(
                                "sourcePath", "selection.data.phone",
                                "targetPath", "form.contactPhone"),
                        Map.of(
                                "sourcePath", "selection.data.tags",
                                "targetPath", "form.tagText",
                                "transform", "JOIN",
                                "separator", "、"),
                        Map.of(
                                "sourcePath", "selection.data.contacts",
                                "targetPath", "form.primaryContact",
                                "transform", "FIRST")),
                Map.of(
                        "selection",
                        Map.of(
                                "data",
                                Map.of(
                                        "phone", "13800000000",
                                        "tags", List.of("重点", "续约"),
                                        "contacts", List.of("张三", "李四")))),
                Map.of());

        assertEquals(
                Map.of(
                        "form",
                        Map.of(
                                "contactPhone", "13800000000",
                                "tagText", "重点、续约",
                                "primaryContact", "张三")),
                mapped);
    }

    @Test
    void keepsFallbackWhenEmptyValuesAreConfiguredNotToClear() {
        Map<String, Object> fallback = Map.of("kept", true);

        Object mapped = mapper.apply(
                List.of(
                        Map.of(
                                "sourcePath", "selection.data.phone",
                                "targetPath", "form.contactPhone",
                                "clearOnEmpty", false)),
                Map.of("selection", Map.of("data", Map.of())),
                fallback);

        assertEquals(fallback, mapped);
    }

    @Test
    void rejectsIncompatibleEntitySelectionFieldTypes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.apply(
                        List.of(Map.of(
                                "sourcePath",
                                "selection.data.tags",
                                "targetPath",
                                "form.amount",
                                "sourceType",
                                "MULTI_SELECT",
                                "targetType",
                                "DECIMAL")),
                        Map.of(
                                "selection",
                                Map.of(
                                        "data",
                                        Map.of(
                                                "tags",
                                                List.of("A")))),
                        Map.of()));

        assertTrue(exception.getMessage().contains("类型不兼容"));
    }

    @Test
    void evaluatesNestedAllAnyAndNotConditions() {
        Map<String, Object> source = Map.of(
                "input",
                Map.of(
                        "status", "ACTIVE",
                        "amount", 12,
                        "tags", List.of("VIP", "LOCAL")));
        Map<String, Object> condition = Map.of(
                "all",
                List.of(
                        Map.of(
                                "path", "input.status",
                                "equals", "ACTIVE"),
                        Map.of(
                                "any",
                                List.of(
                                        Map.of(
                                                "path", "input.tags",
                                                "includes", "VIP"),
                                        Map.of(
                                                "path", "input.amount",
                                                "equals", 100))),
                        Map.of(
                                "not",
                                Map.of(
                                        "path", "input.status",
                                        "equals", "DISABLED"))));

        assertTrue(mapper.matches(condition, source));
        assertFalse(mapper.matches(
                Map.of(
                        "path", "input.tags",
                        "includes", "EXTERNAL"),
                source));
    }
}
