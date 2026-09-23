package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedExternalIdentityBinding;
import java.time.Instant;
import java.util.Optional;

/** Resolves an exact, application/provider-scoped external identity binding. */
public interface EmbedExternalIdentityBindingPort {

    /**
     * 查询嵌入式外部身份绑定；结果供调用方展示或继续处理。
     *
     * @param applicationId 应用ID，后续用于查询嵌入式外部身份绑定时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于查询嵌入式外部身份绑定时定位或关联目标
     * @param subjectDigest 主体摘要，供本方法查询嵌入式外部身份绑定时使用
     * @param subjectDigestKeyVersion 主体摘要键版本，供本方法查询嵌入式外部身份绑定时使用
     * @param now 当前时间，供本方法查询嵌入式外部身份绑定时使用
     * @return 匹配的嵌入式外部身份绑定；未找到时为空
     */
    Optional<EmbedExternalIdentityBinding> find(
            String applicationId,
            String identityProviderId,
            String subjectDigest,
            String subjectDigestKeyVersion,
            Instant now);
}
