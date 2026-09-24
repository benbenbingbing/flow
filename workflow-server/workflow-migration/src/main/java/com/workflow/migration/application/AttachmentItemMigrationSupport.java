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

    /**
     * 初始化附件条目迁移支持，保存构造参数供后续方法使用。
     */
    private AttachmentItemMigrationSupport() {
    }

    /**
     * 处理重写{@code scoped}配置，并将结果传给后续步骤。
     *
     * @param source 待处理重写{@code scoped}配置的原始输入，结果供调用方继续使用
     * @param targetItems 目标条目，作为 {@code resolveTargetKeys} 的输入影响后续处理
     * @param objectMapper 对象映射器，作为 {@code collectFileItems} 的输入影响后续处理
     * @return 处理后的重写{@code scoped}配置结果，供调用方继续处理
     */
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

    /**
     * 解析目标键集合；输出作为后续校验或处理的输入。
     *
     * @param sourceItems 来源条目，供本方法解析目标键集合时使用
     * @param targetItems 目标条目，供本方法解析目标键集合时使用
     * @param objectMapper 对象映射器，供本方法解析目标键集合时使用
     * @return 目标键集合键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 写入已解析键；后续读取或执行将使用更新后的状态。
     *
     * @param result 结果，供本方法写入已解析键时使用
     * @param sourceKey 来源键，后续用于授权校验、关联或幂等去重
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 收集文件条目；结果供调用方的后续步骤使用。
     *
     * @param source 待收集文件条目的原始输入，结果供调用方继续使用
     * @param result 结果，作为 {@code collection.forEach} 的输入影响后续处理
     * @param objectMapper 对象映射器，作为 {@code collection.forEach} 的输入影响后续处理
     */
    private static void collectFileItems(
            Object source,
            List<Map<String, Object>> result,
            ObjectMapper objectMapper) {
        if (source instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if ("childFormReleaseRef".equals(String.valueOf(entry.getKey()))) continue;
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

    /**
     * 处理重写条目键集合，并将结果传给后续步骤。
     *
     * @param source 待处理重写条目键集合的原始输入，结果供调用方继续使用
     * @param targetKeys 目标键集合，作为 {@code rewritten.put} 的输入影响后续处理
     * @param objectMapper 对象映射器，作为 {@code rewritten.put} 的输入影响后续处理
     * @return 处理后的重写条目键集合结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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
                if ("childFormReleaseRef".equals(name)) {
                    // 固定子表单属于另一个实体，必须由它自己的字段目录处理附件项。
                    rewritten.put(name, value);
                } else if ("itemKey".equals(name)
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

    /**
     * 整理名称集合数据，供调用方遍历或继续处理。
     *
     * @param currentName 当前名称，后续用于处理名称集合时匹配或展示
     * @param aliases {@code aliases}，作为 {@code decodeJson} 的输入影响后续处理
     * @param objectMapper 对象映射器，作为 {@code decodeJson} 的输入影响后续处理
     * @return 附件条目迁移支持集合，供调用方遍历或展示
     */
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

    /**
     * 判断{@code intersects}条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理{@code intersects}时使用
     * @param right 右侧，供本方法处理{@code intersects}时使用
     * @return {@code intersects}条件成立时为 true，否则为 false
     */
    private static boolean intersects(
            Set<String> left,
            Set<String> right) {
        return left.stream().anyMatch(right::contains);
    }

    /**
     * 解码JSON；输出作为后续校验或处理的输入。
     *
     * @param value 待解码JSON的原始输入，结果供调用方继续使用
     * @param objectMapper 对象映射器，供本方法解码JSON时使用
     * @return 解码后的JSON结果，供调用方继续处理
     */
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

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param source 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(
                String.valueOf(key), value));
        return result;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
