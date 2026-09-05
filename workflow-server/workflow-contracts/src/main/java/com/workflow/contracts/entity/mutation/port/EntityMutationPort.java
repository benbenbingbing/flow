package com.workflow.contracts.entity.mutation.port;

import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationResult;

/**
 * 跨模块实体写入的稳定调用端口。
 *
 * <p>由实体模块实现；命令、结果和上下文在兼容期内继续使用原有 mutation model FQCN。</p>
 */
public interface EntityMutationPort {

    EntityMutationResult execute(EntityMutationCommand command);

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
