package com.workflow.contracts.embed;

import java.time.Instant;

/**
 * 为 Embed iframe 复用 Flow 原生 Published Form 运行时签发短期解析上下文。
 *
 * <p>调用方只能传入已经从已认证 Embed Session 和不可变 View Release 恢复出的
 * 坐标；实现必须再次校验表单、实体和发布版本归属。返回令牌只授权读取该固定发布
 * 快照及其声明的嵌套表单，有效期不得超过服务端认证 Session 的绝对到期时间，
 * 也不会创建普通 Flow 登录态。</p>
 */
public interface EmbedNativeFormRuntimePort {

    String issueReleaseResolutionToken(Target target);

    /**
     * 验证浏览器回传的服务端签名令牌并恢复精确 Published Form 目标。
     *
     * <p>实现必须校验签名、mapped user、Embed Session/View Release、绝对
     * 到期时间以及表单对实体的归属；不能只相信请求中的 form/entity 坐标。</p>
     */
    VerifiedTarget verifyReleaseResolutionToken(
            String token,
            VerificationTarget target);

    /**
     * 验证表单快照首次解析请求。
     *
     * <p>token 可直接绑定目标，也可绑定已发布父表单；后者必须
     * 复用 Flow 平台的父快照子表单引用校验。这个放宽只用于
     * {@code /runtime-release}，按钮、事件和详情仍必须携带目标表单 token。</p>
     */
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

    /** 已由 Entity 边界验证归属后的可信目标。 */
    record VerifiedTarget(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion) {
    }
}
