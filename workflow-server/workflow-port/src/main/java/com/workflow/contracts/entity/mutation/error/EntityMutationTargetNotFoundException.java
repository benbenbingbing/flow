package com.workflow.contracts.entity.mutation.error;

/**
 * 实体写入所定位的记录不存在。
 *
 * <p>继承 {@link IllegalArgumentException} 以保持现有接口错误语义，同时允许流程结束、
 * Outbox 补偿等内部调用识别“目标已删除”的幂等终态。</p>
 */
public class EntityMutationTargetNotFoundException
        extends IllegalArgumentException {

    /**
     * 初始化实体变更目标非已找到异常，保存构造参数供后续方法使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    public EntityMutationTargetNotFoundException(
            String entityCode,
            String recordId) {
        super("实体数据不存在: " + entityCode + "/" + recordId);
    }
}
