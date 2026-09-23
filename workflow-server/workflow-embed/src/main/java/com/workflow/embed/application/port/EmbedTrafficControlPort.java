package com.workflow.embed.application.port;

/**
 * Embed Launch/Runtime 的服务端配额端口。
 *
 * <p>实现必须使用持久化的原子操作，保证多 Pod 下的固定窗口限流与
 * Application/Grant 范围并发租约不会超额。配额只能由服务端当前 Grant 读取，
 * 不接受 Launch 或浏览器 Runtime 参数。</p>
 */
public interface EmbedTrafficControlPort {

    /**
     * 扣减一次已通过 Application/Grant/View/Origin 验证的 Launch 配额。
     *
     * @param applicationId 应用ID，后续用于处理消费启动记录时定位或关联目标
     * @param grantId 授权ID，后续用于处理消费启动记录时定位或关联目标
     */
    void consumeLaunch(String applicationId, String grantId);

    /**
     * 在查询 Launch code 摘要前扣减兑换配额。
     *
     * <p>作用域同时包含公开的 launchId 和 Servlet 容器观察到的对端地址，
     * 防止伪造 code 持续触发摘要索引查询。实现不得信任浏览器自带的转发头。</p>
     *
     * @param launchId 启动记录ID，后续用于处理消费交换时定位或关联目标
     * @param peerAddress {@code peer}地址，供本方法处理消费交换时使用
     */
    void consumeExchange(String launchId, String peerAddress);

    /**
     * 扣减 Runtime 配额并占用一个跨 Pod 并发槽位。
     *
     * @param applicationId 应用ID，后续用于处理获取运行时时定位或关联目标
     * @param grantId 授权ID，后续用于处理获取运行时时定位或关联目标
     * @param sessionId 会话ID，后续用于处理获取运行时时定位或关联目标
     * @param requestClass 请求{@code class}，供本方法处理获取运行时时使用
     * @return 仅在服务端流转的租约句柄，调用方必须在响应完成时释放
     */
    RuntimeLease acquireRuntime(
            String applicationId,
            String grantId,
            String sessionId,
            RuntimeRequestClass requestClass);

    /**
     * 幂等释放 Runtime 并发租约。
     *
     * @param lease 租约，供本方法处理发布版本运行时时使用
     */
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

    /**
     * 不对外暴露的持久化租约句柄。
     *
     * @param id 对象标识，供后续引用、更新或关联
     */
    record RuntimeLease(String id) {

        /**
         * 初始化运行时租约，保存构造参数供后续方法使用。
         *
         * @param id 对象标识，供后续引用、更新或关联
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        public RuntimeLease {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("lease id is required");
            }
        }

        /**
         * 生成当前对象的文本表示，供日志和排障使用。
         *
         * @return 转换为后的字符串文本，供调用方比较或展示
         */
        @Override
        public String toString() {
            return "RuntimeLease[id=<redacted>]";
        }
    }
}
