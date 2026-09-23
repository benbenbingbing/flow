package com.workflow.embed.domain;

import java.time.Instant;

/**
 * Exact external subject to Flow user binding.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param applicationId 应用ID，后续用于处理嵌入式外部身份绑定时定位或关联目标
 * @param identityProviderId 身份提供者ID，后续用于处理嵌入式外部身份绑定时定位或关联目标
 * @param subjectDigest 主体摘要，保存在对象中供后续校验、查询或展示
 * @param subjectDigestKeyVersion 主体摘要键版本，保存在对象中供后续校验、查询或展示
 * @param flowUserId 流程用户ID，后续用于处理嵌入式外部身份绑定时定位或关联目标
 * @param status 状态标识，决定后续嵌入式外部身份绑定采用的处理分支
 * @param bindingVersion 绑定版本，保存在对象中供后续校验、查询或展示
 * @param effectiveAt 有效时间，后续用于判断有效期或展示该事件的发生时间
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 */
public record EmbedExternalIdentityBinding(
        String id,
        String applicationId,
        String identityProviderId,
        String subjectDigest,
        String subjectDigestKeyVersion,
        String flowUserId,
        String status,
        long bindingVersion,
        Instant effectiveAt,
        Instant expiresAt) {

    /**
     * 判断是否活动时间；判断结果决定调用方的后续分支。
     *
     * @param now 当前时间，供本方法判断是否活动时间时使用
     * @return 活动时间条件成立时为 true，否则为 false
     */
    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(status)
                && !effectiveAt.isAfter(now)
                && (expiresAt == null || expiresAt.isAfter(now));
    }
}
