package com.workflow.contracts.action;

import java.util.Set;
import java.util.Map;

/**
 * 流程动作处理器接口。
 *
 * <p>开发者实现该接口并注册为 Spring Bean，即可在流程设计器的全局流程、节点或顺序流
 * “流程动作”中引用，并通过能力声明限制支持的触发时机和执行方式。</p>
 */
public interface FlowActionHandler {

    /**
     * 执行流程动作。
     *
     * @param ctx 流程动作执行上下文
     */
    void execute(FlowActionContext ctx);

    /**
     * 空集合表示支持全部标准与自定义时机。
     */
    default Set<String> supportedTriggerTimings() {
        return Set.of();
    }

    default Set<String> supportedExecutionModes() {
        return Set.of(
                FlowActionExecutionMode.IN_TRANSACTION.name(),
                FlowActionExecutionMode.AFTER_COMMIT.name());
    }

    default String recommendedExecutionMode() {
        return null;
    }

    /**
     * Whether an AFTER_COMMIT execution can be retried with the same idempotency key.
     *
     * <p>Handlers with external side effects must remain non-retryable unless the
     * downstream system enforces that key.</p>
     */
    default boolean retryable() {
        return false;
    }

    /**
     * 当前动作允许配置的 extraParams Schema。
     */
    default Map<String, Object> extraParamSchema() {
        return Map.of();
    }

    /**
     * 是否允许传入 Schema 未声明的动态 extraParams。
     */
    default boolean dynamicExtraParams() {
        return false;
    }
}
