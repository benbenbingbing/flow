package com.workflow.process.action;

/**
 * 带类型化业务参数的流程动作处理器接口。
 *
 * <p>适用于希望将 paramsJson 直接映射为 Java 参数类的场景。
 * 平台会自动将 {@link FlowActionContext#getCustomParams()} 转换为参数类型 T 的实例。</p>
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
     * @param ctx    流程动作执行上下文
     * @param params 类型化业务参数
     */
    void execute(FlowActionContext ctx, T params);

    @Override
    default void execute(FlowActionContext ctx) {
        T params = ctx.getHelper().convertParams(ctx.getCustomParams(), getParamType());
        execute(ctx, params);
    }
}
