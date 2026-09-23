package com.workflow.embed.security;

import com.workflow.embed.domain.AuthenticatedEmbedSession;
import java.util.Optional;

/** Request-thread holder for the authenticated Embed target and capability snapshot. */
public final class EmbedContextHolder {

    private static final ThreadLocal<AuthenticatedEmbedSession> CURRENT = new ThreadLocal<>();

    /**
     * 初始化嵌入式上下文持有者，保存构造参数供后续方法使用。
     */
    private EmbedContextHolder() {
    }

    /**
     * 设置嵌入式上下文持有者；后续读取或执行将使用更新后的状态。
     *
     * @param session 会话，供本方法设置嵌入式上下文持有者时使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    public static void set(AuthenticatedEmbedSession session) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Embed context is already established");
        }
        CURRENT.set(session);
    }

    /**
     * 处理当前，并将结果传给后续步骤。
     *
     * @return 匹配的当前；未找到时为空
     */
    public static Optional<AuthenticatedEmbedSession> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * 校验并获取嵌入式上下文持有者；不满足约束时阻止后续处理。
     *
     * @return 校验并获取后的嵌入式上下文持有者结果，供调用方继续处理
     */
    public static AuthenticatedEmbedSession require() {
        return current().orElseThrow(() -> new IllegalStateException(
                "Authenticated Embed context is required"));
    }

    /**
     * 清理嵌入式上下文持有者；后续读取或执行将使用更新后的状态。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
