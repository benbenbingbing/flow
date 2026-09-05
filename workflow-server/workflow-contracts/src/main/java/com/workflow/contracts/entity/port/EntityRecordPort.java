package com.workflow.contracts.entity.port;

/**
 * 流程更新实体记录运行态字段时使用的稳定跨模块写入能力。
 */
public interface EntityRecordPort {

    void updateCurrentTask(
            String entityCode,
            String entityRecordId,
            String currentTaskId,
            String currentTaskName,
            String currentTaskAssignee);

    void updateStatus(
            String entityCode,
            String entityRecordId,
            String status);

    void markProcessEnded(
            String processInstanceId,
            String entityCode,
            String entityRecordId,
            String statusCategory,
            String fallbackStatus);

    void recordActivity(
            String entityCode,
            String entityRecordId,
            String action,
            String actionName,
            String processInstanceId,
            String taskId);
}
