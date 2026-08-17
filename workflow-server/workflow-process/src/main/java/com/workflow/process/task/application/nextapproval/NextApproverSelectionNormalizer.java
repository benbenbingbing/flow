package com.workflow.process.task.application.nextapproval;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 将下一审批人历史配置别名投影为发布校验与运行时读取共用的视图。
 *
 * <p>早期设计器曾保存扁平 sourceType/scopes、字符串 source 以及
 * show/display、allowModify/allowEdit。该类只做保序兼容投影，不会把未知
 * 数据源或范围类型放宽为全员；具体业务校验仍由调用方负责。</p>
 */
public final class NextApproverSelectionNormalizer {

    private NextApproverSelectionNormalizer() {
    }

    /**
     * 归一化一份 nextApproverSelection 配置。
     *
     * @param rawSelection 原始配置对象
     * @return 不可变的兼容读取视图
     */
    public static NormalizedSelection normalize(
            Map<String, ?> rawSelection) {
        Map<String, ?> selection = rawSelection == null
                ? Map.of() : rawSelection;
        Object rawSource = selection.get("source");
        Map<String, ?> nestedSource = rawSource instanceof Map<?, ?> map
                ? stringObjectMap(map) : Map.of();

        Object rawScopes = first(
                nestedSource, "rules", "scopes", "scopeRules");
        if (rawScopes == null && rawSource instanceof Collection<?>) {
            rawScopes = rawSource;
        }
        if (rawScopes == null) {
            rawScopes = first(selection, "scopes", "scopeRules");
        }
        String legacyScopeType = firstText(
                nestedSource.get("scopeType"),
                selection.get("scopeType"));
        if (isEmptyScopes(rawScopes)
                && StringUtils.hasText(legacyScopeType)) {
            Map<String, Object> flatScope = new LinkedHashMap<>();
            flatScope.put("type", legacyScopeType);
            flatScope.put(
                    "values",
                    firstNonNull(
                            nestedSource.get("scopeValues"),
                            nestedSource.get("values"),
                            selection.get("scopeValues"),
                            selection.get("values")));
            flatScope.put(
                    "includeChildren",
                    firstNonNull(
                            nestedSource.get("includeChildren"),
                            selection.get("includeChildren")));
            rawScopes = List.of(flatScope);
        }
        rawScopes = normalizeScopes(rawScopes);

        String sourceType = firstText(
                nestedSource.get("type"),
                selection.get("sourceType"),
                rawSource instanceof String ? rawSource : null);
        String resolverCode = firstText(
                nestedSource.get("resolverCode"),
                nestedSource.get("interfaceName"),
                selection.get("resolverCode"),
                selection.get("interfaceName"));
        Object extraParams = nestedSource.containsKey("extraParams")
                ? nestedSource.get("extraParams")
                : selection.get("extraParams");
        boolean invalidSourceShape = rawSource != null
                && !(rawSource instanceof Map<?, ?>)
                && !(rawSource instanceof String)
                && !(rawSource instanceof Collection<?>);

        return new NormalizedSelection(
                integerValue(selection.get("version"), 1),
                booleanValue(first(
                        selection, "visible", "show", "display")),
                booleanValue(first(
                        selection,
                        "editable",
                        "allowModify",
                        "allowEdit")),
                sourceType,
                rawScopes,
                resolverCode,
                extraParams,
                invalidSourceShape);
    }

    /**
     * 归一化每条历史范围的字段别名和标量值；非对象项直接拒绝，不能因
     * 兼容投影静默删除未知输入。
     */
    private static Object normalizeScopes(Object rawScopes) {
        if (!(rawScopes instanceof Collection<?> collection)) {
            return rawScopes;
        }
        List<Object> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> rawScope)) {
                throw new IllegalArgumentException(
                        "scopes 中的范围必须是对象");
            }
            Map<String, Object> scope = stringObjectMap(rawScope);
            String type = firstText(
                    scope.get("type"), scope.get("scopeType"));
            if ("DEPARTMENT".equalsIgnoreCase(type)) {
                type = "ORGANIZATION";
            }
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("type", type);
            canonical.put(
                    "values",
                    stringList(firstNonNull(
                            scope.get("values"),
                            scope.get("targetIds"),
                            scope.get("ids"))));
            canonical.put(
                    "includeChildren",
                    booleanValue(scope.get("includeChildren")));
            result.add(canonical);
        }
        return List.copyOf(result);
    }

    private static List<String> stringList(Object raw) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            collection.forEach(value -> addString(result, value));
        } else {
            addString(result, raw);
        }
        return List.copyOf(result);
    }

    private static void addString(
            Collection<String> target,
            Object value) {
        if (value == null) {
            return;
        }
        for (String item : String.valueOf(value).split(",")) {
            if (StringUtils.hasText(item)) {
                target.add(item.trim());
            }
        }
    }

    private static int integerValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "nextApproverSelection.version 必须是整数",
                    exception);
        }
    }

    private static boolean isEmptyScopes(Object value) {
        return value == null
                || (value instanceof Collection<?> collection
                && collection.isEmpty());
    }

    private static boolean booleanValue(Object value) {
        return value != null
                && Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object first(
            Map<String, ?> values,
            String... names) {
        for (String name : names) {
            if (values.containsKey(name) && values.get(name) != null) {
                return values.get(name);
            }
        }
        return null;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    /** 供发布校验和部署运行时共用的只读兼容视图。 */
    public record NormalizedSelection(
            int version,
            boolean visible,
            boolean editable,
            String sourceType,
            Object rawScopes,
            String resolverCode,
            Object extraParams,
            boolean invalidSourceShape) {
    }
}
