package com.workflow.contracts.embed;

import java.util.Optional;

/**
 * Embed opaque Session 向普通 Flow MVC 运行时委托时使用的请求属性契约。
 *
 * <p>这些属性只由服务端认证过滤器写入；HTTP header 或参数中的同名值无效。</p>
 */
public final class EmbedDelegatedRequestContext {

    private static final ThreadLocal<SessionCoordinates> CURRENT_SESSION =
            new ThreadLocal<>();

    public static final String VERIFIED_ATTRIBUTE =
            EmbedDelegatedRequestContext.class.getName() + ".verified";
    public static final String TARGET_ATTRIBUTE =
            EmbedDelegatedRequestContext.class.getName() + ".target";
    public static final String AUTHENTICATED_SESSION_ATTRIBUTE =
            EmbedDelegatedRequestContext.class.getName() + ".session";
    public static final String JSON_BODY_ATTRIBUTE =
            EmbedDelegatedRequestContext.class.getName() + ".jsonBody";
    public static final String CAPABILITIES_ATTRIBUTE =
            EmbedDelegatedRequestContext.class.getName() + ".capabilities";

    /**
     * MVC 策略已经授权的原生运行时目标。该值仅能由服务端拦截器写入，
     * 下游可据此固定历史发布执行，不能从请求 JSON 重建。
     */
    public record AuthorizedTarget(
            String entityCode,
            String formId,
            String formReleaseId,
            Integer formReleaseVersion,
            String mode,
            String recordId) {
    }

    /** 已验证 opaque Session 的不可变运行坐标。 */
    public record SessionCoordinates(
            String sessionId,
            String viewReleaseId) {
    }

    /**
     * 在当前请求线程内建立 Embed Session 坐标上下文。
     *
     * <p>返回的 Scope 必须关闭；关闭时恢复嵌套前的值，避免容器线程
     * 复用造成跨请求泄露。</p>
     */
    public static Scope openSession(
            String sessionId,
            String viewReleaseId) {
        SessionCoordinates previous = CURRENT_SESSION.get();
        CURRENT_SESSION.set(new SessionCoordinates(
                sessionId, viewReleaseId));
        return () -> {
            if (previous == null) {
                CURRENT_SESSION.remove();
            } else {
                CURRENT_SESSION.set(previous);
            }
        };
    }

    /** 返回当前已认证 Embed Session 坐标。 */
    public static Optional<SessionCoordinates> currentSession() {
        return Optional.ofNullable(CURRENT_SESSION.get());
    }

    /** 请求级 Session 坐标作用域。 */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private EmbedDelegatedRequestContext() {
    }
}
