package com.workflow.contracts.entity.mutation;

import java.util.List;

/**
 * 变更申请目标冻结与原子生效端口。
 */
public interface EntityChangeTargetPort {

    List<FrozenEntityChangeTarget> freeze(
            EntityChangeTargetFreezeCommand command);

    EntityMutationBatchResult apply(
            EntityChangeTargetApplyCommand command);
}
