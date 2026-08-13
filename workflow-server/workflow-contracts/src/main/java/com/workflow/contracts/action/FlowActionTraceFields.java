package com.workflow.contracts.action;

/**
 * 流程动作执行轨迹的固定字段名。
 *
 * <p>字段值仍由动作处理器自由定义，轨迹对外继续保持 Map 结构。</p>
 */
public final class FlowActionTraceFields {

    /**
     * 处理器定义的阶段标识，用于区分一条轨迹发生在动作执行的哪个业务步骤。
     *
     * <p>该字段值是开放字符串，例如 {@code HANDLER_STARTED}；平台只负责透传
     * 和持久化，不限制处理器自定义阶段。</p>
     */
    public static final String STAGE = "stage";

    /**
     * 面向执行记录查看者的阶段说明，用于展示该步骤完成了什么或为何失败。
     */
    public static final String MESSAGE = "message";

    /**
     * 可选的结构化步骤详情，保存处理结果摘要、诊断上下文等扩展数据。
     *
     * <p>详情类型由处理器决定；运行时在写入执行记录前会对其做序列化清洗。</p>
     */
    public static final String DETAILS = "details";

    private FlowActionTraceFields() {
    }
}
