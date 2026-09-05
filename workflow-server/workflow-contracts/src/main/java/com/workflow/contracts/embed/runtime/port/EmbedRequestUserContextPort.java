package com.workflow.contracts.embed.runtime.port;

/**
 * 将已认证 Embed 会话桥接到平台请求用户上下文的端口。
 */
public interface EmbedRequestUserContextPort {

    /**
     * 打开一个与 Embed 请求关联的 Flow 用户上下文。
     *
     * @param flowUserId 精确映射的 Flow 用户 ID
     * @param username 当前 Flow 用户名
     * @param embedSessionId 用作请求会话标识的 Embed Session ID
     * @return 关闭时清理上下文的幂等作用域
     */
    Scope open(String flowUserId, String username, String embedSessionId);

    /** 请求结束时清理用户上下文的幂等句柄。 */
    @FunctionalInterface
    interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
