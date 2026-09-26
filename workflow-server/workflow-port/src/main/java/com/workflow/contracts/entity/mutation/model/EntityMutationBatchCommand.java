package com.workflow.contracts.entity.mutation.model;

import java.util.List;
import java.util.UUID;

/**
 * 一次业务操作中的多条实体变更。
 *
 * @param operationId 操作ID，后续用于处理实体变更批次命令时定位或关联目标
 * @param commands {@code commands}，保存在对象中供后续校验、查询或展示
 * @param atomic {@code atomic}，保存在对象中供后续校验、查询或展示
 */
public record EntityMutationBatchCommand(
        String operationId,
        List<EntityMutationCommand> commands,
        boolean atomic) {

    /**
     * 初始化实体变更批次命令，保存构造参数供后续方法使用。
     *
     * @param operationId 操作ID，后续用于初始化实体变更批次时定位或关联目标
     * @param commands {@code commands}，保存在对象中供后续校验、查询或展示
     * @param atomic {@code atomic}，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
