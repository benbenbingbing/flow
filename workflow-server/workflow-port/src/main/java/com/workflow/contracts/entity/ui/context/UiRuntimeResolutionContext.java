package com.workflow.contracts.entity.ui.context;

import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;

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

    /**
     * 兼容无需任务主体绑定的独立、新建和历史解析调用。
     *
     * @param purpose 用途，保存在对象中供后续校验、查询或展示
     * @param processVersionHistoryId 流程版本历史ID，后续用于初始化界面运行时解析上下文时定位或关联目标
     * @param nodeId 节点ID，后续用于初始化界面运行时解析上下文时定位或关联目标
     */
    public UiRuntimeResolutionContext(
            UiRuntimePurpose purpose,
            String processVersionHistoryId,
            String nodeId) {
        this(purpose, processVersionHistoryId, nodeId,
                null, null, null, null);
    }

    /**
     * 构造与确切活动待办和业务记录绑定的解析上下文。
     *
     * @param processVersionHistoryId 流程版本历史ID，后续用于处理活动任务时定位或关联目标
     * @param nodeId 节点ID，后续用于处理活动任务时定位或关联目标
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 处理后的活动任务结果，供调用方继续处理
     */
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

    /**
     * 处理{@code standalone}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code standalone}结果，供调用方继续处理
     */
    public static UiRuntimeResolutionContext standalone() {
        return new UiRuntimeResolutionContext(
                UiRuntimePurpose.STANDALONE,
                null,
                null);
    }

    /**
     * 处理{@code historical}，并将结果传给后续步骤。
     *
     * @param processVersionHistoryId 流程版本历史ID，后续用于处理{@code historical}时定位或关联目标
     * @param nodeId 节点ID，后续用于处理{@code historical}时定位或关联目标
     * @return 处理后的{@code historical}结果，供调用方继续处理
     */
    public static UiRuntimeResolutionContext historical(
            String processVersionHistoryId,
            String nodeId) {
        return new UiRuntimeResolutionContext(
                UiRuntimePurpose.HISTORICAL,
                processVersionHistoryId,
                nodeId);
    }
}
