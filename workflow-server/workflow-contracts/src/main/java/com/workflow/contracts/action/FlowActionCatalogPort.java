package com.workflow.contracts.action;

/**
 * Read contract from process action runtime to the administrator-managed action catalog.
 */
public interface FlowActionCatalogPort {

    FlowActionDefinitionDescriptor requireSelectable(
            String processConfigId,
            String definitionId,
            String handlerName);

    String displayName(String handlerName);
}
