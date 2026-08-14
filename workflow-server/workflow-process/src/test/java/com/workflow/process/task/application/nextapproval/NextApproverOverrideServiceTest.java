package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.task.api.request.NextApproverSelectionRequest;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NextApproverOverrideServiceTest {

    private RuntimeService runtimeService;
    private NextApprovalRouteService routeService;
    private NextApproverCandidateService candidateService;
    private ProcessOperationLogMapper operationLogMapper;
    private NextApproverOverrideService service;
    private Task task;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        routeService = mock(NextApprovalRouteService.class);
        candidateService = mock(NextApproverCandidateService.class);
        operationLogMapper = mock(ProcessOperationLogMapper.class);
        service = new NextApproverOverrideService(
                runtimeService,
                routeService,
                candidateService,
                operationLogMapper,
                mock(SysUserService.class),
                new ObjectMapper());
        task = mock(Task.class);
        when(task.getId()).thenReturn("source-task");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
    }

    @Test
    void rejectsTamperedGroupScopeKeyWithStableConflictCode() {
        NextApprovalTarget target = target("manager-review", "DIRECT");
        stubReady(target);

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.validateAndStage(
                        task,
                        "approve",
                        "同意",
                        null,
                        "tampered",
                        List.of(selection(
                                "manager-review", "alice"))));

        assertEquals("NEXT_APPROVAL_SCOPE_CHANGED", error.getErrorCode());
        verify(runtimeService, never()).setVariable(
                eq("instance-1"), any(), any());
    }

    @Test
    void rejectsUserOutsideResolvedScopeAndDirectMultiSelection() {
        NextApprovalTarget target = target("manager-review", "DIRECT");
        stubReady(target);
        when(candidateService.resolveAllowed(
                any(), eq(target), eq(PersonResolveUsage.CANDIDATE)))
                .thenReturn(List.of(user("user-1", "alice")));

        BusinessConflictException outside = assertThrows(
                BusinessConflictException.class,
                () -> service.validateAndStage(
                        task,
                        "approve",
                        null,
                        null,
                        "scope-1",
                        List.of(selection(
                                "manager-review", "disabled-user"))));
        assertEquals("NEXT_APPROVER_OUT_OF_SCOPE", outside.getErrorCode());

        BusinessConflictException multiple = assertThrows(
                BusinessConflictException.class,
                () -> service.validateAndStage(
                        task,
                        "approve",
                        null,
                        null,
                        "scope-1",
                        List.of(selection(
                                "manager-review", "alice", "user-1"))));
        assertEquals(
                "NEXT_APPROVER_CARDINALITY_INVALID",
                multiple.getErrorCode());
    }

    @Test
    void rejectsDuplicateTargetSelection() {
        NextApprovalTarget target = target("manager-review", "CANDIDATE");
        stubReady(target);

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.validateAndStage(
                        task,
                        "approve",
                        null,
                        null,
                        "scope-1",
                        List.of(
                                selection("manager-review", "alice"),
                                selection("manager-review", "bob"))));

        assertEquals("NEXT_APPROVAL_TARGET_INVALID", error.getErrorCode());
    }

    @Test
    void rejectsForgedTargetNodeWithDedicatedConflictCode() {
        NextApprovalTarget target = target("manager-review", "DIRECT");
        stubReady(target);

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.validateAndStage(
                        task,
                        "approve",
                        null,
                        null,
                        "scope-1",
                        List.of(selection("forged-review", "alice"))));

        assertEquals("NEXT_APPROVAL_TARGET_INVALID", error.getErrorCode());
    }

    @Test
    void stagesDirectOverrideAndWritesRequiredAuditRecord() {
        NextApprovalTarget target = target("manager-review", "DIRECT");
        stubReady(target);
        when(candidateService.resolveAllowed(
                any(), eq(target), eq(PersonResolveUsage.CANDIDATE)))
                .thenReturn(List.of(user("user-1", "alice")));

        service.validateAndStage(
                task,
                "approve",
                "同意",
                "",
                "scope-1",
                List.of(selection("manager-review", "user-1")));

        verify(runtimeService).setVariable(
                eq("instance-1"),
                eq(NextApproverOverrideService.VARIABLE_NAME),
                any());
        ArgumentCaptor<ProcessOperationLog> audit =
                ArgumentCaptor.forClass(ProcessOperationLog.class);
        verify(operationLogMapper).insert(audit.capture());
        assertEquals(
                "NEXT_ASSIGNEE_OVERRIDE",
                audit.getValue().getOperationType());
        assertEquals("source-task", audit.getValue().getTaskId());
        org.junit.jupiter.api.Assertions.assertTrue(
                audit.getValue().getNewValue().contains("alice"));
    }

    @Test
    void stagesAssigneeWithCandidateGroupsAsDirectOverride() {
        UserTask userTask = configuredUserTask("manager-review");
        userTask.setAssignee("alice");
        userTask.setCandidateGroups(List.of("ROLE_MANAGER"));
        NextApprovalTarget target =
                new NextApproverSelectionPolicyReader(new ObjectMapper())
                        .read("definition-1", userTask);
        stubReady(target);
        when(candidateService.resolveAllowed(
                any(), eq(target), eq(PersonResolveUsage.CANDIDATE)))
                .thenReturn(List.of(user("user-2", "bob")));

        service.validateAndStage(
                task,
                "approve",
                "同意",
                "",
                "scope-1",
                List.of(selection("manager-review", "bob")));

        ArgumentCaptor<Object> staged = ArgumentCaptor.forClass(Object.class);
        verify(runtimeService).setVariable(
                eq("instance-1"),
                eq(NextApproverOverrideService.VARIABLE_NAME),
                staged.capture());
        Map<?, ?> overrides = (Map<?, ?>) staged.getValue();
        Map<?, ?> entry = (Map<?, ?>) overrides.get("manager-review");
        assertEquals("DIRECT", entry.get("assignmentMode"),
                "人工覆盖必须保留直接办理语义，不能降级为纯候选池");
        assertEquals(List.of("bob"), entry.get("usernames"));
    }

    @Test
    void writesMultiInstanceCollectionBeforeCurrentTaskCompletes() {
        NextApprovalTarget target = target(
                "joint-review", "MULTI_INSTANCE");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem(
                "${_wfMultiInstanceUsers_joint_review}");
        target.userTask().setLoopCharacteristics(loop);
        stubReady(target);
        when(candidateService.resolveAllowed(
                any(), eq(target), eq(PersonResolveUsage.CANDIDATE)))
                .thenReturn(List.of(
                        user("user-1", "alice"),
                        user("user-2", "bob")));

        service.validateAndStage(
                task,
                "approve",
                "同意",
                null,
                "scope-1",
                List.of(selection(
                        "joint-review", "user-1", "bob")));

        verify(runtimeService).setVariable(
                "instance-1",
                "_wfMultiInstanceUsers_joint_review",
                List.of("alice", "bob"));
        verify(runtimeService, never()).setVariable(
                eq("instance-1"),
                eq(NextApproverOverrideService.VARIABLE_NAME),
                any());
    }

    @Test
    void rejectsSelectedMultiInstanceCollectionSharedWithHiddenHitTarget() {
        NextApprovalTarget editable = target(
                "joint-review", "MULTI_INSTANCE");
        NextApprovalTarget hidden = target(
                "hidden-joint-review", "MULTI_INSTANCE", false, false);
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${sharedApprovers}");
        editable.userTask().setLoopCharacteristics(loop);
        MultiInstanceLoopCharacteristics hiddenLoop =
                new MultiInstanceLoopCharacteristics();
        hiddenLoop.setInputDataItem("${sharedApprovers}");
        hidden.userTask().setLoopCharacteristics(hiddenLoop);
        NextApprovalResolution resolution = new NextApprovalResolution(
                task,
                NextApprovalPreviewStatus.READY,
                null,
                "scope-1",
                List.of(editable, hidden),
                Map.of());
        when(routeService.resolve(eq(task), any(), eq(false)))
                .thenReturn(resolution);
        when(candidateService.defaultAssignees(any(), eq(editable)))
                .thenReturn(List.of(new NextApproverCandidateDTO(
                        "default-id", "default-user", "Default")));
        when(candidateService.resolveAllowed(
                any(), eq(editable), eq(PersonResolveUsage.CANDIDATE)))
                .thenReturn(List.of(user("user-1", "alice")));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.validateAndStage(
                        task,
                        "approve",
                        null,
                        null,
                        "scope-1",
                        List.of(selection("joint-review", "alice"))));

        assertEquals(
                "NEXT_APPROVER_RESOLUTION_FAILED",
                error.getErrorCode());
    }

    @Test
    void blocksVisibleReadOnlyNodeWithoutDefaultAssignee() {
        NextApprovalTarget target = target(
                "read-only-review", "DIRECT", false);
        stubReady(target);
        when(candidateService.defaultAssignees(any(), eq(target)))
                .thenReturn(List.of());

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.validateAndStage(
                        task,
                        "approve",
                        null,
                        null,
                        null,
                        List.of()));

        assertEquals(
                "NEXT_APPROVER_RESOLUTION_FAILED",
                error.getErrorCode());
    }

    @Test
    void deferredPreviewAllowsNormalCompletionWhenDefaultAssigneeExists() {
        NextApprovalTarget target = target(
                "manager-review", "DIRECT", true, true);
        stubReady(target);

        service.validateAndStage(
                task,
                "approve",
                "同意",
                null,
                null,
                List.of(),
                true);

        verify(runtimeService, never()).setVariable(
                eq("instance-1"), any(), any());
    }

    @Test
    void deferredPreviewRequiresStableDefaultForEditableActualTarget() {
        NextApprovalTarget target = target(
                "select-reviewer", "DIRECT", true, true);
        stubReady(target);
        when(candidateService.defaultAssignees(any(), eq(target)))
                .thenReturn(List.of());

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.validateAndStage(
                        task,
                        "approve",
                        "同意",
                        null,
                        null,
                        List.of(),
                        true));

        assertEquals(
                "NEXT_APPROVER_DEFERRED_DEFAULT_REQUIRED",
                error.getErrorCode());
        org.junit.jupiter.api.Assertions.assertTrue(
                error.getMessage().contains("请勿重复提交"));
    }

    @Test
    void deferredCompletionRequiresUserWhenEditableDefaultIsEmpty() {
        NextApprovalTarget target = target(
                "select-reviewer", "DIRECT", true, true);
        NextApprovalResolution resolution = new NextApprovalResolution(
                task,
                NextApprovalPreviewStatus.READY,
                null,
                "scope-1",
                List.of(target),
                Map.of());
        when(routeService.resolve(eq(task), any(), eq(true)))
                .thenReturn(resolution);
        when(candidateService.defaultAssignees(any(), eq(target)))
                .thenReturn(List.of());

        boolean required = service
                .requiresManualSelectionForDeferredCompletion(
                        task,
                        "approve",
                        "同意",
                        null,
                        Map.of("amount", 100));

        assertEquals(true, required);
    }

    private void stubReady(NextApprovalTarget target) {
        NextApprovalResolution resolution = new NextApprovalResolution(
                task,
                NextApprovalPreviewStatus.READY,
                null,
                "scope-1",
                List.of(target),
                Map.of());
        when(routeService.resolve(eq(task), any(), eq(false)))
                .thenReturn(resolution);
        when(candidateService.defaultAssignees(any(), eq(target)))
                .thenReturn(List.of(new NextApproverCandidateDTO(
                        "default-id", "default-user", "Default")));
    }

    private NextApprovalTarget target(String nodeId, String mode) {
        return target(nodeId, mode, true);
    }

    private NextApprovalTarget target(
            String nodeId,
            String mode,
            boolean editable) {
        return target(nodeId, mode, true, editable);
    }

    private NextApprovalTarget target(
            String nodeId,
            String mode,
            boolean visible,
            boolean editable) {
        UserTask userTask = new UserTask();
        userTask.setId(nodeId);
        userTask.setName(nodeId);
        NextApproverSelectionPolicy policy =
                new NextApproverSelectionPolicy(
                        true,
                        1,
                        visible,
                        editable,
                        mode,
                        !"DIRECT".equals(mode),
                        NextApproverSelectionPolicy.SourceType.SCOPE,
                        List.of(new NextApproverSelectionPolicy.Scope(
                                NextApproverSelectionPolicy.ScopeType.ALL_USERS,
                                List.of(),
                                false)),
                        null,
                        Map.of(),
                        "policy-" + nodeId);
        return new NextApprovalTarget(userTask, Map.of(), policy);
    }

    private NextApproverSelectionRequest selection(
            String nodeId,
            String... userKeys) {
        NextApproverSelectionRequest request =
                new NextApproverSelectionRequest();
        request.setNodeId(nodeId);
        request.setUserKeys(List.of(userKeys));
        return request;
    }

    private SysUser user(String id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        user.setStatus(SysUser.Status.ENABLED.getValue());
        user.setDeleted(0);
        return user;
    }

    private UserTask configuredUserTask(String nodeId) {
        UserTask userTask = new UserTask();
        userTask.setId(nodeId);
        userTask.setName(nodeId);
        ExtensionElement properties = extensionElement("properties");
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute("value", """
                {
                  "nextApproverSelection": {
                    "version": 1,
                    "visible": true,
                    "editable": true,
                    "source": {
                      "type": "SCOPE",
                      "rules": [{"type":"ALL_USERS","values":[]}]
                    }
                  }
                }
                """));
        properties.addChildElement(property);
        userTask.addExtensionElement(properties);
        return userTask;
    }

    private ExtensionElement extensionElement(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
