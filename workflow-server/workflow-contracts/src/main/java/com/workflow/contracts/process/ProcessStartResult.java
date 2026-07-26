package com.workflow.contracts.process;

/**
 * 流程发起后返回给实体模块的最小运行态信息。
 */
public record ProcessStartResult(
        String processInstanceId,
        String entityStatus,
        String currentTaskId,
        String currentTaskName,
        String currentTaskAssignee) {
}
