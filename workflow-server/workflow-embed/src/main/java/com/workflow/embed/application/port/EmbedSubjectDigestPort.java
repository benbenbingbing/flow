package com.workflow.embed.application.port;

import com.workflow.embed.domain.SubjectDigest;
import java.util.List;

/** Computes versioned, application/provider-isolated HMAC digests for external subjects. */
public interface EmbedSubjectDigestPort {

    /**
     * 处理摘要，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理摘要时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理摘要时定位或关联目标
     * @param namespace 命名空间，供本方法处理摘要时使用
     * @param externalSubject 外部主体，供本方法处理摘要时使用
     * @return 处理后的摘要结果，供调用方继续处理
     */
    SubjectDigest digest(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject);

    /**
     * Computes the current and still-accepted rotation-window digests. The current digest must be
     * first; implementations backed by a single key may keep the default behavior.
     *
     * @param applicationId 应用ID，后续用于处理{@code accepted}时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理{@code accepted}时定位或关联目标
     * @param namespace 命名空间，供本方法处理{@code accepted}时使用
     * @param externalSubject 外部主体，供本方法处理{@code accepted}时使用
     * @return 主体摘要集合，供调用方遍历或展示
     */
    default List<SubjectDigest> accepted(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject) {
        return List.of(digest(
                applicationId, identityProviderId, namespace, externalSubject));
    }
}
