package com.workflow.entity.ui.application;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * UI 事件输入输出映射与结构化条件求值器。
 *
 * <p>只支持数据路径、字面量和结构化条件，不执行表达式或脚本。</p>
 */
@Component
public class UiEventValueMapper {

    public Object apply(
            Object mappingValue,
            Map<String, Object> source,
            Object fallback) {
        if (mappingValue instanceof List<?> mappings) {
            return applyRows(mappings, source, fallback);
        }
        if (!(mappingValue instanceof Map<?, ?> mapping)
                || mapping.isEmpty()) {
            return fallback;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        mapping.forEach((target, selector) -> {
            Object value;
            if (selector instanceof Map<?, ?> selectorMap
                    && selectorMap.containsKey("literal")) {
                value = selectorMap.get("literal");
            } else {
                value = resolve(source, String.valueOf(selector));
            }
            set(result, String.valueOf(target), value);
        });
        return result;
    }

    private Object applyRows(
            List<?> mappings,
            Map<String, Object> source,
            Object fallback) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean mapped = false;
        for (Object item : mappings) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String sourcePath = text(row.get("sourcePath"));
            String targetPath = text(row.get("targetPath"));
            if (!StringUtils.hasText(targetPath)) {
                continue;
            }
            Object value = row.containsKey("literal")
                    ? row.get("literal")
                    : resolve(source, sourcePath);
            value = transform(
                    value,
                    text(row.get("transform")),
                    text(row.get("separator")));
            requireCompatibleTypes(
                    text(row.get("sourceType")),
                    text(row.get("targetType")));
            boolean clearOnEmpty =
                    !Boolean.FALSE.equals(row.get("clearOnEmpty"));
            if (!clearOnEmpty && empty(value)) {
                continue;
            }
            set(result, targetPath, value);
            mapped = true;
        }
        return mapped ? result : fallback;
    }

    private void requireCompatibleTypes(
            String sourceType,
            String targetType) {
        if (!StringUtils.hasText(sourceType)
                || !StringUtils.hasText(targetType)
                || compatible(sourceType, targetType)) {
            return;
        }
        throw new IllegalArgumentException(
                "实体选择回填字段类型不兼容: "
                        + sourceType + " -> " + targetType);
    }

    private boolean compatible(
            String sourceType,
            String targetType) {
        String source = sourceType.trim().toUpperCase();
        String target = targetType.trim().toUpperCase();
        if (Objects.equals(source, target)) {
            return true;
        }
        Set<String> textTypes = Set.of(
                "STRING", "TEXT", "RICH_TEXT",
                "SELECT", "RADIO", "USER", "DEPT",
                "ROLE", "GROUP", "REFERENCE");
        Set<String> numericTypes = Set.of(
                "INTEGER", "LONG", "DECIMAL", "DOUBLE",
                "NUMBER");
        Set<String> dateTypes = Set.of("DATE", "DATETIME");
        Set<String> collectionTypes = Set.of(
                "MULTI_SELECT", "CHECKBOX", "MULTI_REFERENCE");
        return textTypes.contains(source) && textTypes.contains(target)
                || numericTypes.contains(source)
                && numericTypes.contains(target)
                || dateTypes.contains(source)
                && dateTypes.contains(target)
                || collectionTypes.contains(source)
                && collectionTypes.contains(target);
    }

    public boolean matches(
            Object conditionValue,
            Map<String, Object> source) {
        if (!(conditionValue instanceof Map<?, ?> condition)
                || condition.isEmpty()) {
            return true;
        }
        if (condition.get("all") instanceof List<?> all) {
            return all.stream().allMatch(item -> matches(item, source));
        }
        if (condition.get("any") instanceof List<?> any) {
            return any.stream().anyMatch(item -> matches(item, source));
        }
        if (condition.containsKey("not")) {
            return !matches(condition.get("not"), source);
        }
        Object actual = resolve(source, text(condition.get("path")));
        if (condition.containsKey("equals")) {
            return Objects.equals(actual, condition.get("equals"));
        }
        if (condition.containsKey("notEquals")) {
            return !Objects.equals(actual, condition.get("notEquals"));
        }
        if (condition.get("in") instanceof Collection<?> values) {
            return values.contains(actual);
        }
        if (condition.containsKey("includes")) {
            Object expected = condition.get("includes");
            if (actual instanceof Collection<?> values) {
                return values.contains(expected);
            }
            return actual != null
                    && String.valueOf(actual).contains(String.valueOf(expected));
        }
        if (condition.containsKey("exists")) {
            return Boolean.TRUE.equals(condition.get("exists"))
                    ? actual != null : actual == null;
        }
        if (condition.containsKey("truthy")) {
            boolean truthy = truthy(actual);
            return Boolean.TRUE.equals(condition.get("truthy"))
                    ? truthy : !truthy;
        }
        return true;
    }

    public Object resolve(
            Object source,
            String path) {
        if (!StringUtils.hasText(path)) {
            return source;
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
                continue;
            }
            if (current instanceof List<?> list) {
                try {
                    current = list.get(Integer.parseInt(part));
                    continue;
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
            return null;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    public void set(
            Map<String, Object> target,
            String path,
            Object value) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < parts.length - 1; index++) {
            Object child = current.get(parts[index]);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                current.put(parts[index], child);
            }
            current = (Map<String, Object>) child;
        }
        current.put(parts[parts.length - 1], value);
    }

    private boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return StringUtils.hasText(String.valueOf(value));
    }

    private Object transform(
            Object value,
            String transform,
            String separator) {
        String mode = StringUtils.hasText(transform)
                ? transform.trim().toUpperCase() : "IDENTITY";
        if ("FIRST".equals(mode) && value instanceof List<?> list) {
            return list.isEmpty() ? null : list.get(0);
        }
        if ("JOIN".equals(mode) && value instanceof Collection<?> values) {
            return values.stream()
                    .map(item -> item == null ? "" : String.valueOf(item))
                    .collect(java.util.stream.Collectors.joining(
                            separator == null ? "," : separator));
        }
        if ("ARRAY".equals(mode)
                && value != null
                && !(value instanceof Collection<?>)) {
            return List.of(value);
        }
        return value;
    }

    private boolean empty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return !StringUtils.hasText(text);
        }
        if (value instanceof Collection<?> values) {
            return values.isEmpty();
        }
        return false;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
