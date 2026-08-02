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
    ConfigMigrationImportValueSupport(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    Map<String, Object> readMap(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalArgumentException("迁移快照 JSON 格式错误", exception); }
    }
    Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) return new LinkedHashMap<>();
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, child) -> converted.put(String.valueOf(key), child));
        return converted;
    }
    List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) if (item instanceof Map<?, ?>) result.add(mapValue(item));
        return result;
    }
    <T> T convert(Map<String, Object> value, Class<T> type) {
        return objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(value, type);
    }
    String text(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? fallback : String.valueOf(value);
    }
    Integer integerObject(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) return null;
        try { return Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw new IllegalStateException("迁移配置整数格式错误: " + value, exception); }
    }
    List<String> stringList(Object value) {
        Object decoded = decodeDocument(value);
        if (decoded == null) return List.of();
        if (!(decoded instanceof Collection<?> collection)) throw new IllegalStateException("迁移扩展兼容范围必须为数组");
        return collection.stream().map(String::valueOf).toList();
    }
    Map<String, Object> documentMap(Object value) {
        Object decoded = decodeDocument(value);
        if (decoded == null) return Map.of();
        if (!(decoded instanceof Map<?, ?>)) throw new IllegalStateException("迁移扩展配置必须为对象");
        return mapValue(decoded);
    }
    Object decodeDocument(Object value) {
        if (!(value instanceof String document)) return value;
        if (!StringUtils.hasText(document)) return List.of();
        try { return objectMapper.readValue(document, Object.class); }
        catch (Exception exception) { throw new IllegalStateException("迁移扩展 JSON 文档格式错误", exception); }
    }
    EntityDefinition.LifecycleMode lifecycleMode(Map<String, Object> definition) {
        String value = text(definition.get("lifecycleMode"), EntityDefinition.LifecycleMode.STANDALONE.name());
        try { return EntityDefinition.LifecycleMode.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("不支持的实体生命周期模式: " + value); }
    }
    Boolean booleanObject(Object value) { return value == null ? null : booleanValue(value); }
    boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }
}
