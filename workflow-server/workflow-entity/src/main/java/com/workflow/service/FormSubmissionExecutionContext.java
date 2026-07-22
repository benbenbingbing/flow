package com.workflow.service;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

    public static FormSubmissionExecutionContext standalone(
            String operation) {
        return new FormSubmissionExecutionContext(
                "srv_" + UUID.randomUUID(),
                operation,
                Map.of());
    }

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
