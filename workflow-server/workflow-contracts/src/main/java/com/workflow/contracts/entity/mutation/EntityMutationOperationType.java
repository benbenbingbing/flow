package com.workflow.contracts.entity.mutation;

/**
 * 实体变更操作类型。
 */
public enum EntityMutationOperationType {
    CREATE,
    UPDATE,
    DELETE,
    STATUS_CHANGE,
    APPLY_CHANGE,
    UPSERT
}
