package com.workflow.embed.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Authenticated Embed actor and immutable runtime target exposed to the request adapter.
 *
 * @param sessionId 会话ID，后续用于处理已认证嵌入式会话时定位或关联目标
 * @param applicationId 应用ID，后续用于处理已认证嵌入式会话时定位或关联目标
 * @param grantId 授权ID，后续用于处理已认证嵌入式会话时定位或关联目标
 * @param identityProviderId 身份提供者ID，后续用于处理已认证嵌入式会话时定位或关联目标
 * @param identityBindingId 身份绑定ID，后续用于处理已认证嵌入式会话时定位或关联目标
 * @param viewId 视图ID，后续用于处理已认证嵌入式会话时定位或关联目标
 * @param viewReleaseId 视图发布版本ID，后续用于处理已认证嵌入式会话时定位或关联目标
 * @param flowUserId 流程用户ID，后续用于处理已认证嵌入式会话时定位或关联目标
 * @param flowUsername 流程用户名，后续用于处理已认证嵌入式会话时匹配或展示
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param channelId 通道ID，后续用于处理已认证嵌入式会话时定位或关联目标
 * @param entryMode 入口模式标识，决定后续已认证嵌入式会话采用的处理分支
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param context 执行上下文，向后续已认证嵌入式会话步骤传递身份、配置或状态
 * @param capabilities 能力集合，保存在对象中供后续校验、查询或展示
 * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
 */
public record AuthenticatedEmbedSession(
        String sessionId,
        String applicationId,
        String grantId,
        String identityProviderId,
        String identityBindingId,
        String viewId,
        String viewReleaseId,
        String flowUserId,
        String flowUsername,
        String parentOrigin,
        String channelId,
        String entryMode,
        String recordId,
        Map<String, Object> context,
        Set<String> capabilities,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt) {

    /**
     * 兼容只读用例和既有测试的构造器；写操作必须使用包含 Provider/Binding 的完整会话。
     *
     * @param sessionId 会话ID，后续用于初始化已认证嵌入式会话时定位或关联目标
     * @param applicationId 应用ID，后续用于初始化已认证嵌入式会话时定位或关联目标
     * @param grantId 授权ID，后续用于初始化已认证嵌入式会话时定位或关联目标
     * @param viewId 视图ID，后续用于初始化已认证嵌入式会话时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于初始化已认证嵌入式会话时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于初始化已认证嵌入式会话时定位或关联目标
     * @param flowUsername 流程用户名，后续用于初始化已认证嵌入式会话时匹配或展示
     * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
     * @param channelId 通道ID，后续用于初始化已认证嵌入式会话时定位或关联目标
     * @param entryMode 入口模式标识，决定后续已认证嵌入式会话采用的处理分支
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param context 执行上下文，向后续已认证嵌入式会话步骤传递身份、配置或状态
     * @param capabilities 能力集合，保存在对象中供后续校验、查询或展示
     * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
     */
    public AuthenticatedEmbedSession(
            String sessionId,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String flowUserId,
            String flowUsername,
            String parentOrigin,
            String channelId,
            String entryMode,
            String recordId,
            Map<String, Object> context,
            Set<String> capabilities,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt) {
        this(
                sessionId, applicationId, grantId, null, null,
                viewId, viewReleaseId, flowUserId, flowUsername,
                parentOrigin, channelId, entryMode, recordId, context,
                capabilities, idleExpiresAt, absoluteExpiresAt);
    }

    /**
     * 生成当前对象的文本表示，供日志和排障使用。
     *
     * @return 转换为后的字符串文本，供调用方比较或展示
     */
    @Override
    public String toString() {
        return "AuthenticatedEmbedSession[sessionId=" + sessionId
                + ", applicationId=" + applicationId
                + ", identityProviderId=" + identityProviderId
                + ", viewId=" + viewId
                + ", viewReleaseId=" + viewReleaseId
                + ", flowUserId=" + flowUserId
                + ", context=<redacted>, capabilities=<redacted>"
                + ", idleExpiresAt=" + idleExpiresAt
                + ", absoluteExpiresAt=" + absoluteExpiresAt + "]";
    }
}
