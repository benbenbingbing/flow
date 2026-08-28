package com.workflow.embed.domain;

/**
 * Session 终止结果的安全摘要。
 *
 * <p>该对象只携带审计和管理响应所需的内部标识，不包含 token 摘要、nonce、Subject 或上下文。</p>
 */
public record EmbedSessionTerminationResult(
        EmbedSessionTermination outcome,
        boolean transitioned,
        String sessionId,
        String applicationId,
        String viewId,
        String flowUserId,
        String status) {

    public static EmbedSessionTerminationResult invalid() {
        return new EmbedSessionTerminationResult(
                EmbedSessionTermination.INVALID, false, null, null, null, null, null);
    }
}
