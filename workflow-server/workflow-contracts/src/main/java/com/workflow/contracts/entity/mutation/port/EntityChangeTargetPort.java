package com.workflow.contracts.entity.mutation.port;

import com.workflow.contracts.entity.mutation.EntityChangeTargetApplyCommand;
import com.workflow.contracts.entity.mutation.EntityChangeTargetFreezeCommand;
import com.workflow.contracts.entity.mutation.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.FrozenEntityChangeTarget;

import java.util.List;

/**
 * 变更申请目标冻结与原子生效的稳定调用端口。
 */
public interface EntityChangeTargetPort {

    List<FrozenEntityChangeTarget> freeze(EntityChangeTargetFreezeCommand command);

    EntityMutationBatchResult apply(EntityChangeTargetApplyCommand command);
}
