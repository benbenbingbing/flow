package com.workflow.migration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Normalizes imported snapshot values and tolerates older package shapes. */
@Component
final class ConfigMigrationImportValueSupport {
    private final ObjectMapper objectMapper;
    /**
     * 初始化配置迁移导入值支持，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    ConfigMigrationImportValueSupport(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    /**
     * 读取键值配置，供后续规则或接口处理使用。
     *
     * @param value 待读取映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    Map<String, Object> readMap(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalArgumentException("迁移快照 JSON 格式错误", exception); }
    }
    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) return new LinkedHashMap<>();
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, child) -> converted.put(String.valueOf(key), child));
        return converted;
    }
    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 配置迁移导入值支持集合，供调用方遍历或展示
     */
    List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) if (item instanceof Map<?, ?>) result.add(mapValue(item));
        return result;
    }
    /**
     * 转换配置迁移导入值支持；输出作为后续校验或处理的输入。
     *
     * @param value 待转换配置迁移导入值支持的原始输入，结果供调用方继续使用
     * @param type 类型标识，决定后续配置迁移导入值支持采用的处理分支
     * @return 转换后的配置迁移导入值支持结果，供调用方继续处理
     */
    <T> T convert(Map<String, Object> value, Class<T> type) {
        return objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(value, type);
    }
    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的文本文本，供调用方比较或展示
     */
    String text(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? fallback : String.valueOf(value);
    }
    /**
     * 处理整数对象，并将结果传给后续步骤。
     *
     * @param value 待处理整数对象的原始输入，结果供调用方继续使用
     * @return 处理后的整数对象结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    Integer integerObject(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) return null;
        try { return Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw new IllegalStateException("迁移配置整数格式错误: " + value, exception); }
    }
    /**
     * 整理字符串列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串列表的原始输入，结果供调用方继续使用
     * @return 配置迁移导入值支持集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    List<String> stringList(Object value) {
        Object decoded = decodeDocument(value);
        if (decoded == null) return List.of();
        if (!(decoded instanceof Collection<?> collection)) throw new IllegalStateException("迁移扩展兼容范围必须为数组");
        return collection.stream().map(String::valueOf).toList();
    }
    /**
     * 整理文档映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理文档映射的原始输入，结果供调用方继续使用
     * @return 文档映射键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    Map<String, Object> documentMap(Object value) {
        Object decoded = decodeDocument(value);
        if (decoded == null) return Map.of();
        if (!(decoded instanceof Map<?, ?>)) throw new IllegalStateException("迁移扩展配置必须为对象");
        return mapValue(decoded);
    }
    /**
     * 解码文档；输出作为后续校验或处理的输入。
     *
     * @param value 待解码文档的原始输入，结果供调用方继续使用
     * @return 解码后的文档结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    Object decodeDocument(Object value) {
        if (!(value instanceof String document)) return value;
        if (!StringUtils.hasText(document)) return List.of();
        try { return objectMapper.readValue(document, Object.class); }
        catch (Exception exception) { throw new IllegalStateException("迁移扩展 JSON 文档格式错误", exception); }
    }
    /**
     * 处理生命周期模式，并将结果传给后续步骤。
     *
     * @param definition 定义，作为 {@code text} 的输入影响后续处理
     * @return 处理后的生命周期模式结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    EntityDefinition.LifecycleMode lifecycleMode(Map<String, Object> definition) {
        String value = text(definition.get("lifecycleMode"), EntityDefinition.LifecycleMode.STANDALONE.name());
        try { return EntityDefinition.LifecycleMode.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("不支持的实体生命周期模式: " + value); }
    }
    /**
     * 处理布尔值对象，并将结果传给后续步骤。
     *
     * @param value 待处理布尔值对象的原始输入，结果供调用方继续使用
     * @return 处理后的布尔值对象结果，供调用方继续处理
     */
    Boolean booleanObject(Object value) { return value == null ? null : booleanValue(value); }
    /**
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param value 待处理布尔值值的原始输入，结果供调用方继续使用
     * @return 布尔值值条件成立时为 true，否则为 false
     */
    boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }
}
