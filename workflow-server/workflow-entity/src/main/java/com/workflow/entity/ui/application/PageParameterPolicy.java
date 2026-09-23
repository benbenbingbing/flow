package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;

/** 页面输入参数的公共契约；参数是业务输入，不能作为关系或权限的授权依据。 */
public final class PageParameterPolicy {
    private static final Set<String> TYPES = Set.of("string", "number", "integer", "boolean", "object", "array");
    private static final Set<String> SOURCES = Set.of("FIELD", "RECORD_ID", "PARAMETER", "LITERAL");
    private static final Set<String> OPS = Set.of("EQ", "NE", "LIKE", "IN");
    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * 初始化分页参数策略，保存构造参数供后续方法使用。
     */
    private PageParameterPolicy() {}

    /**
     * 保存/发布时检查参数及用途；字段存在性由所属页面校验器检查。
     *
     * @param config 配置内容，决定后续分页参数策略的处理规则
     * @param kind 类型，供本方法校验分页参数策略时使用
     * @param fields 字段集合，后续逐项校验、转换或持久化
     */
    public static void validate(Map<String, Object> config, String kind, Set<String> fields) {
        Map<String, Object> schema = map(config.get("inputParameterSchema"));
        Map<String, Object> properties = map(schema.get("properties"));
        for (var entry : properties.entrySet()) {
            identifier(entry.getKey());
            Map<String, Object> definition = map(entry.getValue());
            String type = String.valueOf(definition.getOrDefault("type", "string"));
            if (!TYPES.contains(type)) throw new IllegalArgumentException("输入参数类型不支持: " + type);
            if (definition.containsKey("default")) convert(definition.get("default"), type);
        }
        for (Object key : list(schema.get("required"))) {
            if (!properties.containsKey(String.valueOf(key))) throw new IllegalArgumentException("必填参数未声明: " + key);
        }
        Set<String> targets = new HashSet<>();
        for (Object value : list(config.get("inputParameterBindings"))) {
            Map<String, Object> binding = map(value);
            String parameter = String.valueOf(binding.get("parameter"));
            String field = String.valueOf(binding.get("targetField"));
            identifier(field);
            if (!properties.containsKey(parameter)) throw new IllegalArgumentException("参数用途引用了未声明参数: " + parameter);
            if (!targets.add(field)) throw new IllegalArgumentException("字段重复配置参数用途: " + field);
            if (fields != null && !fields.contains(field)) throw new IllegalArgumentException("参数用途字段不可用: " + field);
            if ("LIST".equals(kind)) {
                if (!"FILTER".equals(binding.get("usage")) || !OPS.contains(binding.getOrDefault("operator", "EQ")))
                    throw new IllegalArgumentException("列表参数只能用于附加查询条件");
            } else if (!"INITIALIZE".equals(binding.get("usage")) || "id".equals(field)) {
                throw new IllegalArgumentException("表单参数只能初始化可编辑字段");
            }
        }
    }

