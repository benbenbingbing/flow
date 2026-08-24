package com.workflow.process.definition.application;

import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 计算流程设计草稿的稳定内容哈希。
 *
 * <p>每个字段使用长度前缀写入摘要，避免简单字符串拼接产生边界歧义；BPMN 仅统一换行符，
 * 不擅自裁剪设计内容。该哈希用于幂等保存、发布漂移检查和后续三方比较，不作为授权凭证。</p>
 */
final class ProcessDraftHashSupport {

    private ProcessDraftHashSupport() {
    }

    /**
     * 计算持久化流程草稿的 SHA-256。
     *
     * @param config 流程草稿
     * @return 小写十六进制 SHA-256
     */
    static String hash(ProcessDefinitionConfig config) {
        return hash(
                config.getProcessKey(),
                config.getProcessName(),
                config.getDescription(),
                config.getCategory(),
                config.getBpmnXml());
    }

    /**
     * 按流程设计字段计算 SHA-256。
     *
     * @param processKey  流程稳定标识
     * @param processName 流程名称
     * @param description 流程描述
     * @param category    流程分类
     * @param bpmnXml     BPMN 设计 XML
     * @return 小写十六进制 SHA-256
     */
    static String hash(
            String processKey,
            String processName,
            String description,
            String category,
            String bpmnXml) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, processKey);
            update(digest, processName);
            update(digest, description);
            update(digest, category);
            update(digest, normalizeXml(bpmnXml));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value == null
                ? new byte[0]
                : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String normalizeXml(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
