package com.workflow.contracts.action;

/**
 * Read contract from process action runtime to the administrator-managed action catalog.
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
