package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonicalization, hashing and volatile-value removal for UI snapshots.
 */
@Component
@RequiredArgsConstructor
public class UiConfigSnapshotSupport {

    private static final Set<String> VOLATILE_KEYS = Set.of(
            "revision",
            "activeReleaseId",
            "draftHash",
            "publishedVersion",
            "publishedSnapshot",
            "toolbarCapabilities",
            "createTime",
            "updateTime",
            "createdAt",
            "updatedAt",
            "deleted");

    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    public String canonical(Map<String, Object> snapshot) {
        String document = codec.write(snapshot, "UI配置快照");
        return codec.canonicalize(document, "UI配置快照");
    }

    public boolean equivalent(Object left, Object right) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        String leftDocument = codec.write(left, "UI配置差异左值");
        String rightDocument = codec.write(right, "UI配置差异右值");
        return Objects.equals(
                codec.canonicalize(leftDocument, "UI配置差异左值"),
                codec.canonicalize(rightDocument, "UI配置差异右值"));
    }

    public Map<String, Object> stableMap(Map<String, Object> source) {
        Object value = stableValue(source);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    public Object stableValue(Object source) {
        return stripVolatile(
                objectMapper.convertValue(source, Object.class));
    }

    public String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "计算配置哈希失败",
                    exception);
        }
    }

    private Object stripVolatile(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                String name = String.valueOf(key);
                if (!VOLATILE_KEYS.contains(name)) {
                    Object stableChild = stripVolatile(child);
                    if (stableChild != null) {
                        result.put(name, stableChild);
                    }
                }
            });
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::stripVolatile).toList();
        }
        return value;
    }
}
