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

    /**
     * 初始化下一步审批人选择{@code normalizer}，保存构造参数供后续方法使用。
     */
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
     *
     * @param rawScopes 原始{@code scopes}，供本方法规范化{@code scopes}时使用
     * @return 规范化后的{@code scopes}结果，供调用方继续处理
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

    /**
     * 整理字符串列表数据，供调用方遍历或继续处理。
     *
     * @param raw 待处理字符串列表的原始输入，结果供调用方继续使用
     * @return 下一步审批人选择{@code normalizer}集合，供调用方遍历或展示
     */
    private static List<String> stringList(Object raw) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            collection.forEach(value -> addString(result, value));
        } else {
            addString(result, raw);
        }
        return List.copyOf(result);
    }

    /**
     * 添加字符串；结果供后续流程传递或持久化。
     *
     * @param target 目标，供本方法添加字符串时使用
     * @param value 待添加字符串的原始输入，结果供调用方继续使用
     */
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

    /**
     * 处理整数值，并将结果传给后续步骤。
     *
     * @param value 待处理整数值的原始输入，结果供调用方继续使用
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 处理后的整数值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 判断是否空{@code scopes}；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否空{@code scopes}的原始输入，结果供调用方继续使用
     * @return 空{@code scopes}条件成立时为 true，否则为 false
     */
    private static boolean isEmptyScopes(Object value) {
        return value == null
                || (value instanceof Collection<?> collection
                && collection.isEmpty());
    }

    /**
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param value 待处理布尔值值的原始输入，结果供调用方继续使用
     * @return 布尔值值条件成立时为 true，否则为 false
     */
    private static boolean booleanValue(Object value) {
        return value != null
                && Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 处理首个，并将结果传给后续步骤。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param names 名称集合，供本方法处理首个时使用
     * @return 处理后的首个结果，供调用方继续处理
     */
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

    /**
     * 处理首个非空值，并将结果传给后续步骤。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个非空值结果，供调用方继续处理
     */
    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    /**
     * 整理字符串对象映射数据，供调用方遍历或继续处理。
     *
     * @param source 待处理字符串对象映射的原始输入，结果供调用方继续使用
     * @return 字符串对象映射键值结果，供调用方继续处理
     */
    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 供发布校验和部署运行时共用的只读兼容视图。
     *
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param visible 可见，保存在对象中供后续校验、查询或展示
     * @param editable 可编辑，保存在对象中供后续校验、查询或展示
     * @param sourceType 来源类型标识，决定后续规范化选择采用的处理分支
     * @param rawScopes 原始{@code scopes}，保存在对象中供后续校验、查询或展示
     * @param resolverCode 解析器编码，后续用于处理规范化选择时定位或关联目标
     * @param extraParams 附加参数，后续传给解析器或执行器
     * @param invalidSourceShape 无效来源{@code shape}，保存在对象中供后续校验、查询或展示
     */
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
