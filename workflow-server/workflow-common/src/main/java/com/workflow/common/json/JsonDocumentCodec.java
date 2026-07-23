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

    /** JSON 文档默认最大长度（1MB） */
    public static final int DEFAULT_MAX_LENGTH = 1_048_576;
    /** JSON 文档默认最大嵌套层级 */
    public static final int DEFAULT_MAX_DEPTH = 16;
    /** 单个 JSON 对象默认最大属性数量 */
    public static final int DEFAULT_MAX_OBJECT_PROPERTIES = 500;
    /** 单个 JSON 数组默认最大元素数量 */
    public static final int DEFAULT_MAX_ARRAY_ITEMS = 5_000;

    /** 禁止出现的配置键，避免原型链污染等安全风险 */
    private static final Set<String> FORBIDDEN_KEYS =
            Set.of("__proto__", "prototype", "constructor");

    /** 用于读写 JSON 的 ObjectMapper */
    private final ObjectMapper objectMapper;
    /** 用于规范化输出的 ObjectMapper（按 key 排序） */
    private final ObjectMapper canonicalMapper;
    /** JSON 文档最大长度限制 */
    private final int maxLength;
    /** JSON 文档最大嵌套层级限制 */
    private final int maxDepth;

    /**
     * 构造编解码器，使用默认最大长度与嵌套层级。
     *
     * @param objectMapper JSON 序列化/反序列化器
     */
    public JsonDocumentCodec(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_MAX_LENGTH, DEFAULT_MAX_DEPTH);
    }

    /**
     * 构造编解码器。
     *
     * @param objectMapper JSON 序列化/反序列化器
     * @param maxLength     允许的文档最大长度
     * @param maxDepth      允许的最大嵌套层级
     */
    public JsonDocumentCodec(ObjectMapper objectMapper, int maxLength, int maxDepth) {
        this.objectMapper = objectMapper;
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.maxLength = maxLength;
        this.maxDepth = maxDepth;
    }

    /**
     * 将 JSON 文档解析为对象，空文档返回空 Map。
     *
     * @param document JSON 文档字符串
     * @param label    字段标签，用于异常提示
     * @return 解析得到的有序 Map
     * @throws IllegalArgumentException 当文档非空且不是 JSON 对象时抛出
     */
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

    /**
     * 将 JSON 文档解析为数组，空文档返回空列表。
     *
     * @param document JSON 文档字符串
     * @param label    字段标签，用于异常提示
     * @return 不可变的元素列表
     * @throws IllegalArgumentException 当文档非空且不是 JSON 数组时抛出
     */
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

    /**
     * 将 JSON 文档反序列化为指定泛型类型。
     *
     * @param document JSON 文档字符串
     * @param type     目标类型引用
     * @param label    字段标签，用于异常提示
     * @param <T>      目标类型
     * @return 反序列化得到的对象
     * @throws IllegalArgumentException 当文档超长或不是合法 JSON 时抛出
     */
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

    /**
     * 将 JSON 文档解析为通用对象（Map/List/标量）。
     *
     * @param document JSON 文档字符串
     * @param label    字段标签，用于异常提示
     * @return 解析得到的对象
     * @throws IllegalArgumentException 当文档超长或不是合法 JSON 时抛出
     */
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

    /**
     * 将 JSON 文档解析为 {@link JsonNode} 树。
     *
     * @param document JSON 文档字符串
     * @param label    字段标签，用于异常提示
     * @return 解析得到的 JsonNode
     * @throws IllegalArgumentException 当文档超长或不是合法 JSON 时抛出
     */
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

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化的对象
     * @param label 字段标签，用于异常提示
     * @return JSON 字符串，入参为 null 时返回 null
     * @throws IllegalArgumentException 当序列化结果超长或失败时抛出
     */
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

    /**
     * 将 JSON 文档规范化为按 key 排序的稳定字符串。
     *
     * @param document JSON 文档字符串
     * @param label    字段标签，用于异常提示
     * @return 规范化后的 JSON 字符串，空文档返回 null
     * @throws IllegalArgumentException 当文档非法或规范化失败时抛出
     */
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

    /**
     * 将文档规范化，空文档返回 null。
     *
     * @param document JSON 文档字符串
     * @param label    字段标签，用于异常提示
     * @return 规范化后的 JSON 字符串或 null
     */
    public String normalizeNullable(String document, String label) {
        return isBlank(document) ? null : canonicalize(document, label);
    }

    /**
     * 确保文档包含指定的 schemaVersion，原 schemaVersion 会被覆盖。
     *
     * @param source        原始文档内容，可为 null
     * @param schemaVersion 目标 schema 版本号
     * @return 包含指定 schemaVersion 的新 Map
     */
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

    /**
     * 校验文档长度限制。
     *
     * @param document JSON 文档字符串
     * @param label    字段标签，用于异常提示
     * @throws IllegalArgumentException 当文档为 null 或超长时抛出
     */
    private void validateLength(String document, String label) {
        if (document == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (document.length() > maxLength) {
            throw new IllegalArgumentException(label + "超过最大长度 " + maxLength);
        }
    }

    /**
     * 递归校验 JSON 节点的嵌套层级、属性数量、禁止键与文本长度。
     *
     * @param value 当前节点值
     * @param label 字段标签，用于异常提示
     * @param depth 当前嵌套深度
     * @throws IllegalArgumentException 当超出限制或包含禁止键时抛出
     */
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

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待判断字符串
     * @return 为 null 或全空白返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
