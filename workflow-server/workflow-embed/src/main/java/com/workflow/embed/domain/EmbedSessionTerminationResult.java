package com.workflow.embed.domain;

/**
 * Session 终止结果的安全摘要。
 *
 * <p>该对象只携带审计和管理响应所需的内部标识，不包含 token 摘要、nonce、Subject 或上下文。</p>
 *
 * @param outcome 结果，保存在对象中供后续校验、查询或展示
 * @param transitioned {@code transitioned}，保存在对象中供后续校验、查询或展示
 * @param sessionId 会话ID，后续用于处理嵌入式会话终止结果时定位或关联目标
 * @param applicationId 应用ID，后续用于处理嵌入式会话终止结果时定位或关联目标
 * @param viewId 视图ID，后续用于处理嵌入式会话终止结果时定位或关联目标
 * @param flowUserId 流程用户ID，后续用于处理嵌入式会话终止结果时定位或关联目标
 * @param status 状态标识，决定后续嵌入式会话终止结果采用的处理分支
 */
public record EmbedSessionTerminationResult(
        EmbedSessionTermination outcome,
        boolean transitioned,
        String sessionId,
        String applicationId,
        String viewId,
        String flowUserId,
        String status) {

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @return 处理后的无效结果，供调用方继续处理
     */
    public static EmbedSessionTerminationResult invalid() {
        return new EmbedSessionTerminationResult(
                EmbedSessionTermination.INVALID, false, null, null, null, null, null);
    }
}
