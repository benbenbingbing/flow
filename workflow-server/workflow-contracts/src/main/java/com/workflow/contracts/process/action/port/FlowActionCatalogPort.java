package com.workflow.contracts.process.action.port;

import com.workflow.contracts.process.action.model.FlowActionDefinitionDescriptor;

/**
 * 读取管理员维护的流程动作目录。
 *
 * <p>这是由流程动作目录提供的稳定能力端口，而非可被业务模块多实现的扩展点。</p>
 */
public interface FlowActionCatalogPort {

    /**
     * 校验并获取{@code selectable}；不满足约束时阻止后续处理。
     *
     * @param processConfigId 流程配置ID，后续用于校验并获取{@code selectable}时定位或关联目标
     * @param definitionId 定义ID，后续用于校验并获取{@code selectable}时定位或关联目标
     * @param handlerName 处理器名称，后续用于校验并获取{@code selectable}时匹配或展示
     * @return 校验并获取后的{@code selectable}结果，供调用方继续处理
     */
    FlowActionDefinitionDescriptor requireSelectable(
            String processConfigId,
            String definitionId,
            String handlerName);

    /**
     * 判断处理器是否已纳入动作目录、已启用且 Bean 当前可用。
     *
     * @param handlerName 处理器 Bean 名称
     * @return 满足发布前置条件时返回 true
     */
    boolean isConfiguredAndAvailable(String handlerName);

    /**
     * 生成展示名称文本，供后续匹配或展示。
     *
     * @param handlerName 处理器名称，后续用于处理展示名称时匹配或展示
     * @return 处理后的展示名称文本，供调用方比较或展示
     */
    String displayName(String handlerName);
}
