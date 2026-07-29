package com.workflow.contracts.entity.mutation;

import java.util.List;
import java.util.UUID;

/**
 * 一次业务操作中的多条实体变更。
 */
public record EntityMutationBatchCommand(
        String operationId,
        List<EntityMutationCommand> commands,
        boolean atomic) {

    public EntityMutationBatchCommand {
        operationId = operationId == null
                || operationId.isBlank()
                ? UUID.randomUUID().toString()
                : operationId;
        commands = commands == null
                ? List.of() : List.copyOf(commands);
        if (commands.isEmpty()) {
            throw new IllegalArgumentException(
                    "批量实体变更不能为空");
        }
    }
}
