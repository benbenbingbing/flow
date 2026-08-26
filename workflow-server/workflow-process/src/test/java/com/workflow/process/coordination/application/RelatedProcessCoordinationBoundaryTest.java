package com.workflow.process.coordination.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionExecutionMode;
import com.workflow.contracts.action.FlowActionFailurePolicy;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.model.EntityRelationGraph.RecordRef;
import com.workflow.entity.definition.application.model.PublishedRelationPath;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Command;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Operation;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.ProcessState;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Source;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.TargetImpact;
import com.workflow.process.task.application.ProcessTaskService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelatedProcessCoordinationBoundaryTest {

    @Test
    void publisherUsesStablePerTargetOutboxKey() {
        OutboxPublisher outbox = mock(OutboxPublisher.class);
        RelatedProcessCoordinationPublisher publisher =
                new RelatedProcessCoordinationPublisher(outbox);
        RelatedProcessCoordinationPlan plan = plan(
                Operation.PROPAGATE_TERMINATION,
                "graph-1",
                activeTarget("req-1", "process-1", "definition-1"));
        Command command = command(
                Operation.PROPAGATE_TERMINATION);

        publisher.publish(plan, command);
        publisher.publish(plan, command);

        ArgumentCaptor<OutboxPublishRequest> requests =
                ArgumentCaptor.forClass(OutboxPublishRequest.class);
        verify(outbox, times(2)).publish(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(OutboxPublishRequest::eventKey)
                .containsExactly(
                        requests.getAllValues().get(0).eventKey(),
                        requests.getAllValues().get(0).eventKey());
        assertThat(requests.getValue().topic())
                .isEqualTo(RelatedProcessCoordinationPublisher.TOPIC);
    }

    @Test
    void executionRejectsChangedRelationGraphBeforeProcessMutation() {
        RelatedProcessCoordinationPlanService planService =
                mock(RelatedProcessCoordinationPlanService.class);
        RuntimeService runtime = mock(RuntimeService.class);
        RelatedProcessCoordinationExecutionService service =
                new RelatedProcessCoordinationExecutionService(
                        planService,
                        runtime,
                        mock(HistoryService.class),
                        mock(ProcessTaskService.class),
                        mock(ProcessOperationLogMapper.class),
                        new ObjectMapper());
        TargetImpact target = activeTarget(
                "req-1", "process-1", "definition-1");
        RelatedProcessCoordinationPlan original = plan(
                Operation.PROPAGATE_TERMINATION,
                "graph-before",
                target);
        RelatedProcessCoordinationPlan current = plan(
                Operation.PROPAGATE_TERMINATION,
                "graph-after",
                target);
        Command command = command(
                Operation.PROPAGATE_TERMINATION);
        when(planService.replan(original.source(), command))
                .thenReturn(current);

        String eventKey = RelatedProcessCoordinationPublisher.eventKey(
                original, target);
        assertThatThrownBy(() -> service.execute(
                "outbox-1",
                eventKey,
                new RelatedProcessCoordinationEvent(
                        original, command, target)))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(error -> assertThat(
                        ((BusinessConflictException) error).getErrorCode())
                        .isEqualTo("RELATED_PROCESS_GRAPH_CHANGED"));
        verify(runtime, never()).deleteProcessInstance(any(), any());
    }

    @Test
    void executionRejectsTargetProcessVersionDrift() {
        RelatedProcessCoordinationPlanService planService =
                mock(RelatedProcessCoordinationPlanService.class);
        RuntimeService runtime = mock(RuntimeService.class);
        RelatedProcessCoordinationExecutionService service =
                new RelatedProcessCoordinationExecutionService(
                        planService,
                        runtime,
                        mock(HistoryService.class),
                        mock(ProcessTaskService.class),
                        mock(ProcessOperationLogMapper.class),
                        new ObjectMapper());
        TargetImpact originalTarget = activeTarget(
                "req-1", "process-1", "definition-1");
        TargetImpact drifted = new TargetImpact(
                originalTarget.record(),
                ProcessState.ACTIVE,
                "link-1",
                "process-2",
                "definition-2",
                "history-2",
                2,
                "requirement-flow",
                "PROCESSING",
                List.of("approve"));
        RelatedProcessCoordinationPlan original = plan(
                Operation.PROPAGATE_TERMINATION,
                "graph-1",
                originalTarget);
        RelatedProcessCoordinationPlan current = plan(
                Operation.PROPAGATE_TERMINATION,
                "graph-1",
                drifted);
        Command command = command(
                Operation.PROPAGATE_TERMINATION);
        when(planService.replan(original.source(), command))
                .thenReturn(current);

        String eventKey = RelatedProcessCoordinationPublisher.eventKey(
                original, originalTarget);
        assertThatThrownBy(() -> service.execute(
                "outbox-1",
                eventKey,
                new RelatedProcessCoordinationEvent(
                        original, command, originalTarget)))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(error -> assertThat(
                        ((BusinessConflictException) error).getErrorCode())
                        .isEqualTo("RELATED_PROCESS_VERSION_DRIFTED"));
        verify(runtime, never()).deleteProcessInstance(any(), any());
    }

    @Test
    void successfulTerminationWritesMarkerEffectAndStableAudit() {
        RelatedProcessCoordinationPlanService planService =
                mock(RelatedProcessCoordinationPlanService.class);
        RuntimeService runtime = mock(RuntimeService.class);
        ProcessTaskService taskService = mock(ProcessTaskService.class);
        ProcessOperationLogMapper logMapper =
                mock(ProcessOperationLogMapper.class);
        RelatedProcessCoordinationExecutionService service =
                new RelatedProcessCoordinationExecutionService(
                        planService,
                        runtime,
                        mock(HistoryService.class),
                        taskService,
                        logMapper,
                        new ObjectMapper());
        TargetImpact target = activeTarget(
                "req-1", "process-1", "definition-1");
        RelatedProcessCoordinationPlan plan = plan(
                Operation.PROPAGATE_TERMINATION,
                "graph-1",
                target);
        Command command = command(
                Operation.PROPAGATE_TERMINATION);
        when(planService.replan(plan.source(), command))
                .thenReturn(plan);
        String eventKey = RelatedProcessCoordinationPublisher.eventKey(
                plan, target);

        var result = service.execute(
                "outbox-1",
                eventKey,
                new RelatedProcessCoordinationEvent(
                        plan, command, target));

        assertThat(result.status()).isEqualTo("EXECUTED");
        verify(runtime).setVariable(
                "process-1",
                "_relatedProcessCoordinationTerminationKey",
                eventKey);
        verify(runtime).deleteProcessInstance(
                "process-1",
                "宿主终止 [coordination:" + eventKey + "]");
        verify(taskService).deleteTasksByProcessInstance("process-1");
        ArgumentCaptor<ProcessOperationLog> audit =
                ArgumentCaptor.forClass(ProcessOperationLog.class);
        verify(logMapper).insert(audit.capture());
        assertThat(audit.getValue().getId()).isEqualTo("outbox-1");
        assertThat(audit.getValue().getOperationType())
                .isEqualTo("PROPAGATE_TERMINATION");
    }

    @Test
    void replayStillReauthorizesButDoesNotRepeatVisibleEffect() {
        RelatedProcessCoordinationPlanService planService =
                mock(RelatedProcessCoordinationPlanService.class);
        RuntimeService runtime = mock(RuntimeService.class);
        ProcessOperationLogMapper logMapper =
                mock(ProcessOperationLogMapper.class);
        RelatedProcessCoordinationExecutionService service =
                new RelatedProcessCoordinationExecutionService(
                        planService,
                        runtime,
                        mock(HistoryService.class),
                        mock(ProcessTaskService.class),
                        logMapper,
                        new ObjectMapper());
        TargetImpact target = activeTarget(
                "req-1", "process-1", "definition-1");
        RelatedProcessCoordinationPlan plan = plan(
                Operation.PROPAGATE_TERMINATION,
                "graph-1",
                target);
        Command command = command(
                Operation.PROPAGATE_TERMINATION);
        when(planService.replan(plan.source(), command))
                .thenReturn(plan);
        when(logMapper.selectById("outbox-1"))
                .thenReturn(new ProcessOperationLog());
        String eventKey = RelatedProcessCoordinationPublisher.eventKey(
                plan, target);

        var result = service.execute(
                "outbox-1",
                eventKey,
                new RelatedProcessCoordinationEvent(
                        plan, command, target));

        assertThat(result.status()).isEqualTo("IDEMPOTENT_REPLAY");
        verify(planService).replan(plan.source(), command);
        verify(runtime, never()).deleteProcessInstance(any(), any());
    }

    @Test
    void blockingChecksRejectAfterCommitConfigurationBeforePlanning() {
        RelatedProcessCoordinationPlanService planService =
                mock(RelatedProcessCoordinationPlanService.class);
        RelatedProcessCoordinationFlowActionHandler handler =
                new RelatedProcessCoordinationFlowActionHandler(
                        planService,
                        mock(RelatedProcessCoordinationPublisher.class));
        FlowActionContext context = new FlowActionContext();
        context.setExecutionMode(
                FlowActionExecutionMode.AFTER_COMMIT.name());
        context.setFailurePolicy(
                FlowActionFailurePolicy.RETRY.name());
        Command command = new Command(
                Operation.WAIT_RELATED_PROCESSES,
                path(),
                Set.of(ProcessState.COMPLETED),
                1,
                null,
                null);

        assertThatThrownBy(() -> handler.execute(context, command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("事务内执行且失败回滚");
        verify(planService, never()).plan(any(), any());
    }

    private RelatedProcessCoordinationPlan plan(
            Operation operation,
            String graphFingerprint,
            TargetImpact target) {
        Source source = new Source(
                "action-1",
                "execution-1",
                "source-history-1",
                "source-definition-1",
                "source-process-1",
                "project",
                "project-1",
                "user-1",
                "测试用户");
        return new RelatedProcessCoordinationPlan(
                "plan-1",
                graphFingerprint,
                operation,
                source,
                path(),
                List.of(target),
                Set.of(),
                operation == Operation.ROUTE_RELATED_PARENT
                        ? "remediation" : null,
                "宿主终止",
                1,
                0,
                0,
                true);
    }

    private TargetImpact activeTarget(
            String recordId,
            String processInstanceId,
            String definitionId) {
        return new TargetImpact(
                new RecordRef("requirement", recordId),
                ProcessState.ACTIVE,
                "link-1",
                processInstanceId,
                definitionId,
                "history-1",
                1,
                "requirement-flow",
                "PROCESSING",
                List.of("approve"));
    }

    private Command command(Operation operation) {
        return new Command(
                operation,
                path(),
                Set.of(),
                1,
                null,
                "宿主终止");
    }

    private PublishedRelationPath path() {
        return new PublishedRelationPath(
                "project", "source-history-1", "schema-1", List.of());
    }
}
