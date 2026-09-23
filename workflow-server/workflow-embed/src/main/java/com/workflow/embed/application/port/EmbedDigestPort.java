package com.workflow.embed.application.port;

/** Computes lowercase SHA-256 digests for high-entropy one-time/runtime credentials. */
public interface EmbedDigestPort {

    /**
     * 计算输入内容的 SHA-256 摘要，供后续签名或幂等键使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     */
    String sha256(String value);
}
