package com.workflow.contracts.entity.mutation;

/**
 * 跨模块实体写入端口。
 */
public interface EntityMutationPort {

    EntityMutationResult execute(
            EntityMutationCommand command);

    EntityMutationBatchResult executeBatch(
            EntityMutationBatchCommand command);

    /**
     * 在不修改业务记录、不生成业务版本的前提下，对当前最终记录
     * 执行表单作用域唯一终检并协调占位。
     *
     * <p>用于审批表单实际已提交、但本次没有可编辑字段变化的场景。
     * context 必须由服务端携带实际应用的发布表单引用；无表单引用时
     * 调用方应直接跳过。</p>
     */
    void reconcileFormUniqueness(
            String entityCode,
            String recordId,
            EntityMutationContext context);
}
