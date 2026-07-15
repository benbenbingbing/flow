package com.workflow.service.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StructuredConfigValidator {

    private static final int MAX_JSON_LENGTH = 65_535;
    private static final int MAX_DEPTH = 8;
    private static final Set<String> FORBIDDEN_KEYS = Set.of("__proto__", "prototype", "constructor");

    private final ObjectMapper objectMapper;

    public Map<String, Object> parseObject(String json, String label) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        if (json.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException(label + "超过最大长度");
        }
        try {
            Map<String, Object> value = objectMapper.readValue(json, new TypeReference<>() {});
            if (value == null) {
                throw new IllegalArgumentException(label + "必须为 JSON 对象");
            }
            validateNode(value, label, 0);
            return value;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "不是合法 JSON 对象");
        }
    }

    public Object parseJson(String json, String label) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        if (json.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException(label + "超过最大长度");
        }
        try {
            Object value = objectMapper.readValue(json, Object.class);
            validateNode(value, label, 0);
            return value;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "不是合法 JSON");
        }
    }

    private void validateNode(Object value, String label, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(label + "嵌套层级不能超过 " + MAX_DEPTH);
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 100) {
                throw new IllegalArgumentException(label + "配置项过多");
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (FORBIDDEN_KEYS.contains(key)) {
                    throw new IllegalArgumentException(label + "包含禁止的配置键: " + key);
                }
                validateNode(entry.getValue(), label, depth + 1);
            }
        } else if (value instanceof List<?> list) {
            if (list.size() > 500) {
                throw new IllegalArgumentException(label + "数组项过多");
            }
            for (Object item : list) {
                validateNode(item, label, depth + 1);
            }
        } else if (value instanceof String text && text.length() > 20_000) {
            throw new IllegalArgumentException(label + "文本配置过长");
        }
    }
}
