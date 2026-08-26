package com.workflow.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.audit.infrastructure.SystemAuditAspect;
import com.workflow.contracts.action.FlowActionCatalogPort;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.entity.version.application.EntityMutationPipeline;
import com.workflow.entity.version.application.EntityMutationStepExecutor;
import com.workflow.entity.version.application.EntityMutationTransactionExecutor;
import com.workflow.core.web.CorrelationContext;
import com.workflow.process.action.application.FlowActionExecutionService;
import com.workflow.process.action.domain.FlowActionTriggerEvent;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionExecutionMapper;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.action.infrastructure.persistence.record.FlowActionExecution;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 HTTP Trace 进入审计 AOP 后，operationId 能继续传播到实体变更与
 * FlowAction 的持久化审计事件，且不会覆盖 traceId。
 */
public class UnifiedAuditOperationPropagationTest {

    @Test
    void propagatesHttpOperationAcrossEntityMutationAndFlowAction() {
        RecordingAuditPort auditPort = new RecordingAuditPort();
        EntityMutationPipeline mutationPipeline = mutationPipeline(auditPort);
        FlowActionExecutionService flowActionService =
                flowActionService(auditPort);
        OperationOrchestrator target = new OperationOrchestrator(
                mutationPipeline,
                flowActionService);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new SystemAuditAspect(
                auditPort,
                new ObjectMapper().findAndRegisterModules()));
        OperationOrchestrator proxy = factory.getProxy();

        MDC.put(CorrelationContext.BUSINESS_TRACE_MDC_KEY,
                "http-trace-1");
        try {
            proxy.execute(new OperationRequest("http-operation-1"));
        } finally {
            MDC.remove(CorrelationContext.BUSINESS_TRACE_MDC_KEY);
        }

        assertEquals(3, auditPort.events.size());
        assertTrue(auditPort.events.stream().allMatch(event ->
                "http-operation-1".equals(event.operationId())));
        assertTrue(auditPort.events.stream().allMatch(event ->
                "http-trace-1".equals(event.traceId())));
        assertTrue(auditPort.events.stream().anyMatch(event ->
                "ENTITY_MUTATION".equals(
                        event.sourcePointer().sourceSystem())));
        assertTrue(auditPort.events.stream().anyMatch(event ->
                "FLOW_ACTION_EXECUTION".equals(
                        event.sourcePointer().sourceType())));
        assertTrue(auditPort.events.stream().anyMatch(event ->
                "HTTP_OPERATION".equals(event.targetType())));
    }

    private EntityMutationPipeline mutationPipeline(
            SystemAuditPort auditPort) {
        EntityMutationStepExecutor stepExecutor =
                mock(EntityMutationStepExecutor.class);
        EntityMutationTransactionExecutor transactionExecutor =
                mock(EntityMutationTransactionExecutor.class);
        EntityMutationCommand command = mutationCommand();
        when(stepExecutor.execute(
                eq(command),
                eq(EntityMutationPhase.PREPARE),
                anyMap(),
                anyMap())).thenReturn(
                new EntityMutationStepExecutor.ExecutionOutcome(
                        command,
                        List.of()));
        when(transactionExecutor.execute(command)).thenReturn(
                new EntityMutationResult(
                        command.operationId(),
                        command.entityCode(),
                        command.recordId(),
                        command.operationType(),
                        Map.of("data", Map.of("status", "DONE")),
                        null,
                        null,
                        true,
                        false));
        return new EntityMutationPipeline(
                stepExecutor,
                transactionExecutor,
                auditPort);
    }

    private FlowActionExecutionService flowActionService(
            SystemAuditPort auditPort) {
        FlowActionExecutionMapper mapper =
                mock(FlowActionExecutionMapper.class);
        doAnswer(invocation -> {
            FlowActionExecution execution = invocation.getArgument(0);
            execution.setId("flow-execution-1");
            return 1;
        }).when(mapper).insert(any(FlowActionExecution.class));
        return new FlowActionExecutionService(
                mapper,
                mock(FlowActionMapper.class),
                new ObjectMapper().findAndRegisterModules(),
                mock(FlowActionCatalogPort.class),
                auditPort);
    }

    private EntityMutationCommand mutationCommand() {
        return new EntityMutationCommand(
                "entity-mutation-1",
                "requirement",
                "requirement-1",
                EntityMutationOperationType.UPDATE,
                Map.of("data", Map.of("status", "DONE")),
                EntityMutationContext.builder(
                                EntityMutationSourceType.FORM,
                                "EDIT_REQUIREMENT",
                                "编辑需求")
                        .sourceId("form-1")
                        .operator("user-1", "张三")
                        .trace("mutation-trace", "mutation-idempotency")
                        .build());
    }

    static class OperationOrchestrator {

        private final EntityMutationPipeline mutationPipeline;
        private final FlowActionExecutionService flowActionService;

        OperationOrchestrator(
                EntityMutationPipeline mutationPipeline,
                FlowActionExecutionService flowActionService) {
            this.mutationPipeline = mutationPipeline;
            this.flowActionService = flowActionService;
        }

        @SystemAudit(
                module = AuditModule.SYSTEM,
                action = AuditAction.OTHER,
                operation = "HTTP业务操作",
                targetType = "HTTP_OPERATION")
        public void execute(OperationRequest request) {
            mutationPipeline.execute(mutationCommandStatic());
            FlowAction action = new FlowAction();
            action.setId("flow-action-1");
            action.setActionName("同步项目状态");
            action.setInterfaceName("projectHandler");
            FlowActionTriggerEvent event = new FlowActionTriggerEvent();
            event.setProcessInstanceId("process-1");
            event.setOperatorId("user-1");
            event.setOperatorName("张三");
            flowActionService.create(
                    action,
                    event,
                    "flow-idempotency-1",
                    FlowActionExecution.Status.PENDING);
        }

        private static EntityMutationCommand mutationCommandStatic() {
            return new EntityMutationCommand(
                    "entity-mutation-1",
                    "requirement",
                    "requirement-1",
                    EntityMutationOperationType.UPDATE,
                    Map.of("data", Map.of("status", "DONE")),
                    EntityMutationContext.builder(
                                    EntityMutationSourceType.FORM,
                                    "EDIT_REQUIREMENT",
                                    "编辑需求")
                            .sourceId("form-1")
                            .operator("user-1", "张三")
                            .trace("mutation-trace", "mutation-idempotency")
                            .build());
        }
    }

    public static final class OperationRequest {
        private final String operationId;

        OperationRequest(String operationId) {
            this.operationId = operationId;
        }

        public String getOperationId() {
            return operationId;
        }
    }

    static final class RecordingAuditPort implements SystemAuditPort {
        private final List<SystemAuditEvent> events = new ArrayList<>();

        @Override
        public void record(SystemAuditEvent event) {
            events.add(event);
        }
    }
}
