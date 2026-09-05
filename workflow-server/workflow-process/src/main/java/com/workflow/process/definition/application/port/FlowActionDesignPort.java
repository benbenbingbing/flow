package com.workflow.process.definition.application.port;

/**
 * 流程动作设计期端口。
 *
 * <p>该端口仅用于流程定义发布编排与流程动作子域之间的解耦，不是跨模块共享契约。
 * 定义流程配置在设计/发布阶段对流程动作的校验、BPMN 改写、发布与清理能力。</p>
 */
public interface FlowActionDesignPort {

    /**
     * 发布前校验流程配置对应的动作定义。
     *
     * @param processConfigId 流程配置 ID
     */
    void validateForPublish(String processConfigId);

    /**
     * 在发布前对 BPMN XML 进行改写（如注入动作节点引用）。
     *
     * @param processConfigId 流程配置 ID
     * @param bpmnXml         原始 BPMN XML
     * @return 改写后的 BPMN XML
     */
    String prepareBpmnForPublish(String processConfigId, String bpmnXml);

    /**
     * 发布流程配置对应版本的动作定义。
     *
     * @param processConfigId 流程配置 ID
     * @param versionId       版本 ID
     */
    void publishActions(String processConfigId, String versionId);

    /**
     * 按版本 ID 删除已发布的动作定义。
     *
     * @param versionId 流程发布版本 ID
     */
    void deleteActionsByVersionId(String versionId);
}
