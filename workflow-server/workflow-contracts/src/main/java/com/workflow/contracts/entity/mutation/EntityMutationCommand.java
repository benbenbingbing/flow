package com.workflow.contracts.entity.mutation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 单条实体变更命令。
 */
public record EntityMutationCommand(
        String operationId,
        String entityCode,
        String recordId,
        EntityMutationOperationType operationType,
        Map<String, Object> payload,
        EntityMutationContext context) {

    public EntityMutationCommand {
        if (entityCode == null || entityCode.isBlank()) {
            throw new IllegalArgumentException(
                    "实体编码不能为空");
        }
        if (operationType == null) {
            throw new IllegalArgumentException(
                    "实体变更操作类型不能为空");
        }
        if (operationType != EntityMutationOperationType.CREATE
                && (recordId == null || recordId.isBlank())) {
            throw new IllegalArgumentException(
                    "非新增操作的实体记录ID不能为空");
        }
        operationId = operationId == null
                || operationId.isBlank()
                ? UUID.randomUUID().toString()
                : operationId;
        entityCode = entityCode.trim();
        recordId = recordId == null
                ? null : recordId.trim();
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(payload));
        context = context == null
                ? EntityMutationContext.builder(
                        EntityMutationSourceType.SYSTEM_TASK,
                        "UNSPECIFIED",
                        "未指定业务变更")
                .build()
                : context;
    }

    public static EntityMutationCommand create(
            String entityCode,
            Map<String, Object> payload,
            EntityMutationContext context) {
        return new EntityMutationCommand(
                null,
                entityCode,
                null,
                EntityMutationOperationType.CREATE,
                payload,
                context);
    }

    public static EntityMutationCommand update(
            String entityCode,
            String recordId,
            Map<String, Object> payload,
            EntityMutationContext context) {
        return new EntityMutationCommand(
                null,
                entityCode,
                recordId,
                EntityMutationOperationType.UPDATE,
                payload,
                context);
    }

    public static EntityMutationCommand delete(
            String entityCode,
            String recordId,
            EntityMutationContext context) {
        return new EntityMutationCommand(
                null,
                entityCode,
                recordId,
                EntityMutationOperationType.DELETE,
                Map.of(),
                context);
    }
}
