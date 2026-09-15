package com.workflow.contracts.process.action.spi;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionExecutionMode;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import java.util.Map;
import java.util.Set;

/**
 * 流程动作处理器扩展点。
 *
 * <p>开发者实现该接口并注册为 Spring Bean，即可在流程设计器的全局流程、节点或顺序流
 * “流程动作”中引用，并通过能力声明限制支持的触发时机和执行方式。</p>
 */
public interface FlowActionHandler {

    /**
     * 返回扩展实现归属。
     *
     * <p>SPI 默认视为项目自定义；平台内置处理器必须显式覆盖，避免按包名猜测。</p>
     */
    default ExtensionImplementationOrigin implementationOrigin() {
        return ExtensionImplementationOrigin.CUSTOM;
    }

    /**
     * 执行流程动作。
     *
     * @param ctx 流程动作执行上下文
     */
    void execute(FlowActionContext ctx);

    /** 空集合表示支持全部标准与自定义时机。 */
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
     * 是否允许使用同一幂等键重试 AFTER_COMMIT 执行。
     *
     * <p>具有外部副作用的处理器，只有下游系统强制该幂等键时才可返回 true。</p>
     */
    default boolean retryable() {
        return false;
    }

    /** 当前动作允许配置的 extraParams Schema。 */
    default Map<String, Object> extraParamSchema() {
        return Map.of();
    }

    /** 是否允许传入 Schema 未声明的动态 extraParams。 */
    default boolean dynamicExtraParams() {
        return false;
    }
}
