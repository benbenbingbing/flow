package com.workflow.contracts.embed.runtime.context;

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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param formReleaseId 表单发布版本ID，后续用于处理已授权目标时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     * @param mode 模式标识，决定后续已授权目标采用的处理分支
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    public record AuthorizedTarget(
            String entityCode,
            String formId,
            String formReleaseId,
            Integer formReleaseVersion,
            String mode,
            String recordId) {
    }

    /**
     * 已验证 opaque Session 的不可变运行坐标。
     *
     * @param sessionId 会话ID，后续用于处理会话{@code coordinates}时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理会话{@code coordinates}时定位或关联目标
     */
    public record SessionCoordinates(
            String sessionId,
            String viewReleaseId) {
    }

    /**
     * 在当前请求线程内建立 Embed Session 坐标上下文。
     *
     * <p>返回的 Scope 必须关闭；关闭时恢复嵌套前的值，避免容器线程
     * 复用造成跨请求泄露。</p>
     *
     * @param sessionId 会话ID，后续用于处理打开会话时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理打开会话时定位或关联目标
     * @return 处理后的打开会话结果，供调用方继续处理
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

    /**
     * 返回当前已认证 Embed Session 坐标。
     *
     * @return 匹配的当前会话；未找到时为空
     */
    public static Optional<SessionCoordinates> currentSession() {
        return Optional.ofNullable(CURRENT_SESSION.get());
    }

    /** 请求级 Session 坐标作用域。 */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        /**
         * 处理关闭，并将结果传给后续步骤。
         */
        @Override
        void close();
    }

    /**
     * 初始化嵌入式委托请求上下文，保存构造参数供后续方法使用。
     */
    private EmbedDelegatedRequestContext() {
    }
}
