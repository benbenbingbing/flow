package com.workflow.process.action;

/**
 * 流程动作处理器接口。
 *
 * <p>开发者实现该接口并注册为 Spring Bean，即可在流程设计器的顺序流“流程动作”中引用。</p>
 */
public interface FlowActionHandler {

    /**
     * 执行流程动作。
     *
     * @param ctx 流程动作执行上下文
     */
    void execute(FlowActionContext ctx);
}
