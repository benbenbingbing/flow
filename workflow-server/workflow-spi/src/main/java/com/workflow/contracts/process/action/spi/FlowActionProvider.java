package com.workflow.contracts.process.action.spi;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.FlowActionExecutionMode;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import java.util.Map;
import java.util.Set;

/**
 * 流程动作处理器扩展点。
 *
 * <p>开发者实现该接口并注册为 Spring Bean，即可在流程设计器的全局流程、节点或顺序流
 * “流程动作”中引用，并通过能力声明限制支持的触发时机和执行方式。</p>
 */
public interface FlowActionProvider {

    /**
     * 返回扩展实现归属。
     *
     * <p>SPI 默认视为项目自定义；平台内置处理器必须显式覆盖，避免按包名猜测。</p>
     *
     * @return 处理后的实现来源结果，供调用方继续处理
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

    /**
     * 空集合表示支持全部标准与自定义时机。
     *
     * @return 流程动作集合，供调用方遍历或展示
     */
    default Set<String> supportedTriggerTimings() {
        return Set.of();
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 流程动作集合，供调用方遍历或展示
     */
    default Set<String> supportedExecutionModes() {
        return Set.of(
                FlowActionExecutionMode.IN_TRANSACTION.name(),
                FlowActionExecutionMode.AFTER_COMMIT.name());
    }

    /**
     * 生成推荐执行模式文本，供后续匹配或展示。
     *
     * @return 处理后的推荐执行模式文本，供调用方比较或展示
     */
    default String recommendedExecutionMode() {
        return null;
    }

    /**
     * 是否允许使用同一幂等键重试 AFTER_COMMIT 执行。
     *
     * <p>具有外部副作用的处理器，只有下游系统强制该幂等键时才可返回 true。</p>
     *
     * @return 可重试条件成立时为 true，否则为 false
     */
    default boolean retryable() {
        return false;
    }

    /**
     * 当前动作允许配置的 extraParams Schema。
     *
     * @return 附加参数结构键值结果，供调用方继续处理
     */
    default Map<String, Object> extraParamSchema() {
        return Map.of();
    }

    /**
     * 是否允许传入 Schema 未声明的动态 extraParams。
     *
     * @return 动态附加参数条件成立时为 true，否则为 false
     */
    default boolean dynamicExtraParams() {
        return false;
    }
}
