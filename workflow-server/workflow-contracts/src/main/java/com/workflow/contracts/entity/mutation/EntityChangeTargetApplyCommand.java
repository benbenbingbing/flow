package com.workflow.contracts.entity.mutation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批通过后原子应用已冻结变更目标的请求。
 */
public record EntityChangeTargetApplyCommand(
        String sourceEntityCode,
        String sourceRecordId,
        String processDefinitionId,
        String processInstanceId,
        String taskId,
        String operatorId,
        String operatorName,
        EntityMutationSourceType sourceType,
        String businessIntentCode,
        String businessIntentName,
        String idempotencyKey,
        Map<String, Object> extraParams) {

    public EntityChangeTargetApplyCommand {
        if (sourceEntityCode == null
                || sourceEntityCode.isBlank()) {
            throw new IllegalArgumentException(
                    "来源实体编码不能为空");
        }
        if (sourceRecordId == null
                || sourceRecordId.isBlank()) {
            throw new IllegalArgumentException(
                    "来源记录ID不能为空");
        }
        sourceType = sourceType == null
                ? EntityMutationSourceType.PROCESS_RUNTIME
                : sourceType;
        businessIntentCode =
                businessIntentCode == null
                        || businessIntentCode.isBlank()
                        ? "CHANGE_EFFECTIVE"
                        : businessIntentCode;
        businessIntentName =
                businessIntentName == null
                        || businessIntentName.isBlank()
                        ? "变更审批生效"
                        : businessIntentName;
        idempotencyKey =
                idempotencyKey == null
                        || idempotencyKey.isBlank()
                        ? sourceEntityCode
                                + ":"
                                + sourceRecordId
                                + ":"
                                + processInstanceId
                                + ":apply"
                        : idempotencyKey;
        extraParams = extraParams == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(extraParams));
    }
}
