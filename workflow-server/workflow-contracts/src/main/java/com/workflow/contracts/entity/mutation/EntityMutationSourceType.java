package com.workflow.contracts.entity.mutation;

/**
 * 发起实体变更的平台入口。
 */
public enum EntityMutationSourceType {
    FORM,
    LIST,
    APPROVAL_TASK,
    PROCESS_RUNTIME,
    FLOW_ACTION,
    CUSTOM_INTERFACE,
    BATCH,
    IMPORT,
    SCHEDULED_JOB,
    MESSAGE_CONSUMER,
    SYSTEM_TASK
}
