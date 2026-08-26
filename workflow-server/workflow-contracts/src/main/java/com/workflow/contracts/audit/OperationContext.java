package com.workflow.contracts.audit;

import java.util.Objects;

/**
 * 跨模块传播的一次业务操作上下文。
 *
 * <p>{@code operationId} 串联同一次业务操作产生的多个审计事件，
 * {@code traceId} 只负责请求/消息链路追踪，两者不得互相冒充。
 * 异步边界必须显式复制本对象，不能依赖线程本地状态自动传播。</p>
 */
public record OperationContext(
        String operationId,
        String traceId,
        String parentOperationId,
        AuditSourcePointer sourcePointer) {

    public OperationContext {
        operationId = required(operationId, "operationId");
        traceId = text(traceId);
        parentOperationId = text(parentOperationId);
    }

    public static OperationContext root(
            String operationId,
            String traceId) {
        return new OperationContext(
                operationId,
                traceId,
                null,
                null);
    }

    /**
     * 为受当前操作触发的子操作建立上下文，同时保留同一链路 Trace。
     */
    public OperationContext child(
            String childOperationId,
            AuditSourcePointer childSource) {
        return new OperationContext(
                childOperationId,
                traceId,
                operationId,
                childSource);
    }

    private static String required(
            String value,
            String name) {
        String normalized = text(value);
        return Objects.requireNonNull(
                normalized,
                name + " 不能为空");
    }

    private static String text(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
