package com.workflow.project.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
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

    private MutationSession requireSession() {
        MutationSession session = currentSession.get();
        if (session == null) {
            throw new IllegalStateException(
                    "项目治理写入缺少实体变更会话");
        }
        return session;
    }

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

    private Map<String, Object> mutationExtraParams(
            FlowActionContext context) {
        if (context == null) {
            return Map.of();
        }
        Map<String, Object> result =
                new LinkedHashMap<>();
        if (context.getCustomParams() != null) {
            result.putAll(context.getCustomParams());
        }
        if (context.getExtraParams() != null) {
            result.putAll(context.getExtraParams());
        }
        return result;
    }

    private record MutationSession(
            FlowActionContext flowContext,
            String businessIntentCode,
            String businessIntentName,
            String baseIdempotencyKey,
            String businessTraceKey,
            Map<String, Object> extraParams,
            AtomicInteger sequence) {

        private String nextIdempotencyKey() {
            return baseIdempotencyKey
                    + ":mutation:"
                    + sequence.incrementAndGet();
        }
    }
}
