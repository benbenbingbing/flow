package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rebinds portable attachment item keys to stable keys already present in the
 * target environment.
 */
final class AttachmentItemMigrationSupport {

    private AttachmentItemMigrationSupport() {
    }

    static Object rewriteScopedConfiguration(
            Object source,
            List<EntityFieldFileItem> targetItems,
            ObjectMapper objectMapper) {
        List<Map<String, Object>> sourceItems = new ArrayList<>();
        collectFileItems(source, sourceItems, objectMapper);
        if (sourceItems.isEmpty()) {
            return source;
        }
        Map<String, String> targetKeys = resolveTargetKeys(
                sourceItems,
                targetItems == null ? List.of() : targetItems,
                objectMapper);
        return rewriteItemKeys(source, targetKeys, objectMapper);
    }

    static Map<String, String> resolveTargetKeys(
            List<Map<String, Object>> sourceItems,
            List<EntityFieldFileItem> targetItems,
            ObjectMapper objectMapper) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> sourceItem : sourceItems) {
            String sourceKey = text(sourceItem.get("itemKey"));
            if (!StringUtils.hasText(sourceKey)) {
                continue;
            }
            List<EntityFieldFileItem> exactKeyMatches = targetItems.stream()
                    .filter(item -> item != null
                            && sourceKey.equals(item.getItemKey()))
                    .toList();
            if (exactKeyMatches.size() == 1) {
                putResolvedKey(
                        result,
                        sourceKey,
                        exactKeyMatches.get(0).getItemKey());
                continue;
            }

            Set<String> sourceNames = names(
                    text(sourceItem.get("itemName")),
                    sourceItem.get("nameAliases"),
                    objectMapper);
            List<EntityFieldFileItem> nameMatches = targetItems.stream()
                    .filter(item -> item != null
                            && intersects(
                                    sourceNames,
                                    names(
                                            item.getItemName(),
                                            item.getNameAliases(),
                                            objectMapper)))
                    .toList();
            if (nameMatches.size() > 1) {
                throw new IllegalStateException(
                        "附件项历史名称在目标环境匹配到多项: "
                                + text(sourceItem.get("itemName")));
            }
            if (nameMatches.size() == 1
                    && StringUtils.hasText(nameMatches.get(0).getItemKey())) {
                putResolvedKey(
                        result,
                        sourceKey,
                        nameMatches.get(0).getItemKey());
            }
        }
        return result;
    }

    private static void putResolvedKey(
            Map<String, String> result,
            String sourceKey,
            String targetKey) {
        String previous = result.putIfAbsent(sourceKey, targetKey);
        if (previous != null && !previous.equals(targetKey)) {
            throw new IllegalStateException(
                    "附件项稳定标识在迁移配置中存在冲突: " + sourceKey);
        }
    }

    private static void collectFileItems(
            Object source,
            List<Map<String, Object>> result,
            ObjectMapper objectMapper) {
        if (source instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if ("fileItems".equals(String.valueOf(entry.getKey()))
                        && entry.getValue() instanceof Collection<?> items) {
                    for (Object item : items) {
                        if (item instanceof Map<?, ?> itemMap) {
                            result.add(stringMap(itemMap));
                        }
                    }
                } else {
                    collectFileItems(entry.getValue(), result, objectMapper);
                }
            }
            return;
        }
        if (source instanceof Collection<?> collection) {
            collection.forEach(value -> collectFileItems(
                    value, result, objectMapper));
            return;
        }
        Object decoded = decodeJson(source, objectMapper);
        if (decoded != source) {
            collectFileItems(decoded, result, objectMapper);
        }
    }

    private static Object rewriteItemKeys(
            Object source,
            Map<String, String> targetKeys,
            ObjectMapper objectMapper) {
        if (targetKeys.isEmpty()) {
            return source;
        }
        if (source instanceof Map<?, ?> map) {
            Map<String, Object> rewritten = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                String name = String.valueOf(key);
                if ("itemKey".equals(name)
                        && value instanceof String itemKey
                        && targetKeys.containsKey(itemKey)) {
                    rewritten.put(name, targetKeys.get(itemKey));
                } else {
                    rewritten.put(
                            name,
                            rewriteItemKeys(value, targetKeys, objectMapper));
                }
            });
            return rewritten;
        }
        if (source instanceof Collection<?> collection) {
            return collection.stream()
                    .map(value -> rewriteItemKeys(
                            value, targetKeys, objectMapper))
                    .toList();
        }
        Object decoded = decodeJson(source, objectMapper);
        if (decoded != source) {
            try {
                return objectMapper.writeValueAsString(
                        rewriteItemKeys(decoded, targetKeys, objectMapper));
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "附件项迁移配置序列化失败",
                        exception);
            }
        }
        return source;
    }

    private static Set<String> names(
            String currentName,
            Object aliases,
            ObjectMapper objectMapper) {
        Set<String> result = new LinkedHashSet<>();
        if (StringUtils.hasText(currentName)) {
            result.add(currentName.trim());
        }
        Object decoded = decodeJson(aliases, objectMapper);
        if (decoded instanceof Collection<?> collection) {
            collection.stream()
                    .map(AttachmentItemMigrationSupport::text)
                    .filter(StringUtils::hasText)
                    .forEach(result::add);
        }
        return result;
    }

    private static boolean intersects(
            Set<String> left,
            Set<String> right) {
        return left.stream().anyMatch(right::contains);
    }

    private static Object decodeJson(
            Object value,
            ObjectMapper objectMapper) {
        if (!(value instanceof String text)) {
            return value;
        }
        String trimmed = text.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return value;
        }
        try {
            return objectMapper.readValue(trimmed, Object.class);
        } catch (Exception ignored) {
            return value;
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(
                String.valueOf(key), value));
        return result;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
