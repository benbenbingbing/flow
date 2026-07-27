package com.workflow.contracts.action;

/**
 * Published view of an action catalog entry used by process configuration and execution.
 */
public record FlowActionDefinitionDescriptor(
        String id,
        String handlerName,
        String displayName) {

    public String getId() {
        return id;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
