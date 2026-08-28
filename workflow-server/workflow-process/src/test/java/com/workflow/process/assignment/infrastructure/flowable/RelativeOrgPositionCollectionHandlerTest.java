package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.application.AssigneeIncidentRecorder;
import com.workflow.process.assignment.application.AssigneeResolutionService;
import com.workflow.process.assignment.application.EmptyAssigneePolicyResolver;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver.ResolvedAssignment;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelativeOrgPositionCollectionHandlerTest {

    private RepositoryService repositoryService;
    private MultiInstanceAssignmentResolver assignmentResolver;
    private NodeAssignmentReferenceResolver referenceResolver;
    private NextApproverOverrideStore overrideStore;
    private AssigneeIncidentRecorder incidentRecorder;
    private RelativeOrgPositionCollectionHandler handler;
    private UserTask task;

    @BeforeEach
    void setUp() {
        repositoryService = mock(RepositoryService.class);
        assignmentResolver = mock(MultiInstanceAssignmentResolver.class);
        referenceResolver = mock(NodeAssignmentReferenceResolver.class);
        overrideStore = mock(NextApproverOverrideStore.class);
        incidentRecorder = mock(AssigneeIncidentRecorder.class);
        handler = new RelativeOrgPositionCollectionHandler(
                repositoryService,
                mock(ProcessVersionHistoryMapper.class),
                new ObjectMapper(),
                assignmentResolver,
                referenceResolver,
                overrideStore,
                mock(EmptyAssigneePolicyResolver.class),
                mock(AssigneeResolutionService.class),
                incidentRecorder);
        task = relativeTask();
    }

    @Test
    void parallelAndSequentialCallbacksReuseOneStableCycleSnapshot() {
        mockDeployedResolution();
        when(assignmentResolver.resolve(
                any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of("alice", "bob"), List.of("changed"));
        DelegateExecution root = cycleExecution("root-1", null, true);
        DelegateExecution child = cycleExecution("child-1", root, false);

        Collection<?> countCollection = handler.resolveCollection(
                null, root);
        Collection<?> parallelElementCollection = handler.resolveCollection(
                null, child);
        Collection<?> sequentialNextCollection = handler.resolveCollection(
                null, child);

        assertEquals(List.of("alice", "bob"), countCollection);
        assertEquals(countCollection, parallelElementCollection);
        assertEquals(countCollection, sequentialNextCollection);
        verify(assignmentResolver, times(1)).resolve(
                any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void aFreshMultiInstanceRootRecalculatesOnLoopReentry() {
        mockDeployedResolution();
        when(assignmentResolver.resolve(
                any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of("alice"), List.of("new-leader"));
        DelegateExecution firstCycle = cycleExecution(
                "root-first", null, true);
        DelegateExecution secondCycle = cycleExecution(
                "root-second", null, true);

        assertEquals(
                List.of("alice"),
                handler.resolveCollection(null, firstCycle));
        assertEquals(
                List.of("new-leader"),
                handler.resolveCollection(null, secondCycle));

        verify(assignmentResolver, times(2)).resolve(
                any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void stagedOverrideIsConsumedOncePerCycleAndThenComesFromCache() {
        when(overrideStore.hasStagedOverride("instance-1", "approve"))
                .thenReturn(true);
        when(overrideStore.consumeForMultiInstance(
                "instance-1", "approve"))
                .thenReturn(
                        List.of("manual-a", "manual-b"),
                        List.of("manual-next"));
        DelegateExecution firstRoot = cycleExecution(
                "root-override-1", null, true);
        DelegateExecution firstChild = cycleExecution(
                "child-override-1", firstRoot, false);
        DelegateExecution secondRoot = cycleExecution(
                "root-override-2", null, true);

        assertEquals(
                List.of("manual-a", "manual-b"),
                handler.resolveCollection(null, firstRoot));
        assertEquals(
                List.of("manual-a", "manual-b"),
                handler.resolveCollection(null, firstChild));
        assertEquals(
                List.of("manual-next"),
                handler.resolveCollection(null, secondRoot));

        verify(overrideStore, times(2)).consumeForMultiInstance(
                "instance-1", "approve");
    }

    private void mockDeployedResolution() {
        org.flowable.bpmn.model.BpmnModel model =
                mock(org.flowable.bpmn.model.BpmnModel.class);
        when(repositoryService.getBpmnModel("definition-1"))
                .thenReturn(model);
        when(referenceResolver.resolve(
                eq(model), eq(task), any()))
                .thenAnswer(invocation -> new ResolvedAssignment(
                        task,
                        invocation.getArgument(2),
                        List.of(task.getId())));
        ProcessDefinitionQuery query = mock(
                ProcessDefinitionQuery.class, RETURNS_SELF);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(query);
        when(query.singleResult()).thenReturn(definition);
    }

    private DelegateExecution cycleExecution(
            String id,
            DelegateExecution parent,
            boolean multiInstanceRoot) {
        DelegateExecution execution = mock(DelegateExecution.class);
        AtomicReference<Object> local = new AtomicReference<>();
        when(execution.getId()).thenReturn(id);
        when(execution.getProcessInstanceId()).thenReturn("instance-1");
        when(execution.getProcessDefinitionId()).thenReturn("definition-1");
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(execution.getCurrentActivityId()).thenReturn(task.getId());
        when(execution.getVariables()).thenReturn(Map.of(
                "startUserId", "user-1"));
        when(execution.isMultiInstanceRoot())
                .thenReturn(multiInstanceRoot);
        when(execution.getParent()).thenReturn(parent);
        when(execution.getVariableLocal(
                RelativeOrgPositionCollectionHandler.CYCLE_CACHE_VARIABLE))
                .thenAnswer(invocation -> local.get());
        when(execution.setVariableLocal(
                eq(RelativeOrgPositionCollectionHandler
                        .CYCLE_CACHE_VARIABLE),
                any()))
                .thenAnswer(invocation -> {
                    local.set(invocation.getArgument(1));
                    return null;
                });
        return execution;
    }

    private UserTask relativeTask() {
        UserTask userTask = new UserTask();
        userTask.setId("approve");
        userTask.setName("审批");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        loop.setElementVariable("reviewer");
        userTask.setLoopCharacteristics(loop);

        ExtensionElement properties = extension("properties");
        ExtensionElement property = extension("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute(
                "value", """
                        {
                          "assignmentConfigVersion": 2,
                          "assigneeType": "interface",
                          "resolverCode": "relativeOrgPosition",
                          "extraParams": {
                            "schemaVersion": 1,
                            "subject": "PROCESS_INITIATOR",
                            "anchor": "DEPARTMENT",
                            "positionCode": "UNIT_LEADER",
                            "hierarchy": {"mode": "SELF"},
                            "multipleMatchPolicy": "ALL"
                          }
                        }
                        """));
        properties.addChildElement(property);
        userTask.addExtensionElement(properties);
        return userTask;
    }

    private ExtensionElement extension(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        return element;
    }
}
