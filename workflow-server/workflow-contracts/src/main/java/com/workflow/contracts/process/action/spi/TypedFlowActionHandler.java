package com.workflow.contracts.process.action.spi;

import com.workflow.contracts.action.FlowActionContext;

/**
 * 带类型化业务参数的流程动作处理器扩展点。
 *
 * <p>平台会将 {@link FlowActionContext#getCustomParams()} 转换为参数类型 T 的实例。</p>
 *
 * @param <T> 业务参数类型
 */
public interface TypedFlowActionHandler<T> extends FlowActionHandler {

    /**
     * 返回业务参数类型。
     *
     * @return 参数类型 Class
     */
    Class<T> getParamType();

    /**
     * 执行流程动作。
     *
     * @param ctx 流程动作执行上下文
     * @param params 类型化业务参数
     */
    void execute(FlowActionContext ctx, T params);

    @Override
    default void execute(FlowActionContext ctx) {
        T params = ctx.convertExtraParams(getParamType());
        execute(ctx, params);
    }
}