    /**
     * 运行时使用目标发布声明补默认值和类型；未声明的输入不进入业务上下文。
     *
     * @param config 配置内容，决定后续分页参数策略的处理规则
     * @param supplied {@code supplied}，供本方法解析分页参数策略时使用
     * @return 分页参数策略键值结果，供调用方继续处理
     */
    public static Map<String, Object> resolve(Map<String, Object> config, Map<String, Object> supplied) {
        if (!config.containsKey("inputParameterSchema")) return supplied == null ? Map.of() : new LinkedHashMap<>(supplied);
        Map<String, Object> schema = map(config.get("inputParameterSchema"));
        Map<String, Object> properties = map(schema.get("properties"));
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : properties.entrySet()) {
            identifier(entry.getKey());
            Map<String, Object> definition = map(entry.getValue());
            Object value = supplied != null && supplied.containsKey(entry.getKey()) ? supplied.get(entry.getKey()) : definition.get("default");
            if (value != null) result.put(entry.getKey(), convert(value, String.valueOf(definition.getOrDefault("type", "string"))));
        }
        for (Object required : list(schema.get("required"))) {
            if (empty(result.get(String.valueOf(required)))) throw new IllegalArgumentException("页面缺少必填输入参数: " + required);
        }
        return result;
    }

    /**
     * 由发布用途生成附加过滤，调用方必须与关系、固定条件和权限范围求交。
     *
     * @param config 配置内容，决定后续过滤条件的处理规则
     * @param parameters 参数集合，供本方法处理过滤条件时使用
     * @return 过滤条件键值结果，供调用方继续处理
     */
    public static Map<String, Object> filters(Map<String, Object> config, Map<String, Object> parameters) {
        Map<String, Object> filters = new LinkedHashMap<>();
        for (Object value : list(config.get("inputParameterBindings"))) {
            Map<String, Object> binding = map(value);
            if (!"FILTER".equals(binding.get("usage"))) continue;
            Object input = parameters.get(String.valueOf(binding.get("parameter")));
            if (empty(input)) continue;
            String field = String.valueOf(binding.get("targetField"));
            identifier(field);
            filters.put(field, input);
            filters.put(field + "_op", binding.getOrDefault("operator", "EQ"));
        }
        return filters;
    }

    /**
     * 来源映射保存结构校验，不执行表达式；固定对象值也必须符合 JSON 数据结构。
     *
     * @param value 待处理映射集合的原始输入，结果供调用方继续使用
     * @return 分页参数策略集合，供调用方遍历或展示
     */
    public static List<Map<String, Object>> mappings(Object value) {
        List<?> rows = list(value);
        if (rows.size() > 50) throw new IllegalArgumentException("最多配置 50 个参数映射");
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object row : rows) {
            Map<String, Object> mapping = map(row);
            if (!Set.of("parameter", "sourceType", "sourceField", "value").containsAll(mapping.keySet())) throw new IllegalArgumentException("参数映射包含不支持的属性");
            String key = String.valueOf(mapping.get("parameter"));
            identifier(key);
            if (!seen.add(key)) throw new IllegalArgumentException("参数重复映射: " + key);
            if (!SOURCES.contains(mapping.get("sourceType"))) throw new IllegalArgumentException("参数来源类型不支持");
            if (Set.of("FIELD", "PARAMETER").contains(mapping.get("sourceType"))) identifier(String.valueOf(mapping.get("sourceField")));
            result.add(new LinkedHashMap<>(mapping));
        }
        return result;
    }

    /**
     * 统一读取对象或 JSON 文档；空配置返回空 Map，非对象输入拒绝保存或执行。
     *
     * @param value 待处理映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
    public static Map<String, Object> map(Object value) {
        if (value == null) return Map.of();
        if (value instanceof String text) {
            if (text.isBlank()) return Map.of();
            try { return map(JSON.readValue(text, Object.class)); }
            catch (Exception e) { throw new IllegalArgumentException("参数配置必须为 JSON 对象", e); }
        }
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("参数配置必须为对象");
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
    /**
     * 列出分页参数策略；查询结果供调用方展示或继续处理。
     *
     * @param value 待列出分页参数策略的原始输入，结果供调用方继续使用
     * @return {@code list<?>}集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static List<?> list(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> rows)) throw new IllegalArgumentException("参数配置必须为数组");
        return rows;
    }
    /**
     * 处理标识符，并将结果传给后续步骤。
     *
     * @param value 待处理标识符的原始输入，结果供调用方继续使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void identifier(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_]{0,99}") || Set.of("constructor", "prototype", "__proto__", "null").contains(value))
            throw new IllegalArgumentException("参数或字段编码不合法: " + value);
    }
    /**
     * 判断空条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理空的原始输入，结果供调用方继续使用
     * @return 空条件成立时为 true，否则为 false
     */
    private static boolean empty(Object value) {
        return value == null || "".equals(value) || (value instanceof Collection<?> rows && rows.isEmpty());
    }
    /**
     * 转换分页参数策略；输出作为后续校验或处理的输入。
     *
     * @param value 待转换分页参数策略的原始输入，结果供调用方继续使用
     * @param type 类型标识，决定后续分页参数策略采用的处理分支
     * @return 转换后的分页参数策略结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static Object convert(Object value, String type) {
        if (value == null) return null;
        try {
            return switch (type) {
                case "string" -> { if (value instanceof Map || value instanceof Collection) throw new IllegalArgumentException(); yield String.valueOf(value); }
                case "number", "integer" -> { BigDecimal number = new BigDecimal(String.valueOf(value)); if ("integer".equals(type)) number.toBigIntegerExact(); yield number; }
                case "boolean" -> { if (Boolean.TRUE.equals(value) || "true".equals(value)) yield true; if (Boolean.FALSE.equals(value) || "false".equals(value)) yield false; throw new IllegalArgumentException(); }
                case "object", "array" -> { Object parsed = value instanceof String text ? JSON.readValue(text, Object.class) : value; if ("object".equals(type) ? !(parsed instanceof Map) : !(parsed instanceof List)) throw new IllegalArgumentException(); yield parsed; }
                default -> throw new IllegalArgumentException();
            };
        } catch (Exception e) { throw new IllegalArgumentException("输入参数类型不匹配: " + type, e); }
    }
}
