package com.workflow.contracts.action;

public interface FlowActionDesignPort {

    void validateForPublish(String processConfigId);

    String prepareBpmnForPublish(String processConfigId, String bpmnXml);

    void publishActions(String processConfigId, String versionId);

    void deleteActionsByVersionId(String versionId);
}
