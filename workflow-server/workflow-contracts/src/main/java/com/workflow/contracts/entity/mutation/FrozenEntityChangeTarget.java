package com.workflow.contracts.entity.mutation;

/**
 * 已冻结的变更目标摘要。
 */
public record FrozenEntityChangeTarget(
        String instanceId,
        String bindingCode,
        String bindingName,
        String targetEntityCode,
        String targetRecordId,
        Integer baselineVersionNo,
        String status) {
}
