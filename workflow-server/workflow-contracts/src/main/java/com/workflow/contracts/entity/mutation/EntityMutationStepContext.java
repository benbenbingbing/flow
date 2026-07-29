package com.workflow.contracts.entity.mutation;

import java.util.Map;

/**
 * 前置操作执行上下文。
 */
public record EntityMutationStepContext(
        EntityMutationPhase phase,
        EntityMutationCommand command,
        Map<String, Object> beforeRecord,
        Map<String, Object> workingPayload,
        Map<String, Object> configuration) {
}
