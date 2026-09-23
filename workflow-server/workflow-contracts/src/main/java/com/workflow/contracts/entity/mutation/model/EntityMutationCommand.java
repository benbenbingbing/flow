package com.workflow.contracts.entity.mutation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 单条实体变更命令。
 *
 * @param operationId 操作ID，后续用于处理实体变更命令时定位或关联目标
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param operationType 操作类型标识，决定后续实体变更命令采用的处理分支
 * @param payload 载荷，后续用于处理实体变更命令并传递处理结果
 * @param context 执行上下文，向后续实体变更命令步骤传递身份、配置或状态
 */
public record EntityMutationCommand(
        String operationId,
        String entityCode,
        String recordId,
        EntityMutationOperationType operationType,
        Map<String, Object> payload,
        EntityMutationContext context) {

    /**
     * 初始化实体变更命令，保存构造参数供后续方法使用。
     *
     * @param operationId 操作ID，后续用于初始化实体变更时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param operationType 操作类型标识，决定后续实体变更采用的处理分支
     * @param payload 载荷，后续用于初始化实体变更并传递处理结果
     * @param context 执行上下文，向后续实体变更步骤传递身份、配置或状态
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public EntityMutationCommand {
        if (entityCode == null || entityCode.isBlank()) {
            throw new IllegalArgumentException(
                    "实体编码不能为空");
        }
        if (operationType == null) {
            throw new IllegalArgumentException(
                    "实体变更操作类型不能为空");
        }
        if (operationType != EntityMutationOperationType.CREATE
                && (recordId == null || recordId.isBlank())) {
            throw new IllegalArgumentException(
                    "非新增操作的实体记录ID不能为空");
        }
        operationId = operationId == null
                || operationId.isBlank()
                ? UUID.randomUUID().toString()
                : operationId;
        entityCode = entityCode.trim();
        recordId = recordId == null
                ? null : recordId.trim();
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(payload));
        context = context == null
                ? EntityMutationContext.builder(
                        EntityMutationSourceType.SYSTEM_TASK,
                        "UNSPECIFIED",
                        "未指定业务变更")
                .build()
                : context;
    }

    /**
     * 创建实体变更；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param payload 载荷，后续用于创建实体变更并传递处理结果
     * @param context 执行上下文，向后续实体变更步骤传递身份、配置或状态
     * @return 创建后的实体变更结果，供调用方继续处理
     */
    public static EntityMutationCommand create(
            String entityCode,
            Map<String, Object> payload,
            EntityMutationContext context) {
        return new EntityMutationCommand(
                null,
                entityCode,
                null,
                EntityMutationOperationType.CREATE,
                payload,
                context);
    }

    /**
     * 更新实体变更；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param payload 载荷，后续用于更新实体变更并传递处理结果
     * @param context 执行上下文，向后续实体变更步骤传递身份、配置或状态
     * @return 更新后的实体变更结果，供调用方继续处理
     */
    public static EntityMutationCommand update(
            String entityCode,
            String recordId,
            Map<String, Object> payload,
            EntityMutationContext context) {
        return new EntityMutationCommand(
                null,
                entityCode,
                recordId,
                EntityMutationOperationType.UPDATE,
                payload,
                context);
    }

    /**
     * 删除实体变更；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param context 执行上下文，向后续实体变更步骤传递身份、配置或状态
     * @return 删除后的实体变更结果，供调用方继续处理
     */
    public static EntityMutationCommand delete(
            String entityCode,
            String recordId,
            EntityMutationContext context) {
        return new EntityMutationCommand(
                null,
                entityCode,
                recordId,
                EntityMutationOperationType.DELETE,
                Map.of(),
                context);
    }
}
