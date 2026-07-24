package com.workflow.service;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 表单提交执行上下文，贯穿单次提交的追踪键、操作类型与附加属性。
 *
 * <p>提供基于业务追踪键的幂等键生成能力，保证同一提交内相同绑定来源的幂等键稳定且唯一，
 * 避免重复提交或重复执行数据源绑定。</p>
 *
 * @param businessTraceKey 业务追踪键，全局唯一，不可为空
 * @param operation        提交操作类型，为空时默认 FORM_SUBMIT
 * @param attributes       附加属性，为空时视为空 Map
 */
public record FormSubmissionExecutionContext(
        String businessTraceKey,
        String operation,
        Map<String, Object> attributes) {

    public FormSubmissionExecutionContext {
        if (!StringUtils.hasText(businessTraceKey)) {
            throw new IllegalArgumentException("业务追踪键不能为空");
        }
        operation = StringUtils.hasText(operation)
                ? operation : "FORM_SUBMIT";
        attributes = attributes == null
                ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 创建一个独立的服务端提交上下文（非用户提交场景），追踪键随机生成。
     *
     * @param operation 操作类型
     * @return 独立上下文
     */
    public static FormSubmissionExecutionContext standalone(
            String operation) {
        return new FormSubmissionExecutionContext(
                "srv_" + UUID.randomUUID(),
                operation,
                Map.of());
    }

    /**
     * 生成表单绑定执行的幂等键（不含发布版本）。
     *
     * @param formId       表单ID
     * @param ownerKey     绑定归属 key
     * @param sourceId     数据源ID
     * @param bindingIndex 绑定序号
     * @return 以 fbs_ 为前缀的幂等键
     */
    public String bindingIdempotencyKey(
            String formId,
            String ownerKey,
            String sourceId,
            int bindingIndex) {
        return bindingIdempotencyKey(
                formId,
                null,
                ownerKey,
                sourceId,
                bindingIndex);
    }

    /**
     * 生成表单绑定执行的幂等键（含发布版本，保证发布前后幂等键不同）。
     *
     * @param formId         表单ID
     * @param formReleaseId  表单发布版本ID，可为 null
     * @param ownerKey       绑定归属 key
     * @param sourceId       数据源ID
     * @param bindingIndex   绑定序号
     * @return 以 fbs_ 为前缀的幂等键
     */
    public String bindingIdempotencyKey(
            String formId,
            String formReleaseId,
            String ownerKey,
            String sourceId,
            int bindingIndex) {
        String material = String.join(
                "|",
                businessTraceKey,
                operation,
                value(formId),
                value(formReleaseId),
                value(ownerKey),
                value(sourceId),
                String.valueOf(bindingIndex));
        return "fbs_" + sha256(material);
    }

    /**
     * 构建运行时上下文 Map，合并附加属性与追踪键、操作类型。
     *
     * @return 不可变的运行时上下文
     */
    public Map<String, Object> runtimeContext() {
        Map<String, Object> result =
                new LinkedHashMap<>(attributes);
        result.put("businessTraceKey", businessTraceKey);
        result.put("submissionOperation", operation);
        return result;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前运行环境不支持 SHA-256",
                    exception);
        }
    }
}
