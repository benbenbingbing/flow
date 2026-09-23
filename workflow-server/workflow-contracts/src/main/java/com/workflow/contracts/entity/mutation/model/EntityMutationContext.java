package com.workflow.contracts.entity.mutation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 一次实体变更的业务上下文。
 *
 * <p>上下文只描述变更来源和业务意图，不允许调用方直接控制是否生成版本。</p>
 *
 * @param sourceType 来源类型标识，决定后续实体变更上下文采用的处理分支
 * @param sourceId 来源ID，后续用于处理实体变更上下文时定位或关联目标
 * @param businessIntentCode 业务{@code intent}编码，后续用于处理实体变更上下文时定位或关联目标
 * @param businessIntentName 业务{@code intent}名称，后续用于处理实体变更上下文时匹配或展示
 * @param sourceEntityCode 来源实体编码，后续用于处理实体变更上下文时定位或关联目标
 * @param sourceRecordId 来源记录ID，后续用于处理实体变更上下文时定位或关联目标
 * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
 * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
 * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
 * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param operatorName 用户名称，后续用于身份匹配或操作展示
 * @param businessTraceKey 业务追踪键，后续用于授权校验、关联或幂等去重
 * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
 * @param extraParams 附加参数，后续传给解析器或执行器
 */
public record EntityMutationContext(
        EntityMutationSourceType sourceType,
        String sourceId,
        String businessIntentCode,
        String businessIntentName,
        String sourceEntityCode,
        String sourceRecordId,
        String processDefinitionId,
        String processInstanceId,
        String taskId,
        String operatorId,
        String operatorName,
        String businessTraceKey,
        String idempotencyKey,
        Map<String, Object> extraParams) {

    /**
     * 初始化实体变更上下文，保存构造参数供后续方法使用。
     *
     * @param sourceType 来源类型标识，决定后续实体变更上下文采用的处理分支
     * @param sourceId 来源ID，后续用于初始化实体变更上下文时定位或关联目标
     * @param businessIntentCode 业务{@code intent}编码，后续用于初始化实体变更上下文时定位或关联目标
     * @param businessIntentName 业务{@code intent}名称，后续用于初始化实体变更上下文时匹配或展示
     * @param sourceEntityCode 来源实体编码，后续用于初始化实体变更上下文时定位或关联目标
     * @param sourceRecordId 来源记录ID，后续用于初始化实体变更上下文时定位或关联目标
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param operatorName 用户名称，后续用于身份匹配或操作展示
     * @param businessTraceKey 业务追踪键，后续用于授权校验、关联或幂等去重
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param extraParams 附加参数，后续传给解析器或执行器
     */
    public EntityMutationContext {
        sourceType = sourceType == null
                ? EntityMutationSourceType.SYSTEM_TASK
                : sourceType;
        businessIntentCode = text(
                businessIntentCode,
                "UNSPECIFIED");
        businessIntentName = text(
                businessIntentName,
                businessIntentCode);
        businessTraceKey = text(
                businessTraceKey,
                "mutation_" + UUID.randomUUID());
        idempotencyKey = text(
                idempotencyKey,
                businessTraceKey);
        extraParams = extraParams == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(extraParams));
    }

    /**
     * 处理构建器，并将结果传给后续步骤。
     *
     * @param sourceType 来源类型标识，决定后续构建器采用的处理分支
     * @param businessIntentCode 业务{@code intent}编码，后续用于处理构建器时定位或关联目标
     * @param businessIntentName 业务{@code intent}名称，后续用于处理构建器时匹配或展示
     * @return 处理后的构建器结果，供调用方继续处理
     */
    public static Builder builder(
            EntityMutationSourceType sourceType,
            String businessIntentCode,
            String businessIntentName) {
        return new Builder(
                sourceType,
                businessIntentCode,
                businessIntentName);
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(
            String value,
            String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim();
    }

    /**
     * 便于跨模块组装上下文的无框架 Builder。
     */
    public static final class Builder {

        private final EntityMutationSourceType sourceType;
        private final String businessIntentCode;
        private final String businessIntentName;
        private String sourceId;
        private String sourceEntityCode;
        private String sourceRecordId;
        private String processDefinitionId;
        private String processInstanceId;
        private String taskId;
        private String operatorId;
        private String operatorName;
        private String businessTraceKey;
        private String idempotencyKey;
        private Map<String, Object> extraParams = Map.of();

        /**
         * 初始化构建器，保存构造参数供后续方法使用。
         *
         * @param sourceType 来源类型依赖，保存到当前对象供后续业务方法调用
         * @param businessIntentCode 业务{@code intent}编码依赖，保存到当前对象供后续业务方法调用
         * @param businessIntentName 业务{@code intent}名称依赖，保存到当前对象供后续业务方法调用
         */
        private Builder(
                EntityMutationSourceType sourceType,
                String businessIntentCode,
                String businessIntentName) {
            this.sourceType = sourceType;
            this.businessIntentCode = businessIntentCode;
            this.businessIntentName = businessIntentName;
        }

        /**
         * 处理来源ID，并将结果传给后续步骤。
         *
         * @param value 待处理来源ID的原始输入，结果供调用方继续使用
         * @return 处理后的来源ID结果，供调用方继续处理
         */
        public Builder sourceId(String value) {
            sourceId = value;
            return this;
        }

        /**
         * 处理来源记录，并将结果传给后续步骤。
         *
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
         * @return 处理后的来源记录结果，供调用方继续处理
         */
        public Builder sourceRecord(
                String entityCode,
                String recordId) {
            sourceEntityCode = entityCode;
            sourceRecordId = recordId;
            return this;
        }

        /**
         * 处理构建器，并将结果传给后续步骤。
         *
         * @param definitionId 定义ID，后续用于处理构建器时定位或关联目标
         * @param instanceId 实例ID，后续用于处理构建器时定位或关联目标
         * @param currentTaskId 当前任务ID，写入当前任务信息供后续待办展示和状态同步
         * @return 处理后的构建器结果，供调用方继续处理
         */
        public Builder process(
                String definitionId,
                String instanceId,
                String currentTaskId) {
            processDefinitionId = definitionId;
            processInstanceId = instanceId;
            taskId = currentTaskId;
            return this;
        }

        /**
         * 处理操作人，并将结果传给后续步骤。
         *
         * @param id 目标记录 ID，后续用于定位具体数据或配置
         * @param name 名称，后续用于处理操作人时匹配或展示
         * @return 处理后的操作人结果，供调用方继续处理
         */
        public Builder operator(
                String id,
                String name) {
            operatorId = id;
            operatorName = name;
            return this;
        }

        /**
         * 处理追踪，并将结果传给后续步骤。
         *
         * @param traceKey 追踪键，后续用于授权校验、关联或幂等去重
         * @param mutationIdempotencyKey 变更幂等键，后续用于授权校验、关联或幂等去重
         * @return 处理后的追踪结果，供调用方继续处理
         */
        public Builder trace(
                String traceKey,
                String mutationIdempotencyKey) {
            businessTraceKey = traceKey;
            idempotencyKey = mutationIdempotencyKey;
            return this;
        }

        /**
         * 处理附加参数，并将结果传给后续步骤。
         *
         * @param value 待处理附加参数的原始输入，结果供调用方继续使用
         * @return 处理后的附加参数结果，供调用方继续处理
         */
        public Builder extraParams(
                Map<String, Object> value) {
            extraParams = value;
            return this;
        }

        /**
         * 构建构建器；结果供后续流程传递或持久化。
         *
         * @return 构建后的构建器结果，供调用方继续处理
         */
        public EntityMutationContext build() {
            return new EntityMutationContext(
                    sourceType,
                    sourceId,
                    businessIntentCode,
                    businessIntentName,
                    sourceEntityCode,
                    sourceRecordId,
                    processDefinitionId,
                    processInstanceId,
                    taskId,
                    operatorId,
                    operatorName,
                    businessTraceKey,
                    idempotencyKey,
                    extraParams);
        }
    }
}
