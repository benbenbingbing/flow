package com.workflow.admin.auth.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 登录会话签发、刷新和失败结果指标。
 */
@Component
public class AuthSessionMetrics {

    /** 可选的 Micrometer 注册器。 */
    private final MeterRegistry registry;

    public AuthSessionMetrics(
            ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    /**
     * 记录一次认证会话事件。
     *
     * @param action 动作，例如 issue、refresh、revoke、access
     * @param result 结果，例如 success 或稳定错误编码
     */
    public void record(String action, String result) {
        if (registry == null) {
            return;
        }
        Counter.builder("flow.auth.session.events")
                .description("Browser authentication session outcomes")
                .tag("action", action)
                .tag("result", result)
                .register(registry)
                .increment();
    }
}
