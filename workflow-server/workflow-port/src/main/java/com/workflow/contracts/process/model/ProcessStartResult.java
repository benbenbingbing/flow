package com.workflow.contracts.process.model;

/**
 * 流程发起后的运行信息；entityStatus 为 null 表示不得覆盖连线已维护的业务状态。
 *
 * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
 * @param entityStatus 实体状态标识，决定后续流程启动结果采用的处理分支
 * @param currentTaskId 当前任务ID，写入当前任务信息供后续待办展示和状态同步
 * @param currentTaskName 当前任务名称，写入当前任务信息供后续待办展示和状态同步
 * @param currentTaskAssignee 当前任务办理人，写入当前任务信息供后续待办展示和状态同步
 * @param processStatus 流程状态标识，决定后续流程启动结果采用的处理分支
 */
public record ProcessStartResult(
        String processInstanceId,
        String entityStatus,
        String currentTaskId,
        String currentTaskName,
        String currentTaskAssignee,
        String processStatus) {
    /**
     * 初始化流程启动结果，保存构造参数供后续方法使用。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityStatus 实体状态标识，决定后续流程启动采用的处理分支
     * @param currentTaskId 当前任务ID，写入当前任务信息供后续待办展示和状态同步
     * @param currentTaskName 当前任务名称，写入当前任务信息供后续待办展示和状态同步
     * @param currentTaskAssignee 当前任务办理人，写入当前任务信息供后续待办展示和状态同步
     */
    public ProcessStartResult(String processInstanceId, String entityStatus, String currentTaskId,
            String currentTaskName, String currentTaskAssignee) {
        this(processInstanceId, entityStatus, currentTaskId, currentTaskName, currentTaskAssignee, "RUNNING");
    }
}
