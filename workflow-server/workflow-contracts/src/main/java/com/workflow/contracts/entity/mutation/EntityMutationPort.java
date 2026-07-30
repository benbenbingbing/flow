package com.workflow.contracts.entity.mutation;

/**
 * 跨模块实体写入端口。
 */
public interface EntityMutationPort {

    EntityMutationResult execute(
            EntityMutationCommand command);

    EntityMutationBatchResult executeBatch(
            EntityMutationBatchCommand command);
}
