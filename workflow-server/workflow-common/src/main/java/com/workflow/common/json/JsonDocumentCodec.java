package com.workflow.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Database-independent JSON document codec.
 *
 * <p>The database stores JSON documents as portable large text. Validation,
 * parsing and canonical serialization belong to the application layer.</p>
 */
public final class JsonDocumentCodec {

    public static final int DEFAULT_MAX_LENGTH = 1_048_576;
    public static final int DEFAULT_MAX_DEPTH = 16;
    public static final int DEFAULT_MAX_OBJECT_PROPERTIES = 500;
    public static final int DEFAULT_MAX_ARRAY_ITEMS = 5_000;

    private static final Set<String> FORBIDDEN_KEYS =
            Set.of("__proto__", "prototype", "constructor");

    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;
    private final int maxLength;
    private final int maxDepth;

    public JsonDocumentCodec(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_MAX_LENGTH, DEFAULT_MAX_DEPTH);
    }

    public JsonDocumentCodec(ObjectMapper objectMapper, int maxLength, int maxDepth) {
        this.objectMapper = objectMapper;
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.maxLength = maxLength;
        this.maxDepth = maxDepth;
    }

    public Map<String, Object> readObject(String document, String label) {
        if (isBlank(document)) {
            return new LinkedHashMap<>();
        }
        Object value = read(document, label);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(label + "必须为 JSON 对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    public List<Object> readArray(String document, String label) {
        if (isBlank(document)) {
            return List.of();
        }
        Object value = read(document, label);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(label + "必须为 JSON 数组");
        }
        return List.copyOf(list);
    }

    public <T> T read(String document, TypeReference<T> type, String label) {
        validateLength(document, label);
        try {
            T value = objectMapper.readValue(document, type);
            validateNode(value, label, 0);
            return value;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(label + "不是合法 JSON", exception);
        }
    }

    public Object read(String document, String label) {
        validateLength(document, label);
        try {
            Object value = objectMapper.readValue(document, Object.class);
            validateNode(value, label, 0);
            return value;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(label + "不是合法 JSON", exception);
        }
    }

    public JsonNode readTree(String document, String label) {
        validateLength(document, label);
        try {
            JsonNode node = objectMapper.readTree(document);
            validateNode(objectMapper.convertValue(node, Object.class), label, 0);
            return node;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(label + "不是合法 JSON", exception);
        }
    }

    public String write(Object value, String label) {
        if (value == null) {
            return null;
        }
        validateNode(value, label, 0);
        try {
            String document = objectMapper.writeValueAsString(value);
            validateLength(document, label);
            return document;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(label + "序列化失败", exception);
        }
    }

    public String canonicalize(String document, String label) {
        if (isBlank(document)) {
            return null;
        }
        Object value = read(document, label);
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(label + "规范化失败", exception);
        }
    }

    public String normalizeNullable(String document, String label) {
        return isBlank(document) ? null : canonicalize(document, label);
    }

    public Map<String, Object> ensureSchemaVersion(
            Map<String, Object> source,
            int schemaVersion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", schemaVersion);
        if (source != null) {
            source.forEach((key, value) -> {
                if (!"schemaVersion".equals(key)) {
                    result.put(key, value);
                }
            });
        }
        return result;
    }

    private void validateLength(String document, String label) {
        if (document == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (document.length() > maxLength) {
            throw new IllegalArgumentException(label + "超过最大长度 " + maxLength);
        }
    }

    private void validateNode(Object value, String label, int depth) {
        if (depth > maxDepth) {
            throw new IllegalArgumentException(label + "嵌套层级不能超过 " + maxDepth);
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > DEFAULT_MAX_OBJECT_PROPERTIES) {
                throw new IllegalArgumentException(label + "对象属性过多");
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (FORBIDDEN_KEYS.contains(key)) {
                    throw new IllegalArgumentException(label + "包含禁止的配置键: " + key);
                }
                validateNode(entry.getValue(), label, depth + 1);
            }
        } else if (value instanceof Collection<?> collection) {
            if (collection.size() > DEFAULT_MAX_ARRAY_ITEMS) {
                throw new IllegalArgumentException(label + "数组项过多");
            }
            for (Object child : collection) {
                validateNode(child, label, depth + 1);
            }
        } else if (value != null && value.getClass().isArray()) {
            Object[] values = (Object[]) value;
            if (values.length > DEFAULT_MAX_ARRAY_ITEMS) {
                throw new IllegalArgumentException(label + "数组项过多");
            }
            for (Object child : values) {
                validateNode(child, label, depth + 1);
            }
        } else if (value instanceof String text && text.length() > maxLength) {
            throw new IllegalArgumentException(label + "文本值过长");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
