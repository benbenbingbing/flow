package com.workflow.contracts.entity;

/**
 * 流程模块更新实体记录运行态字段时使用的跨模块端口。
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
