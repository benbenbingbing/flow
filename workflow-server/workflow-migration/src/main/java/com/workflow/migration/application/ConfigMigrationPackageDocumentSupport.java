package com.workflow.migration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Keeps migration package JSON conversion out of the application facade. */
@Component
final class ConfigMigrationPackageDocumentSupport {
    private final ObjectMapper objectMapper;

    /**
     * 初始化配置迁移包文档支持，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    ConfigMigrationPackageDocumentSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理整数值，并将结果传给后续步骤。
     *
     * @param value 待处理整数值的原始输入，结果供调用方继续使用
     * @return 处理后的整数值结果，供调用方继续处理
     */
    Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    /**
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param value 待处理布尔值值的原始输入，结果供调用方继续使用
     * @return 布尔值值条件成立时为 true，否则为 false
     */
    boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * 写入JSON；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入JSON的原始输入，结果供调用方继续使用
     * @return 写入后的JSON文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("配置迁移 JSON 序列化失败", exception); }
    }

    /**
     * 解析JSON；输出作为后续校验或处理的输入。
     *
     * @param value 待解析JSON的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 解析后的JSON结果，供调用方继续处理
     */
    Object parseJson(String value, Object fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return objectMapper.readValue(value, Object.class); }
        catch (JsonProcessingException exception) { return fallback; }
    }

    /**
     * 读取键值配置，供后续规则或接口处理使用。
     *
     * @param value 待读取映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("迁移快照 JSON 格式错误", exception); }
    }

    /**
     * 读取映射列表；查询结果供调用方展示或继续处理。
     *
     * @param value 待读取映射列表的原始输入，结果供调用方继续使用
     * @return 配置迁移包文档支持集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    List<Map<String, Object>> readMapList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("迁移依赖 JSON 格式错误", exception); }
    }

    /**
     * 读取映射列表；查询结果供调用方展示或继续处理。
     *
     * @param value 待读取映射列表的原始输入，结果供调用方继续使用
     * @return 配置迁移包文档支持集合，供调用方遍历或展示
     */
    List<Map<String, Object>> readMapList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((key, child) -> converted.put(String.valueOf(key), child));
                result.add(converted);
            }
        }
        return result;
    }
}
