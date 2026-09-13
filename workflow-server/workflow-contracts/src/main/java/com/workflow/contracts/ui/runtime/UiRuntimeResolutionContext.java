package com.workflow.contracts.ui.runtime;

/**
 * 服务端可信的 UI 发布快照解析上下文。
 *
 * @param purpose                 解析目的
 * @param processVersionHistoryId 流程发布历史ID
 * @param nodeId                  流程节点ID
 * @param taskId                  ACTIVE_TASK 的确切任务 ID
 * @param processInstanceId       ACTIVE_TASK 的流程实例 ID
 * @param entityCode              ACTIVE_TASK 的业务实体编码
 * @param recordId                ACTIVE_TASK 的业务记录 ID
 */
public record UiRuntimeResolutionContext(
        UiRuntimePurpose purpose,
        String processVersionHistoryId,
        String nodeId,
        String taskId,
        String processInstanceId,
        String entityCode,
        String recordId) {

    /** 兼容无需任务主体绑定的独立、新建和历史解析调用。 */
    public UiRuntimeResolutionContext(
            UiRuntimePurpose purpose,
            String processVersionHistoryId,
            String nodeId) {
        this(purpose, processVersionHistoryId, nodeId,
                null, null, null, null);
    }

    /** 构造与确切活动待办和业务记录绑定的解析上下文。 */
    public static UiRuntimeResolutionContext activeTask(
            String processVersionHistoryId,
            String nodeId,
            String taskId,
            String processInstanceId,
            String entityCode,
            String recordId) {
        return new UiRuntimeResolutionContext(
                UiRuntimePurpose.ACTIVE_TASK,
                processVersionHistoryId,
                nodeId,
                taskId,
                processInstanceId,
                entityCode,
                recordId);
    }

    public static UiRuntimeResolutionContext standalone() {
        return new UiRuntimeResolutionContext(
                UiRuntimePurpose.STANDALONE,
                null,
                null);
    }

    public static UiRuntimeResolutionContext historical(
            String processVersionHistoryId,
            String nodeId) {
        return new UiRuntimeResolutionContext(
                UiRuntimePurpose.HISTORICAL,
                processVersionHistoryId,
                nodeId);
    }
}
