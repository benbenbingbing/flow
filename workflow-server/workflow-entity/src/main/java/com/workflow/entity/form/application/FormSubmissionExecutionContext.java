package com.workflow.entity.form.application;

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
 * 避免重复提交或重复执行接口扩展绑定。</p>
 *
 * @param businessTraceKey 单次业务提交及其重试共用的追踪键；后续参与接口绑定幂等键生成，不可为空
 * @param operation 业务操作类型；参与幂等材料，避免不同动作复用绑定结果
 * @param attributes       传给绑定运行时的附加上下文，也参与输入指纹；为空时视为空 Map
 */
public record FormSubmissionExecutionContext(
        String businessTraceKey,
        String operation,
        Map<String, Object> attributes) {

    /**
     * 保证追踪键可用，并复制附加属性以免调用方后续修改改变幂等输入。
     *
     * @param businessTraceKey 单次提交及重试共用的追踪键，后续参与绑定幂等键
     * @param operation 操作类型，为空时按 FORM_SUBMIT 参与幂等材料
     * @param attributes 附加运行上下文，复制后参与绑定输入指纹
     * @throws IllegalArgumentException 追踪键为空时抛出
     */
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
     * @param operation 业务操作类型；参与幂等材料，避免不同动作复用绑定结果
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
     * @param formId 表单 ID，避免不同表单共用追踪键时发生碰撞
     * @param ownerKey 表单、字段或子表行归属键，区分绑定来源
     * @param extensionId 接口扩展 ID，区分不同 Provider 调用
     * @param bindingIndex 同一归属对象内的绑定序号，区分执行步骤
     * @return 以 fbs_ 为前缀的幂等键
     */
    public String bindingIdempotencyKey(
            String formId,
            String ownerKey,
            String extensionId,
            int bindingIndex) {
        return bindingIdempotencyKey(
                formId,
                null,
                ownerKey,
                extensionId,
                bindingIndex);
    }

    /**
     * 生成表单绑定执行的幂等键（含发布版本，保证发布前后幂等键不同）。
     *
     * @param formId 表单 ID，避免不同表单共用追踪键时发生碰撞
     * @param formReleaseId 表单发布版本 ID；变更后生成新的幂等键
     * @param ownerKey 表单、字段或子表行归属键，区分绑定来源
     * @param extensionId 接口扩展 ID，区分不同 Provider 调用
     * @param bindingIndex 同一归属对象内的绑定序号，区分执行步骤
     * @return 以 fbs_ 为前缀的幂等键
     */
    public String bindingIdempotencyKey(
            String formId,
            String formReleaseId,
            String ownerKey,
            String extensionId,
            int bindingIndex) {
        return bindingIdempotencyKey(
                formId,
                formReleaseId,
                ownerKey,
                extensionId,
                bindingIndex,
                null);
    }

    /**
     * 生成包含绑定输入指纹的幂等键。
     *
     * <p>同一业务追踪键可能被客户端复用于多次预览。把规范化输入指纹纳入
     * 幂等材料，避免表单值变化后受控 Provider 仍按旧键返回上一次映射。</p>
     *
     * @param formId 表单 ID，避免不同表单共用追踪键时发生碰撞
     * @param formReleaseId 表单发布版本 ID；变更后生成新的幂等键
     * @param ownerKey 表单、字段或子表行归属键，区分绑定来源
     * @param extensionId 接口扩展 ID，区分不同 Provider 调用
     * @param bindingIndex 同一归属对象内的绑定序号，区分执行步骤
     * @param inputFingerprint 规范化绑定输入指纹，数据变化后避免读取旧响应
     * @return 以 fbs_ 为前缀的幂等键
     */
    public String bindingIdempotencyKey(
            String formId,
            String formReleaseId,
            String ownerKey,
            String extensionId,
            int bindingIndex,
            String inputFingerprint) {
        String material = String.join(
                "|",
                businessTraceKey,
                operation,
                value(formId),
                value(formReleaseId),
                value(ownerKey),
                value(extensionId),
                String.valueOf(bindingIndex),
                value(inputFingerprint));
        return "fbs_" + sha256(material);
    }

    /**
     * 构建运行时上下文 Map，合并附加属性与追踪键、操作类型。
     *
     * @return 供接口扩展读取的上下文副本；合并后的追踪键和操作类型用于关联调用
     */
    public Map<String, Object> runtimeContext() {
        Map<String, Object> result =
                new LinkedHashMap<>(attributes);
        result.put("businessTraceKey", businessTraceKey);
        result.put("submissionOperation", operation);
        return result;
    }

    /**
     * 幂等材料中用空段表达可选参数缺失，保证重试序列化一致。
     *
     * @param value 可能缺失的幂等键材料；空值转换为空段以保持重试输入稳定
     * @return 非空原值或空字符串
     */
    private static String value(String value) {
        return value == null ? "" : value;
    }

    /**
     * 对完整幂等材料取十六进制摘要，避免把原始业务数据暴露在外部请求键中。
     *
     * @param value 完整幂等材料，摘要后作为外部请求键的一部分
     * @return 小写十六进制 SHA-256 摘要
     * @throws IllegalStateException 运行环境不支持 SHA-256
     */
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
