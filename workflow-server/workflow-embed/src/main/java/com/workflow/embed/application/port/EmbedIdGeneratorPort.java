package com.workflow.embed.application.port;

/** Creates opaque internal identifiers without relying on the HTTP or persistence layer. */
public interface EmbedIdGeneratorPort {

    /**
     * 生成下一步启动记录ID文本，供后续匹配或展示。
     *
     * @return 处理后的下一步启动记录ID文本，供调用方比较或展示
     */
    String nextLaunchId();

    /**
     * 生成下一步会话ID文本，供后续匹配或展示。
     *
     * @return 处理后的下一步会话ID文本，供调用方比较或展示
     */
    String nextSessionId();
}
