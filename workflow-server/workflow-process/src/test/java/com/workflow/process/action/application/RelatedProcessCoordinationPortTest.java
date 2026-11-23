package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.model.EntityRelationGraph.RecordRef;
import com.workflow.process.coordination.application.RelatedProcessCoordinationAdapter;
import com.workflow.process.coordination.application.RelatedProcessCoordinationFlowActionHandler;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Operation;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.ProcessState;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.TargetImpact;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlanService;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPublisher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelatedProcessCoordinationPortTest {
    private final RelatedProcessCoordinationPlanService planner = mock(RelatedProcessCoordinationPlanService.class);
    private final RelatedProcessCoordinationPublisher publisher = mock(RelatedProcessCoordinationPublisher.class);
    private final RelatedProcessCoordinationAdapter port = new RelatedProcessCoordinationAdapter(
            new RelatedProcessCoordinationFlowActionHandler(planner, publisher), planner, publisher, new ObjectMapper());

    @Test
    void keepsPublishedSourceStableAndQueuesOnlyOnExecution() {
        var context = new FlowActionContext();
        context.setEntityDataId("verified-record");
        context.setActionId("verified-action");
        context.setIdempotencyKey("execution-key");
        var target = new TargetImpact(new RecordRef("project", "related-record"), ProcessState.ACTIVE,
                "link", "related-process", "definition", "version", 1, "process", "ACTIVE", List.of("review"));
        var plan = new RelatedProcessCoordinationPlan("plan", "fingerprint", Operation.ROUTE_RELATED_PARENT,
                null, null, List.of(target), Set.of(), "target-node", "reason", 1, 0, 0, true);
        when(planner.plan(argThat(c -> "verified-record".equals(c.getEntityDataId())
                        && "execution-key".equals(c.getIdempotencyKey())),
                argThat(command -> Operation.ROUTE_RELATED_PARENT.equals(command.operation())))).thenReturn(plan);
        when(publisher.publish(eq(plan), any())).thenReturn(1);

        PublishedFlowActionInvocation.run(context, """
                {"relatedProcessCoordination":{"operation":"ROUTE_RELATED_PARENT","targetActivityId":"target-node"}}
                """, () -> {
            context.setEntityDataId("overwritten-record");
            assertEquals("related-record", port.preview().targets().get(0).recordId());
            verifyNoInteractions(publisher);
            assertEquals(1, port.execute().queuedTargets());
        });

        assertThrows(ForbiddenException.class, port::preview);
        verify(publisher).publish(eq(plan), any());
    }

    @Test
    void deniesCallsOutsidePublishedActionAndClearsScopeAfterFailure() {
        assertThrows(ForbiddenException.class, port::execute);
        var context = new FlowActionContext();
        assertThrows(IllegalArgumentException.class,
                () -> PublishedFlowActionInvocation.run(context, "{}", port::execute));
        assertThrows(ForbiddenException.class, port::execute);
        verifyNoInteractions(planner, publisher);
    }
}
