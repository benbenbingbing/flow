package com.workflow.process.status.application;

public record ProcessStatusSyncPayload(
        String processInstanceId,
        String eventType,
        String eventSequence,
        String entityCode,
        String entityRecordId,
        String targetStatus,
        String statusCategory,
        String fallbackStatus) {

    public ProcessStatusSyncPayload {
        require(processInstanceId, "processInstanceId");
        require(eventType, "eventType");
        require(eventSequence, "eventSequence");
        require(entityCode, "entityCode");
        require(entityRecordId, "entityRecordId");
        if ("TASK_COMPLETED".equals(eventType)) {
            require(targetStatus, "targetStatus");
        } else if ("PROCESS_END".equals(eventType)) {
            require(statusCategory, "statusCategory");
            // 新发布版本不携带业务状态兜底，结束仅更新生命周期。
        } else {
            throw new IllegalArgumentException(
                    "未知状态同步事件: " + eventType);
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "状态同步事件缺少 " + field);
        }
    }
}
