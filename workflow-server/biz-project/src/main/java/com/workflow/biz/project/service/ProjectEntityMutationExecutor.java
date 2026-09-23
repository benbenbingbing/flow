package com.workflow.biz.project.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.entity.data.api.response.EntityDataDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 为项目治理编排统一实体变更上下文、幂等键和写入端口。
 */
@Component
@RequiredArgsConstructor
public class ProjectEntityMutationExecutor {

    private final EntityMutationPort entityMutationPort;
    private final ObjectMapper objectMapper;
    private final ThreadLocal<MutationSession> currentSession =
            new ThreadLocal<>();

    /**
     * 处理会话，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续会话步骤传递身份、配置或状态
     * @param businessIntentCode 业务{@code intent}编码，后续用于处理会话时定位或关联目标
     * @param businessIntentName 业务{@code intent}名称，后续用于处理会话时匹配或展示
     * @param action 动作标识，决定后续会话采用的处理分支
     * @return 处理后的会话结果，供调用方继续处理
     */
    public <T> T inSession(
            FlowActionContext context,
            String businessIntentCode,
            String businessIntentName,
            Supplier<T> action) {
        MutationSession previous = currentSession.get();
        if (previous != null) {
            return action.get();
        }
        currentSession.set(new MutationSession(
                context,
                businessIntentCode,
                businessIntentName,
                mutationBaseKey(context),
                mutationTraceKey(context),
                mutationExtraParams(context),
                new AtomicInteger()));
        try {
            return action.get();
        } finally {
            currentSession.remove();
        }
    }

    /**
     * 保存项目实体变更执行器；后续读取或执行将使用更新后的状态。
     *
     * @param dto DTO，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @return 保存后的项目实体变更执行器结果，供调用方继续处理
     */
    public EntityDataDTO save(EntityDataDTO dto) {
        Map<String, Object> payload = objectMapper.convertValue(
                dto,
                new TypeReference<>() {
                });
        EntityMutationResult result = execute(
                dto.getEntityCode(),
                null,
                EntityMutationOperationType.CREATE,
                payload);
        return objectMapper.convertValue(
                result.record(),
                EntityDataDTO.class);
    }

    /**
     * 更新项目实体变更执行器；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param payload 载荷，后续用于更新项目实体变更执行器并传递处理结果
     * @return 更新后的项目实体变更执行器结果，供调用方继续处理
     */
    public EntityDataDTO update(
            String entityCode,
            String recordId,
            Map<String, Object> payload) {
        MutationSession session = requireSession();
        EntityMutationOperationType operationType =
                "CHANGE_EFFECTIVE".equals(
                        session.businessIntentCode())
                        ? EntityMutationOperationType.APPLY_CHANGE
                        : EntityMutationOperationType.UPDATE;
        EntityMutationResult result = execute(
                entityCode,
                recordId,
                operationType,
                payload);
        return objectMapper.convertValue(
                result.record(),
                EntityDataDTO.class);
    }

    /**
     * 执行项目实体变更执行器，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param operationType 操作类型标识，决定后续项目实体变更执行器采用的处理分支
     * @param payload 载荷，后续用于执行项目实体变更执行器并传递处理结果
     * @return 执行后的项目实体变更执行器结果，供调用方继续处理
     */
    private EntityMutationResult execute(
            String entityCode,
            String recordId,
            EntityMutationOperationType operationType,
            Map<String, Object> payload) {
        MutationSession session = requireSession();
        String idempotencyKey = session.nextIdempotencyKey();
        return entityMutationPort.execute(
                new EntityMutationCommand(
                        idempotencyKey,
                        entityCode,
                        recordId,
                        operationType,
                        payload,
                        mutationContext(
                                session,
                                idempotencyKey)));
    }

