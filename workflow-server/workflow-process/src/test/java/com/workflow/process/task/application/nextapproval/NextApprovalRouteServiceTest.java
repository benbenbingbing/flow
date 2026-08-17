package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.form.application.FormSubmissionPreviewDeferredException;
import com.workflow.process.form.application.NodeFormSubmissionService;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NextApprovalRouteServiceTest {

    private RuntimeService runtimeService;
    private RepositoryService repositoryService;
    private FlowableConditionEvaluator conditionEvaluator;
    private NextApproverSelectionPolicyReader policyReader;
    private NodeFormSubmissionService nodeFormSubmissionService;
    private NextApprovalRouteService service;
    private Task task;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        repositoryService = mock(RepositoryService.class);
        conditionEvaluator = mock(FlowableConditionEvaluator.class);
        policyReader = mock(NextApproverSelectionPolicyReader.class);
        nodeFormSubmissionService = mock(NodeFormSubmissionService.class);
        service = new NextApprovalRouteService(
                mock(TaskService.class),
                runtimeService,
                repositoryService,
                conditionEvaluator,
                policyReader,
                nodeFormSubmissionService,
                new ObjectMapper());
        task = mock(Task.class);
        when(task.getProcessDefinitionId()).thenReturn("definition-7");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getTaskDefinitionKey()).thenReturn("current-review");
        when(runtimeService.getVariables("instance-1")).thenReturn(Map.of(
                "amount", 100,
                "entityData", Map.of("amount", 100)));
        when(nodeFormSubmissionService.projectEditableData(
                any(Task.class), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(policyReader.read(
                anyString(), any(UserTask.class), any(BpmnModel.class)))
                .thenAnswer(invocation -> target(invocation.getArgument(1)));
    }

    @Test
    void returnsDirectVisibleUserTask() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        UserTask next = node(new UserTask(), "manager-review");
        add(process, current, next);
        connect(process, current, next, "to-manager", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(
                List.of("manager-review"),
                resolution.targets().stream()
                        .map(target -> target.userTask().getId())
                        .toList());
        assertNotNull(resolution.scopeKey());
    }

    @Test
    void unsafeBeforeSubmitMakesPreviewDeferredBeforeRouteEvaluation() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        UserTask next = node(new UserTask(), "manager-review");
        add(process, current, next);
        connect(process, current, next, "to-manager", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(nodeFormSubmissionService.projectEditableData(
                any(Task.class), anyMap()))
                .thenThrow(new FormSubmissionPreviewDeferredException(
                        "普通 BEFORE_SUBMIT 需在正式提交后执行"));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(
                NextApprovalPreviewStatus.DEFERRED,
                resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
        assertEquals(
                "普通 BEFORE_SUBMIT 需在正式提交后执行",
                resolution.message());
        verifyNoInteractions(conditionEvaluator);
    }

    @Test
    void safeBeforeSubmitDerivedFieldParticipatesInRouteSelection() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        ExclusiveGateway gateway = node(
                new ExclusiveGateway(), "route-by-derived-field");
        UserTask high = node(new UserTask(), "high-review");
        UserTask normal = node(new UserTask(), "normal-review");
        gateway.setDefaultFlow("to-normal");
        add(process, current, gateway, high, normal);
        connect(process, current, gateway, "to-route", null);
        connect(
                process,
                gateway,
                high,
                "to-high",
                "${routeBucket == 'HIGH'}");
        connect(process, gateway, normal, "to-normal", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(nodeFormSubmissionService.projectEditableData(
                any(Task.class), anyMap()))
                .thenReturn(Map.of("routeBucket", "HIGH"));
        when(conditionEvaluator.evaluate(
                anyString(),
                org.mockito.ArgumentMatchers.argThat(values ->
                        "HIGH".equals(values.get("routeBucket")))))
                .thenReturn(true);

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(
                List.of("high-review"),
                resolution.targets().stream()
                        .map(target -> target.userTask().getId())
                        .toList());
    }

    @Test
    void exclusiveGatewayUsesDefaultFlowWhenNoConditionMatches() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        ExclusiveGateway gateway = node(
                new ExclusiveGateway(), "exclusive-decision");
        UserTask conditional = node(
                new UserTask(), "conditional-review");
        UserTask fallback = node(new UserTask(), "default-review");
        add(process, current, gateway, conditional, fallback);
        connect(process, current, gateway, "to-decision", null);
        connect(
                process,
                gateway,
                conditional,
                "conditional-flow",
                "${amount > 10000}");
        connect(process, gateway, fallback, "default-flow", null);
        gateway.setDefaultFlow("default-flow");
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(conditionEvaluator.evaluate(anyString(), anyMap()))
                .thenReturn(false);

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(
                List.of("default-review"),
                resolution.targets().stream()
                        .map(target -> target.userTask().getId())
                        .toList());
    }

    @Test
    void inclusiveGatewayUsesDefaultFlowWhenNoConditionMatches() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        InclusiveGateway gateway = node(
                new InclusiveGateway(), "inclusive-decision");
        UserTask conditional = node(
                new UserTask(), "conditional-review");
        UserTask fallback = node(new UserTask(), "default-review");
        add(process, current, gateway, conditional, fallback);
        connect(process, current, gateway, "to-decision", null);
        connect(
                process,
                gateway,
                conditional,
                "conditional-flow",
                "${amount > 10000}");
        connect(process, gateway, fallback, "default-flow", null);
        gateway.setDefaultFlow("default-flow");
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(conditionEvaluator.evaluate(anyString(), anyMap()))
                .thenReturn(false);

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(
                List.of("default-review"),
                resolution.targets().stream()
                        .map(target -> target.userTask().getId())
                        .toList());
    }

    @Test
    void endEventReturnsReadyWithoutTargets() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        EndEvent end = node(new EndEvent(), "end");
        add(process, current, end);
        connect(process, current, end, "to-end", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
    }

    @Test
    void cycleReturnsDeferred() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        ExclusiveGateway gateway = node(
                new ExclusiveGateway(), "loop-decision");
        UserTask next = node(new UserTask(), "next-review");
        add(process, current, gateway, next);
        connect(process, current, gateway, "to-loop", null);
        connect(process, gateway, current, "repeat", "${repeat}");
        connect(process, gateway, next, "leave-loop", null);
        gateway.setDefaultFlow("leave-loop");
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(conditionEvaluator.evaluate(anyString(), anyMap()))
                .thenReturn(true);

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.DEFERRED,
                resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
    }

    @Test
    void convergingGatewayReturnsDeferred() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        ServiceTask otherBranch = node(
                new ServiceTask(), "other-branch");
        ParallelGateway join = node(new ParallelGateway(), "parallel-join");
        UserTask next = node(new UserTask(), "next-review");
        add(process, current, otherBranch, join, next);
        connect(process, current, join, "current-to-join", null);
        connect(process, otherBranch, join, "other-to-join", null);
        connect(process, join, next, "join-to-next", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.DEFERRED,
                resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
    }

    @Test
    void gatewayWithoutMatchOrDefaultReturnsBlocked() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        ExclusiveGateway gateway = node(
                new ExclusiveGateway(), "exclusive-decision");
        UserTask next = node(new UserTask(), "conditional-review");
        add(process, current, gateway, next);
        connect(process, current, gateway, "to-decision", null);
        connect(
                process,
                gateway,
                next,
                "conditional-flow",
                "${amount > 10000}");
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(conditionEvaluator.evaluate(anyString(), anyMap()))
                .thenReturn(false);

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.BLOCKED,
                resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
    }

    @Test
    void predictsExclusiveConditionFromActionAndProjectedEditableData() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        ExclusiveGateway gateway = node(
                new ExclusiveGateway(), "approval-result");
        UserTask highValueReview = node(
                new UserTask(), "high-value-review");
        EndEvent end = node(new EndEvent(), "end");
        add(process, current, gateway, highValueReview, end);
        connect(process, current, gateway, "to-gateway", null);
        connect(
                process,
                gateway,
                highValueReview,
                "to-high-value",
                "${approved == 'approve' && amount >= 1000}");
        connect(process, gateway, end, "default-end", null);
        gateway.setDefaultFlow("default-end");
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(conditionEvaluator.evaluate(anyString(), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> variables = invocation.getArgument(1);
                    return "approve".equals(variables.get("approved"))
                            && Integer.valueOf(2000).equals(
                                    variables.get("amount"));
                });

        NextApprovalResolution approved = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, approved.status());
        assertEquals(
                List.of("high-value-review"),
                approved.targets().stream()
                        .map(target -> target.userTask().getId())
                        .toList());
        assertEquals("approve", approved.variables().get("approved"));
        assertEquals(2000, approved.variables().get("amount"));
        assertEquals(
                2000,
                ((Map<?, ?>) approved.variables().get("entityData"))
                        .get("amount"));
        assertNotNull(approved.scopeKey());

        when(runtimeService.getVariables("instance-1")).thenReturn(Map.of(
                "amount", 2000,
                "entityData", Map.of("amount", 2000)));
        NextApprovalResolution submitRecheck = service.resolve(
                task, request("approve", 9999), false);
        assertEquals(
                approved.targets().stream()
                        .map(target -> target.userTask().getId())
                        .toList(),
                submitRecheck.targets().stream()
                        .map(target -> target.userTask().getId())
                        .toList(),
                "提交重验应使用已落入引擎的表单变量，不能再次信任请求原值");
        assertEquals(approved.scopeKey(), submitRecheck.scopeKey());

        NextApprovalResolution rejected = service.resolve(
                task, request("reject", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, rejected.status());
        assertEquals(List.of(), rejected.targets(),
                "默认流到结束事件时应返回 READY 空节点，而不是猜测审批人");
    }

    @Test
    void preservesEveryUserTaskReachedByParallelSplit() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        ParallelGateway split = node(new ParallelGateway(), "parallel-split");
        UserTask finance = node(new UserTask(), "finance-review");
        UserTask security = node(new UserTask(), "security-review");
        add(process, current, split, finance, security);
        connect(process, current, split, "to-split", null);
        connect(process, split, finance, "to-finance", null);
        connect(process, split, security, "to-security", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(
                List.of("finance-review", "security-review"),
                resolution.targets().stream()
                        .map(target -> target.userTask().getId())
                        .toList());
        assertFalse(resolution.scopeKey().isBlank());
    }

    @Test
    void preservesEveryUserTaskWhoseInclusiveConditionMatches() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        InclusiveGateway split = node(
                new InclusiveGateway(), "conditional-split");
        UserTask finance = node(new UserTask(), "finance-review");
        UserTask security = node(new UserTask(), "security-review");
        add(process, current, split, finance, security);
        connect(process, current, split, "to-split", null);
        connect(process, split, finance, "to-finance", "${amount >= 1000}");
        connect(process, split, security, "to-security", "${approved == 'approve'}");
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(conditionEvaluator.evaluate(anyString(), anyMap()))
                .thenReturn(true);

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(
                List.of("finance-review", "security-review"),
                resolution.targets().stream()
                        .map(target -> target.userTask().getId())
                        .toList(),
                "包容网关同时命中的下一人工节点必须全部返回");
    }

    @Test
    void defersWhenAnAutomaticNodePreventsSafePrediction() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        ServiceTask automatic = node(
                new ServiceTask(), "calculate-assignee");
        UserTask next = node(new UserTask(), "next-review");
        add(process, current, automatic, next);
        connect(process, current, automatic, "to-service", null);
        connect(process, automatic, next, "to-next", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.DEFERRED, resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
    }

    @Test
    void legacyDeploymentWithoutVisiblePolicySkipsUnsafeRoutePrediction() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        ExclusiveGateway gateway = node(
                new ExclusiveGateway(), "legacy-gateway");
        UserTask next = node(new UserTask(), "legacy-next");
        add(process, current, gateway, next);
        connect(process, current, gateway, "to-gateway", null);
        connect(
                process,
                gateway,
                next,
                "legacy-condition",
                "${legacySpringBean.callExternalService()}");
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(policyReader.read(
                anyString(), any(UserTask.class), any(BpmnModel.class)))
                .thenAnswer(invocation -> {
                    UserTask userTask = invocation.getArgument(1);
                    return new NextApprovalTarget(
                            userTask,
                            Map.of(),
                            NextApproverSelectionPolicy.absent());
                });

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
        verifyNoInteractions(conditionEvaluator);
    }

    @Test
    void currentNodesOwnVisiblePolicyDoesNotBlockTerminalCompletion() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        EndEvent end = node(new EndEvent(), "end");
        add(process, current, end);
        connect(process, current, end, "to-end", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(policyReader.read(
                anyString(), any(UserTask.class), any(BpmnModel.class)))
                .thenAnswer(invocation ->
                        target(invocation.getArgument(1)));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
        verifyNoInteractions(conditionEvaluator);
    }

    @Test
    void disconnectedVisibleTaskDoesNotOptCurrentNodeIntoPrediction() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        EndEvent end = node(new EndEvent(), "end");
        UserTask unrelated = node(new UserTask(), "unrelated-review");
        add(process, current, end, unrelated);
        connect(process, current, end, "to-end", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(List.of(), resolution.targets());
        verifyNoInteractions(conditionEvaluator);
    }

    @Test
    void defersWhenNextUserTaskMayBeAutomaticallySkipped() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        UserTask next = node(new UserTask(), "auto-skip-review");
        next.setSkipExpression("${skipNodeEnabled}");
        add(process, current, next);
        connect(process, current, next, "to-next", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.DEFERRED,
                resolution.status());
        assertEquals(List.of(), resolution.targets());
    }

    @Test
    void nullOrEmptyCommentNeverReusesPreviousProcessComment() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        UserTask next = node(new UserTask(), "next-review");
        add(process, current, next);
        connect(process, current, next, "to-next", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(runtimeService.getVariables("instance-1"))
                .thenReturn(Map.of("comment", "上一节点意见"));

        NextApprovalResolution nullRequest = service.resolve(
                task, null, true);
        NextApprovalPreviewRequest emptyComment =
                request("approve", 2000);
        emptyComment.setComment("");
        NextApprovalResolution explicitEmpty = service.resolve(
                task, emptyComment, true);

        assertEquals("", nullRequest.variables().get("comment"));
        assertEquals("", explicitEmpty.variables().get("comment"));
    }

    @Test
    void currentMultiInstanceTaskDefersUntilAllInstancesConverge() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        current.setLoopCharacteristics(loop);
        UserTask next = node(new UserTask(), "next-review");
        add(process, current, next);
        connect(process, current, next, "to-next", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.DEFERRED,
                resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
    }

    @Test
    void currentAsyncLeaveTaskDefersOnlyAfterVisibleFeatureOptIn() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        current.setAsynchronousLeave(true);
        UserTask next = node(new UserTask(), "next-review");
        add(process, current, next);
        connect(process, current, next, "to-next", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.DEFERRED,
                resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
    }

    @Test
    void legacyMultiInstanceWithoutVisibleDownstreamPolicyStaysHidden() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        current.setLoopCharacteristics(loop);
        UserTask next = node(new UserTask(), "next-review");
        add(process, current, next);
        connect(process, current, next, "to-next", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));
        when(policyReader.read(
                anyString(), any(UserTask.class), any(BpmnModel.class)))
                .thenAnswer(invocation -> new NextApprovalTarget(
                        invocation.getArgument(1),
                        Map.of(),
                        NextApproverSelectionPolicy.absent()));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.READY, resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
    }

    @Test
    void visibleDynamicAssigneeDefersInsteadOfBlockingDefaultResolution() {
        org.flowable.bpmn.model.Process process = process();
        UserTask current = node(new UserTask(), "current-review");
        UserTask next = node(new UserTask(), "dynamic-review");
        next.setAssignee("${managerUsername}");
        add(process, current, next);
        connect(process, current, next, "to-next", null);
        when(repositoryService.getBpmnModel("definition-7"))
                .thenReturn(model(process));

        NextApprovalResolution resolution = service.resolve(
                task, request("approve", 2000), true);

        assertEquals(NextApprovalPreviewStatus.DEFERRED,
                resolution.status());
        assertEquals(List.of(), resolution.targets());
        assertEquals(null, resolution.scopeKey());
    }

    private NextApprovalPreviewRequest request(String action, int amount) {
        NextApprovalPreviewRequest request = new NextApprovalPreviewRequest();
        request.setAction(action);
        request.setActionLabel(action);
        request.setFormData(Map.of("amount", amount));
        return request;
    }

    private NextApprovalTarget target(UserTask userTask) {
        NextApproverSelectionPolicy policy =
                new NextApproverSelectionPolicy(
                        true,
                        1,
                        true,
                        true,
                        "DIRECT",
                        false,
                        NextApproverSelectionPolicy.SourceType.SCOPE,
                        List.of(new NextApproverSelectionPolicy.Scope(
                                NextApproverSelectionPolicy.ScopeType.ALL_USERS,
                                List.of(),
                                false)),
                        null,
                        Map.of(),
                        "policy-" + userTask.getId());
        return new NextApprovalTarget(userTask, Map.of(), policy);
    }

    private BpmnModel model(org.flowable.bpmn.model.Process process) {
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private org.flowable.bpmn.model.Process process() {
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.setId("process-1");
        return process;
    }

    private void add(
            org.flowable.bpmn.model.Process process,
            org.flowable.bpmn.model.FlowElement... elements) {
        for (org.flowable.bpmn.model.FlowElement element : elements) {
            process.addFlowElement(element);
        }
    }

    private <T extends FlowNode> T node(T node, String id) {
        node.setId(id);
        node.setName(id);
        return node;
    }

    private void connect(
            org.flowable.bpmn.model.Process process,
            FlowNode source,
            FlowNode target,
            String id,
            String condition) {
        SequenceFlow flow = new SequenceFlow(source.getId(), target.getId());
        flow.setId(id);
        flow.setConditionExpression(condition);
        flow.setSourceFlowElement(source);
        flow.setTargetFlowElement(target);
        List<SequenceFlow> outgoing = source.getOutgoingFlows() == null
                ? new ArrayList<>()
                : new ArrayList<>(source.getOutgoingFlows());
        outgoing.add(flow);
        source.setOutgoingFlows(outgoing);
        List<SequenceFlow> incoming = target.getIncomingFlows() == null
                ? new ArrayList<>()
                : new ArrayList<>(target.getIncomingFlows());
        incoming.add(flow);
        target.setIncomingFlows(incoming);
        process.addFlowElement(flow);
    }
}
