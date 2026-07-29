package com.workflow.contracts.entity.mutation;

/**
 * 实体变更管道阶段。
 */
public enum EntityMutationPhase {
    PREPARE,
    BEFORE_WRITE,
    AFTER_WRITE,
    AFTER_COMMIT
}
