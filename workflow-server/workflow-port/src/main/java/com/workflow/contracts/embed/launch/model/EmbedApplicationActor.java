package com.workflow.contracts.embed.launch.model;

import java.util.Objects;

/**
 * Machine application identity that requests an Embed launch.
 *
 * @param applicationId 应用ID，后续用于处理嵌入式应用操作人时定位或关联目标
 * @param clientId 客户端ID，后续用于处理嵌入式应用操作人时定位或关联目标
 * @param traceId 追踪ID，后续用于处理嵌入式应用操作人时定位或关联目标
 * @param requestId 请求ID，后续用于处理嵌入式应用操作人时定位或关联目标
 */
public record EmbedApplicationActor(
        String applicationId,
        String clientId,
        String traceId,
        String requestId) {

    /**
     * 初始化嵌入式应用操作人，保存构造参数供后续方法使用。
     *
     * @param applicationId 应用ID，后续用于初始化嵌入式应用操作人时定位或关联目标
     * @param clientId 客户端ID，后续用于初始化嵌入式应用操作人时定位或关联目标
     * @param traceId 追踪ID，后续用于初始化嵌入式应用操作人时定位或关联目标
     * @param requestId 请求ID，后续用于初始化嵌入式应用操作人时定位或关联目标
     */
    public EmbedApplicationActor {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(traceId, "traceId");
    }

    /**
     * 兼容没有独立 requestId 的非 HTTP 调用；不得以 applicationId、launchId 等业务 ID 代替。
     *
     * @param applicationId 应用ID，后续用于初始化嵌入式应用操作人时定位或关联目标
     * @param clientId 客户端ID，后续用于初始化嵌入式应用操作人时定位或关联目标
     * @param traceId 追踪ID，后续用于初始化嵌入式应用操作人时定位或关联目标
     */
    public EmbedApplicationActor(
            String applicationId,
            String clientId,
            String traceId) {
        this(applicationId, clientId, traceId, null);
    }
}
