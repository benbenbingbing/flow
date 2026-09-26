package com.workflow.contracts.process.action.model;

/**
 * Published view of an action catalog entry used by process configuration and execution.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param handlerName 处理器名称，后续用于处理流程动作定义描述时匹配或展示
 * @param displayName 用户可见名称，供界面和日志展示
 */
public record FlowActionDefinitionDescriptor(
        String id,
        String handlerName,
        String displayName) {

    /**
     * 读取ID；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的ID文本，供调用方比较或展示
     */
    public String getId() {
        return id;
    }

    /**
     * 读取处理器名称；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的处理器名称文本，供调用方比较或展示
     */
    public String getHandlerName() {
        return handlerName;
    }

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    public String getDisplayName() {
        return displayName;
    }
}
