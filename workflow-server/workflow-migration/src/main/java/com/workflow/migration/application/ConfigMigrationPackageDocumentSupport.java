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

    ConfigMigrationPackageDocumentSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("配置迁移 JSON 序列化失败", exception); }
    }

    Object parseJson(String value, Object fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return objectMapper.readValue(value, Object.class); }
        catch (JsonProcessingException exception) { return fallback; }
    }

    Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("迁移快照 JSON 格式错误", exception); }
    }

    List<Map<String, Object>> readMapList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("迁移依赖 JSON 格式错误", exception); }
    }

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
