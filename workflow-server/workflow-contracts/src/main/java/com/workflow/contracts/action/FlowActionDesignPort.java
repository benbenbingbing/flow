package com.workflow.contracts.action;

/**
 * 流程动作设计期端口。
 * 定义流程配置在设计/发布阶段对流程动作的校验、BPMN 改写、发布与清理能力。
 */
public interface FlowActionDesignPort {

    /**
     * 发布前校验流程配置对应的动作定义。
     *
     * @param processConfigId 流程配置ID
     */
    void validateForPublish(String processConfigId);

    /**
     * 在发布前对 BPMN XML 进行改写（如注入动作节点引用）。
     *
     * @param processConfigId 流程配置ID
     * @param bpmnXml         原始 BPMN XML
     * @return 改写后的 BPMN XML
     */
    String prepareBpmnForPublish(String processConfigId, String bpmnXml);

    /**
     * 发布流程配置对应版本的动作定义。
     *
     * @param processConfigId 流程配置ID
     * @param versionId       版本ID
     */
    void publishActions(String processConfigId, String versionId);

    /**
     * 按版本ID删除已发布的动作定义。
     *
     * @param versionId 版本ID
     */
    void deleteActionsByVersionId(String versionId);
}
