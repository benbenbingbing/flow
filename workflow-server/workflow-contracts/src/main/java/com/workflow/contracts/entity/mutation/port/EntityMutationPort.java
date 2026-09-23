package com.workflow.contracts.entity.mutation.port;

import com.workflow.contracts.entity.mutation.model.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;

/**
 * 跨模块实体写入的稳定调用端口。
 *
 * <p>由实体模块实现；命令、结果和写入上下文统一放在 {@code entity.mutation.model}，
 * 调用方通过本端口进入受控的实体写入流程。</p>
 */
public interface EntityMutationPort {

    /**
     * 执行实体变更，并将结果传给后续步骤。
     *
     * @param command 本次命令，后续经校验后用于执行实体变更
     * @return 执行后的实体变更结果，供调用方继续处理
     */
    EntityMutationResult execute(EntityMutationCommand command);

    /**
     * 执行实体变更批次，并将结果传给后续步骤。
     *
     * @param command 本次命令，后续经校验后用于执行实体变更批次
     * @return 执行后的实体变更批次结果，供调用方继续处理
     */
    EntityMutationBatchResult executeBatch(EntityMutationBatchCommand command);

    /**
     * 在不修改业务记录、不生成业务版本的前提下，对当前最终记录执行
     * 表单作用域唯一终检，并协调占位。
     *
     * <p>用于审批表单实际已提交、但本次没有可编辑字段变化的场景。调用方必须传入
     * 服务端实际应用的发布表单引用；无表单引用时应直接跳过。</p>
     *
     * @param entityCode 实体编码
     * @param recordId   实体记录 ID
     * @param context    本次变更运行时上下文
     */
    void reconcileFormUniqueness(
            String entityCode,
            String recordId,
            EntityMutationContext context);
}
