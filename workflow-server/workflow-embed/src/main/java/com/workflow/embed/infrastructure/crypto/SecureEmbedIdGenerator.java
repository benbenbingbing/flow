package com.workflow.embed.infrastructure.crypto;

import com.workflow.embed.application.port.EmbedIdGeneratorPort;
import java.util.UUID;

/** UUID-based opaque identifiers with a type-specific non-secret prefix. */
public final class SecureEmbedIdGenerator implements EmbedIdGeneratorPort {

    /**
     * 生成下一步启动记录ID文本，供后续匹配或展示。
     *
     * @return 处理后的下一步启动记录ID文本，供调用方比较或展示
     */
    @Override
    public String nextLaunchId() {
        return "lch_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成下一步会话ID文本，供后续匹配或展示。
     *
     * @return 处理后的下一步会话ID文本，供调用方比较或展示
     */
    @Override
    public String nextSessionId() {
        return "ems_" + UUID.randomUUID().toString().replace("-", "");
    }
}