    /**
     * 处理变更上下文，并将结果传给后续步骤。
     *
     * @param session 会话，作为 {@code extraParams} 的输入影响后续处理
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @return 处理后的变更上下文结果，供调用方继续处理
     */
    private EntityMutationContext mutationContext(
            MutationSession session,
            String idempotencyKey) {
        FlowActionContext flowContext =
                session.flowContext();
        EntityMutationContext.Builder builder =
                EntityMutationContext.builder(
                                flowContext == null
                                        ? EntityMutationSourceType.SYSTEM_TASK
                                        : EntityMutationSourceType.FLOW_ACTION,
                                session.businessIntentCode(),
                                session.businessIntentName())
                        .sourceId(flowContext == null
                                ? "ProjectGovernanceService"
                                : flowContext.getActionId())
                        .trace(
                                session.businessTraceKey(),
                                idempotencyKey)
                        .extraParams(session.extraParams());
        if (flowContext != null) {
            builder.sourceRecord(
                            flowContext.getEntityCode(),
                            flowContext.getEntityDataId())
                    .process(
                            flowContext.getProcessDefinitionId(),
                            flowContext.getProcessInstanceId(),
                            flowContext.getTaskId())
                    .operator(
                            flowContext.getOperatorId(),
                            flowContext.getOperatorId());
        }
        return builder.build();
    }

    /**
     * 校验并获取会话；不满足约束时阻止后续处理。
     *
     * @return 校验并获取后的会话结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private MutationSession requireSession() {
        MutationSession session = currentSession.get();
        if (session == null) {
            throw new IllegalStateException(
                    "项目治理写入缺少实体变更会话");
        }
        return session;
    }

    /**
     * 生成变更基础键文本，供后续匹配或展示。
     *
     * @param context 执行上下文，向后续变更基础键步骤传递身份、配置或状态
     * @return 处理后的变更基础键文本，供调用方比较或展示
     */
    private String mutationBaseKey(
            FlowActionContext context) {
        if (context != null
                && StringUtils.hasText(
                        context.getIdempotencyKey())) {
            return context.getIdempotencyKey();
        }
        return "project-governance:"
                + UUID.randomUUID();
    }

    /**
     * 生成变更追踪键文本，供后续匹配或展示。
     *
     * @param context 执行上下文，向后续变更追踪键步骤传递身份、配置或状态
     * @return 处理后的变更追踪键文本，供调用方比较或展示
     */
    private String mutationTraceKey(
            FlowActionContext context) {
        if (context != null
                && StringUtils.hasText(
                        context.getProcessInstanceId())) {
            return context.getProcessInstanceId();
        }
        return "project-governance:"
                + UUID.randomUUID();
    }

    /**
     * 进入会话时复制动作参数，保证多步实体写入使用同一份参数快照；无上下文时为空。
     *
     * @param context 执行上下文，向后续变更附加参数步骤传递身份、配置或状态
     * @return 变更附加参数键值结果，供调用方继续处理
     */
    private Map<String, Object> mutationExtraParams(
            FlowActionContext context) {
        if (context == null || context.getExtraParams() == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(context.getExtraParams());
    }

    /**
     * 封装变更会话的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param flowContext 执行上下文，向后续变更会话步骤传递身份、配置或状态
     * @param businessIntentCode 业务{@code intent}编码，后续用于处理变更会话时定位或关联目标
     * @param businessIntentName 业务{@code intent}名称，后续用于处理变更会话时匹配或展示
     * @param baseIdempotencyKey 基础幂等键，后续用于授权校验、关联或幂等去重
     * @param businessTraceKey 业务追踪键，后续用于授权校验、关联或幂等去重
     * @param extraParams 附加参数，后续传给解析器或执行器
     * @param sequence 序列，保存在对象中供后续校验、查询或展示
     */
    private record MutationSession(
            FlowActionContext flowContext,
            String businessIntentCode,
            String businessIntentName,
            String baseIdempotencyKey,
            String businessTraceKey,
            Map<String, Object> extraParams,
            AtomicInteger sequence) {

        /**
         * 生成下一步幂等键文本，供后续匹配或展示。
         *
         * @return 处理后的下一步幂等键文本，供调用方比较或展示
         */
        private String nextIdempotencyKey() {
            return baseIdempotencyKey
                    + ":mutation:"
                    + sequence.incrementAndGet();
        }
    }
}
