package com.workflow.contracts.process.action.port;

import com.workflow.contracts.action.FlowActionDefinitionDescriptor;

/**
 * 读取管理员维护的流程动作目录。
 *
 * <p>这是由流程动作目录提供的稳定能力端口，而非可被业务模块多实现的扩展点。</p>
 */
public interface FlowActionCatalogPort {

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

    String displayName(String handlerName);
}
