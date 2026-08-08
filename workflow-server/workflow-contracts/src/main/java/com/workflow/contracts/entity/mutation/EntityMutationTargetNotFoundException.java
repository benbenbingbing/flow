package com.workflow.contracts.entity.mutation;

/**
 * 实体变更目标记录不存在。
 *
 * <p>继承 {@link IllegalArgumentException} 以保持现有接口错误语义，同时允许流程结束、
 * Outbox 补偿等内部调用识别“目标已删除”的幂等终态。</p>
 */
public class EntityMutationTargetNotFoundException
        extends IllegalArgumentException {

    public EntityMutationTargetNotFoundException(
            String entityCode,
            String recordId) {
        super("实体数据不存在: " + entityCode + "/" + recordId);
    }
}
