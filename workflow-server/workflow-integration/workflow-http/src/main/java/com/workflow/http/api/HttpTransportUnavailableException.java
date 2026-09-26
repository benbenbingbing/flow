package com.workflow.http.api;

import java.io.IOException;

/**
 * 本地容量或熔断策略拒绝请求，尚未构成一次远端调用失败。
 * 调用方应停止本轮立即重试，避免拥塞时继续放大流量；具体连接池实现保持封装。
 */
public final class HttpTransportUnavailableException extends IOException {
    /**
     * 携带本地拒绝原因，供调用方区分远端网络异常并决定后续调度策略。
     *
     * @param message 不包含业务载荷的拒绝原因
     */
    public HttpTransportUnavailableException(String message) {
        super(message);
    }
}
