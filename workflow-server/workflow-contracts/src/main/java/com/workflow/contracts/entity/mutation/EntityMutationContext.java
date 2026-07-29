package com.workflow.contracts.entity.mutation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 一次实体变更的业务上下文。
 *
 * <p>上下文只描述变更来源和业务意图，不允许调用方直接控制是否生成版本。</p>
 */
public record EntityMutationContext(
        EntityMutationSourceType sourceType,
        String sourceId,
        String businessIntentCode,
        String businessIntentName,
        String sourceEntityCode,
        String sourceRecordId,
        String processDefinitionId,
        String processInstanceId,
        String taskId,
        String operatorId,
        String operatorName,
        String businessTraceKey,
        String idempotencyKey,
        Map<String, Object> extraParams) {

    public EntityMutationContext {
        sourceType = sourceType == null
                ? EntityMutationSourceType.SYSTEM_TASK
                : sourceType;
        businessIntentCode = text(
                businessIntentCode,
                "UNSPECIFIED");
        businessIntentName = text(
                businessIntentName,
                businessIntentCode);
        businessTraceKey = text(
                businessTraceKey,
                "mutation_" + UUID.randomUUID());
        idempotencyKey = text(
                idempotencyKey,
                businessTraceKey);
        extraParams = extraParams == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(extraParams));
    }

    public static Builder builder(
            EntityMutationSourceType sourceType,
            String businessIntentCode,
            String businessIntentName) {
        return new Builder(
                sourceType,
                businessIntentCode,
                businessIntentName);
    }

    private static String text(
            String value,
            String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim();
    }

    /**
     * 便于跨模块组装上下文的无框架 Builder。
     */
    public static final class Builder {

        private final EntityMutationSourceType sourceType;
        private final String businessIntentCode;
        private final String businessIntentName;
        private String sourceId;
        private String sourceEntityCode;
        private String sourceRecordId;
        private String processDefinitionId;
        private String processInstanceId;
        private String taskId;
        private String operatorId;
        private String operatorName;
        private String businessTraceKey;
        private String idempotencyKey;
        private Map<String, Object> extraParams = Map.of();

        private Builder(
                EntityMutationSourceType sourceType,
                String businessIntentCode,
                String businessIntentName) {
            this.sourceType = sourceType;
            this.businessIntentCode = businessIntentCode;
            this.businessIntentName = businessIntentName;
        }

        public Builder sourceId(String value) {
            sourceId = value;
            return this;
        }

        public Builder sourceRecord(
                String entityCode,
                String recordId) {
            sourceEntityCode = entityCode;
            sourceRecordId = recordId;
            return this;
        }

        public Builder process(
                String definitionId,
                String instanceId,
                String currentTaskId) {
            processDefinitionId = definitionId;
            processInstanceId = instanceId;
            taskId = currentTaskId;
            return this;
        }

        public Builder operator(
                String id,
                String name) {
            operatorId = id;
            operatorName = name;
            return this;
        }

        public Builder trace(
                String traceKey,
                String mutationIdempotencyKey) {
            businessTraceKey = traceKey;
            idempotencyKey = mutationIdempotencyKey;
            return this;
        }

        public Builder extraParams(
                Map<String, Object> value) {
            extraParams = value;
            return this;
        }

        public EntityMutationContext build() {
            return new EntityMutationContext(
                    sourceType,
                    sourceId,
                    businessIntentCode,
                    businessIntentName,
                    sourceEntityCode,
                    sourceRecordId,
                    processDefinitionId,
                    processInstanceId,
                    taskId,
                    operatorId,
                    operatorName,
                    businessTraceKey,
                    idempotencyKey,
                    extraParams);
        }
    }
}
