package com.workflow.embed.application.port;

/**
 * Embed Launch/Runtime 的服务端配额端口。
 *
 * <p>实现必须使用持久化的原子操作，保证多 Pod 下的固定窗口限流与
 * Application/Grant 范围并发租约不会超额。配额只能由服务端当前 Grant 读取，
 * 不接受 Launch 或浏览器 Runtime 参数。</p>
 */
public interface EmbedTrafficControlPort {

    /** 扣减一次已通过 Application/Grant/View/Origin 验证的 Launch 配额。 */
    void consumeLaunch(String applicationId, String grantId);

    /**
     * 在查询 Launch code 摘要前扣减兑换配额。
     *
     * <p>作用域同时包含公开的 launchId 和 Servlet 容器观察到的对端地址，
     * 防止伪造 code 持续触发摘要索引查询。实现不得信任浏览器自带的转发头。</p>
     */
    void consumeExchange(String launchId, String peerAddress);

    /**
     * 扣减 Runtime 配额并占用一个跨 Pod 并发槽位。
     *
     * @return 仅在服务端流转的租约句柄，调用方必须在响应完成时释放
     */
    RuntimeLease acquireRuntime(
            String applicationId,
            String grantId,
            String sessionId,
            RuntimeRequestClass requestClass);

    /** 幂等释放 Runtime 并发租约。 */
    void releaseRuntime(RuntimeLease lease);

    /**
     * 由服务端路由决定的低基数请求类别，用于叠加 Session 级配额。
     * 浏览器不能通过请求体或 Header 选择该值。
     */
    enum RuntimeRequestClass {
        READ,
        WRITE,
        HEARTBEAT
    }

    /** 不对外暴露的持久化租约句柄。 */
    record RuntimeLease(String id) {

        public RuntimeLease {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("lease id is required");
            }
        }

        @Override
        public String toString() {
            return "RuntimeLease[id=<redacted>]";
        }
    }
}
