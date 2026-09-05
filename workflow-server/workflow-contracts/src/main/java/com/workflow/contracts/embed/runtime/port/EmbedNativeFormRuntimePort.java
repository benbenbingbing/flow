package com.workflow.contracts.embed.runtime.port;

import java.time.Instant;

/**
 * 为 Embed iframe 复用 Flow 原生 Published Form 运行时签发短期解析上下文。
 */
public interface EmbedNativeFormRuntimePort {

    /** 为固定表单发布目标签发短期解析令牌。 */
    String issueReleaseResolutionToken(Target target);

    /** 验证浏览器回传令牌并恢复精确 Published Form 目标。 */
    VerifiedTarget verifyReleaseResolutionToken(
            String token,
            VerificationTarget target);

    /** 验证表单快照首次解析请求。 */
    VerifiedTarget verifyRuntimeReleaseRequest(
            String token,
            VerificationTarget target);

    /** 服务端可信的原生表单根目标及其不可延长的会话时限。 */
    record Target(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion,
            Instant sessionAbsoluteExpiresAt) {
    }

    /** 浏览器声明、但必须由签名令牌逐项证明的目标坐标。 */
    record VerificationTarget(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion,
            String sessionId,
            String viewReleaseId,
            Instant sessionAbsoluteExpiresAt) {
    }

    /** 已由实体边界验证归属后的可信目标。 */
    record VerifiedTarget(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion) {
    }
}
