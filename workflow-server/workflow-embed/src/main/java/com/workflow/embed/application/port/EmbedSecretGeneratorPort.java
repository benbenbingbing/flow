package com.workflow.embed.application.port;

/** Generates high-entropy URL-safe launch and session secrets. */
public interface EmbedSecretGeneratorPort {

    /**
     * 生成嵌入式密钥生成器；结果供调用方的后续步骤使用。
     *
     * @param bytes 字节，供本方法生成嵌入式密钥生成器时使用
     * @return 生成后的嵌入式密钥生成器文本，供调用方比较或展示
     */
    String generate(int bytes);
}
