package com.workflow.process.task.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiInstanceOutcomeServiceTest {

    private RuntimeService runtimeService;
    private RepositoryService repositoryService;
    private TaskService taskService;
    private MultiInstanceOutcomeService service;
    private Task task;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        repositoryService = mock(RepositoryService.class);
        taskService = mock(TaskService.class);
        service = new MultiInstanceOutcomeService(
                runtimeService,
                repositoryService,
                taskService,
                new ObjectMapper());
        task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getProcessInstanceId()).thenReturn("proc-1");
        when(task.getProcessDefinitionId()).thenReturn("def-1");
        when(task.getTaskDefinitionKey()).thenReturn("joint-review");
    }

    @Test
    void countersignRejectDoesNotFinishWhenRemainingVotesCanStillMeetRate() {
        stubMultiInstanceUserTask("countersign", 50, false);
        stubCounts(4, 0, 0);

        assertEquals(
                MultiInstanceOutcomeService.MultiInstanceProjection.CONTINUE,
                service.project(task, "reject"));
        assertFalse(service.willFinishCurrentNode(task, "reject"));
        assertNull(service.resolveApprovedOutcome(task, "reject"));
    }

    @Test
    void countersignRejectFinishesWhenRemainingVotesCannotMeetRate() {
        stubMultiInstanceUserTask("countersign", 100, false);
        stubCounts(3, 0, 0);

        assertEquals(
                MultiInstanceOutcomeService.MultiInstanceProjection.FAIL,
                service.project(task, "reject"));
        assertTrue(service.willFinishCurrentNode(task, "reject"));
        assertEquals("reject", service.resolveApprovedOutcome(task, "reject"));
    }

    @Test
    void countersignNeedAllWaitsAfterRejectEvenIfRateIsAlreadyImpossible() {
        stubMultiInstanceUserTask("countersign", 100, true);
        stubCounts(3, 0, 0);

        assertEquals(
                MultiInstanceOutcomeService.MultiInstanceProjection.CONTINUE,
                service.project(task, "reject"));
        assertFalse(service.willFinishCurrentNode(task, "reject"));
        assertNull(service.resolveApprovedOutcome(task, "reject"));
    }

    @Test
    void countersignNeedAllJudgesRateOnlyAfterEveryoneFinished() {
        stubMultiInstanceUserTask("countersign", 50, true);
        stubCounts(3, 2, 1);

        assertEquals(
                MultiInstanceOutcomeService.MultiInstanceProjection.PASS,
                service.project(task, "approve"));
        assertEquals("approve", service.resolveApprovedOutcome(task, "approve"));

        stubCounts(3, 2, 0);
        assertEquals(
                MultiInstanceOutcomeService.MultiInstanceProjection.FAIL,
                service.project(task, "reject"));
        assertEquals("reject", service.resolveApprovedOutcome(task, "reject"));
    }

    @Test
    void orSignApproveFinishesImmediately() {
        stubMultiInstanceUserTask("orsign", 100, false);

        assertTrue(service.willFinishCurrentNode(task, "approve"));
        assertEquals("approve", service.resolveApprovedOutcome(task, "approve"));
    }

    @Test
    void orSignRejectStillVetoesImmediately() {
        stubMultiInstanceUserTask("orsign", 100, false);

        assertTrue(service.willFinishCurrentNode(task, "reject"));
        assertEquals("reject", service.resolveApprovedOutcome(task, "reject"));
        service.recordReject(task);
        verify(runtimeService).setVariable(
                "proc-1", "_wf_mi_rejected_joint_review", true);
    }

    @Test
    void countersignRejectDoesNotWriteVetoFlag() {
        stubMultiInstanceUserTask("countersign", 50, false);
        stubCounts(4, 0, 0);

        service.recordReject(task);

        verify(runtimeService, never()).setVariable(
                org.mockito.ArgumentMatchers.eq("proc-1"),
                org.mockito.ArgumentMatchers.eq("_wf_mi_rejected_joint_review"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void countersignDefersBeforeThreshold() {
        stubMultiInstanceUserTask("countersign", 50, false);
        stubCounts(3, 0, 0);

        assertFalse(service.willFinishCurrentNode(task, "approve"));
    }

    @Test
    void countersignFinishesWhenRateIsMet() {
        stubMultiInstanceUserTask("countersign", 50, false);
        stubCounts(3, 1, 1);

        assertTrue(service.willFinishCurrentNode(task, "approve"));
        assertEquals("approve", service.resolveApprovedOutcome(task, "approve"));
    }

    @Test
    void customActionDoesNotCountAsApproveButCanFailWhenImpossible() {
        stubMultiInstanceUserTask("countersign", 100, false);
        stubCounts(3, 0, 0);

        assertEquals("needMeeting", service.normalizeAction("needMeeting"));
        assertEquals(
                MultiInstanceOutcomeService.MultiInstanceProjection.FAIL,
                service.project(task, "needMeeting"));
    }

    @Test
    void customActionContinuesWhenVotesRemain() {
        stubMultiInstanceUserTask("countersign", 50, false);
        stubCounts(4, 0, 0);

        assertEquals(
                MultiInstanceOutcomeService.MultiInstanceProjection.CONTINUE,
                service.project(task, "needMeeting"));
    }

    @Test
    void intermediateRejectDoesNotOverwriteExistingApprove() {
        stubMultiInstanceUserTask("countersign", 50, false);
        stubCounts(4, 1, 1);
        when(runtimeService.getVariable("proc-1", "approved"))
                .thenReturn("approve");

        assertFalse(service.willFinishCurrentNode(task, "reject"));
        assertEquals("approve", service.resolveApprovedOutcome(task, "reject"));
    }

    @Test
    void completionRateZeroIsRaisedToOne() {
        assertEquals(1, MultiInstanceOutcomeService.normalizeCompletionRate(0));
        assertEquals(
                MultiInstanceOutcomeService.DEFAULT_COMPLETION_RATE,
                MultiInstanceOutcomeService.normalizeCompletionRate(null));
    }

    private void stubCounts(int instances, int completed, int approved) {
        when(taskService.getVariableLocal("task-1", "nrOfInstances"))
                .thenReturn(instances);
        when(taskService.getVariableLocal("task-1", "nrOfCompletedInstances"))
                .thenReturn(completed);
        when(runtimeService.getVariable(
                "proc-1", "_wf_mi_approved_count_joint_review"))
                .thenReturn(approved);
    }

    private void stubMultiInstanceUserTask(
            String decision,
            int rate,
            boolean needAll) {
        UserTask userTask = new UserTask();
        userTask.setId("joint-review");
        userTask.setLoopCharacteristics(new MultiInstanceLoopCharacteristics());
        userTask.addExtensionElement(property(
                "assigneeConfig",
                "{\"multiInstanceDecision\":\"" + decision + "\"}"));
        userTask.addExtensionElement(property(
                "multiInstanceConfig",
                "{\"multiInstanceCompletionRate\":" + rate
                        + ",\"multiInstanceNeedAllApprovers\":" + needAll + "}"));
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.addFlowElement(userTask);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        when(repositoryService.getBpmnModel("def-1")).thenReturn(model);
    }

    private ExtensionElement property(String name, String value) {
        ExtensionElement element = new ExtensionElement();
        element.setName("property");
        ExtensionAttribute nameAttr = new ExtensionAttribute();
        nameAttr.setName("name");
        nameAttr.setValue(name);
        element.addAttribute(nameAttr);
        ExtensionAttribute valueAttr = new ExtensionAttribute();
        valueAttr.setName("value");
        valueAttr.setValue(value);
        element.addAttribute(valueAttr);
        return element;
    }
}
