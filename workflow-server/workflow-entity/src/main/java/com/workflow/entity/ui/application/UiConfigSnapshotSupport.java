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

    /**
     * 生成规范文本，供后续匹配或展示。
     *
     * @param snapshot 快照，作为 {@code codec.write} 的输入影响后续处理
     * @return 处理后的规范文本，供调用方比较或展示
     */
    public String canonical(Map<String, Object> snapshot) {
        String document = codec.write(snapshot, "UI配置快照");
        return codec.canonicalize(document, "UI配置快照");
    }

    /**
     * 判断{@code equivalent}条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，作为 {@code codec.write} 的输入影响后续处理
     * @param right 右侧，作为 {@code codec.write} 的输入影响后续处理
     * @return {@code equivalent}条件成立时为 true，否则为 false
     */
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

    /**
     * 整理稳定映射数据，供调用方遍历或继续处理。
     *
     * @param source 待处理稳定映射的原始输入，结果供调用方继续使用
     * @return 稳定映射键值结果，供调用方继续处理
     */
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

    /**
     * 处理稳定值，并将结果传给后续步骤。
     *
     * @param source 待处理稳定值的原始输入，结果供调用方继续使用
     * @return 处理后的稳定值结果，供调用方继续处理
     */
    public Object stableValue(Object source) {
        return stripVolatile(
                objectMapper.convertValue(source, Object.class));
    }

    /**
     * 生成哈希文本，供后续匹配或展示。
     *
     * @param value 待处理哈希的原始输入，结果供调用方继续使用
     * @return 处理后的哈希文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 处理{@code strip}{@code volatile}，并将结果传给后续步骤。
     *
     * @param value 待处理{@code strip}{@code volatile}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code strip}{@code volatile}结果，供调用方继续处理
     */
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
