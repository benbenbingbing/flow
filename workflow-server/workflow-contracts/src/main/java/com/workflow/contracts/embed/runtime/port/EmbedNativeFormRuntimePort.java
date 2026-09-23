package com.workflow.contracts.embed.runtime.port;

import java.time.Instant;

/**
 * 为 Embed iframe 复用 Flow 原生 Published Form 运行时签发短期解析上下文。
 */
public interface EmbedNativeFormRuntimePort {

    /**
     * 为固定表单发布目标签发短期解析令牌。
     *
     * @param target 目标，供本方法处理签发发布版本解析令牌时使用
     * @return 处理后的签发发布版本解析令牌文本，供调用方比较或展示
     */
    String issueReleaseResolutionToken(Target target);

    /**
     * 验证浏览器回传令牌并恢复精确 Published Form 目标。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @param target 目标，供本方法验证发布版本解析令牌时使用
     * @return 验证后的发布版本解析令牌结果，供调用方继续处理
     */
    VerifiedTarget verifyReleaseResolutionToken(
            String token,
            VerificationTarget target);

    /**
     * 验证表单快照首次解析请求。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @param target 目标，供本方法验证运行时发布版本请求时使用
     * @return 验证后的运行时发布版本请求结果，供调用方继续处理
     */
    VerifiedTarget verifyRuntimeReleaseRequest(
            String token,
            VerificationTarget target);

    /**
     * 服务端可信的原生表单根目标及其不可延长的会话时限。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param formReleaseId 表单发布版本ID，后续用于处理目标时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     * @param sessionAbsoluteExpiresAt 会话绝对过期时间，后续用于判断有效期或展示该事件的发生时间
     */
    record Target(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion,
            Instant sessionAbsoluteExpiresAt) {
    }

    /**
     * 浏览器声明、但必须由签名令牌逐项证明的目标坐标。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param formReleaseId 表单发布版本ID，后续用于处理{@code verification}目标时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     * @param sessionId 会话ID，后续用于处理{@code verification}目标时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理{@code verification}目标时定位或关联目标
     * @param sessionAbsoluteExpiresAt 会话绝对过期时间，后续用于判断有效期或展示该事件的发生时间
     */
    record VerificationTarget(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion,
            String sessionId,
            String viewReleaseId,
            Instant sessionAbsoluteExpiresAt) {
    }

    /**
     * 已由实体边界验证归属后的可信目标。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param formReleaseId 表单发布版本ID，后续用于处理已验证目标时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     */
    record VerifiedTarget(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion) {
    }
}
