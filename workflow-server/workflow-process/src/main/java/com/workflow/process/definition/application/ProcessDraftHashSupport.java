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

    /**
     * 初始化流程草稿哈希支持，保存构造参数供后续方法使用。
     */
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

    /**
     * 更新流程草稿哈希支持；后续读取或执行将使用更新后的状态。
     *
     * @param digest 摘要，供本方法更新流程草稿哈希支持时使用
     * @param value 待更新流程草稿哈希支持的原始输入，结果供调用方继续使用
     */
    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value == null
                ? new byte[0]
                : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    /**
     * 规范化XML；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化XML的原始输入，结果供调用方继续使用
     * @return 规范化后的XML文本，供调用方比较或展示
     */
    private static String normalizeXml(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
