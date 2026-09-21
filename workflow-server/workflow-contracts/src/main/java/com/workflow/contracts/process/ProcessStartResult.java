package com.workflow.contracts.process;

/** 流程发起后的运行信息；entityStatus 为 null 表示不得覆盖连线已维护的业务状态。 */
public record ProcessStartResult(
        String processInstanceId,
        String entityStatus,
        String currentTaskId,
        String currentTaskName,
        String currentTaskAssignee,
        String processStatus) {
    public ProcessStartResult(String processInstanceId, String entityStatus, String currentTaskId,
            String currentTaskName, String currentTaskAssignee) {
        this(processInstanceId, entityStatus, currentTaskId, currentTaskName, currentTaskAssignee, "RUNNING");
    }
}
