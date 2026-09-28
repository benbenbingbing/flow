package com.workflow.process.task.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.model.IdentityHandoverUser;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.sla.runtime.application.TaskSlaRuntimeService;
import com.workflow.process.task.api.request.TaskHandoverRequest;
import com.workflow.process.task.api.response.TaskHandoverTask;
import com.workflow.process.task.infrastructure.persistence.mapper.*;
import com.workflow.process.task.infrastructure.persistence.record.*;
import java.util.*;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 覆盖管理员交接的身份边界、跨页全量、加签和批量预检，不以普通转办替代交接。 */
class TaskHandoverServiceTest {
    private final IdentityDirectoryPort directory = mock(IdentityDirectoryPort.class);
    private final TaskHandoverMapper handover = mock(TaskHandoverMapper.class);
    private final ProcessTaskMapper tasks = mock(ProcessTaskMapper.class);
    private final ProcessTaskAddSignMapper parents = mock(ProcessTaskAddSignMapper.class);
    private final ProcessTaskAddSignUserMapper children = mock(ProcessTaskAddSignUserMapper.class);
    private final TaskInboxProjectionMapper projection = mock(TaskInboxProjectionMapper.class);
    private final TaskInboxProjectionService inbox = mock(TaskInboxProjectionService.class);
    private final TaskService engine = mock(TaskService.class);
    private final ProcessTaskService processTasks = mock(ProcessTaskService.class);
    private final TaskSlaRuntimeService sla = mock(TaskSlaRuntimeService.class);
    private final ProcessOperationLogMapper logs = mock(ProcessOperationLogMapper.class);
    private final TaskHandoverService service = new TaskHandoverService(directory, handover, tasks,
            parents, children, projection, inbox, engine, processTasks, sla, logs);

    @BeforeEach void setup() {
        UserContext.setCurrentUser("admin-id", "admin");
        source("1", true);
        when(directory.lockHandoverUser("target-id")).thenReturn(Optional.of(user("target", "0", false)));
        when(directory.getDisplayName("admin-id")).thenReturn("管理员");
        when(projection.lockEngineTask(anyString())).thenAnswer(call -> call.getArgument(0));
        when(projection.updateIdentity(anyLong(), anyString(), anyString(), eq("user"))).thenReturn(1);
        when(logs.insert(any(ProcessOperationLog.class))).thenReturn(1);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void disabledDeletedSourceCanTransferAndDoesNotCompleteOrAcknowledge() {
        ordinary("a", "source", "todo");
        assertEquals(1, service.transfer(request(List.of("a"), false)));
        verify(engine).setAssignee("a", "target");
        verify(inbox).synchronizeTask("a");
        verify(sla).updateAssignee("a", "target");
        verify(sla, never()).acknowledgeIfConfigured(anyString());
        verify(engine, never()).complete(anyString());
        verify(processTasks).refreshAssignmentSummary("instance");
        var log = ArgumentCaptor.forClass(ProcessOperationLog.class);
        verify(logs).insert(log.capture());
        assertEquals("admin-id", log.getValue().getOperatorId());
        assertEquals("TRANSFER", log.getValue().getOperationType());
        assertEquals("来源(source)", log.getValue().getOldValue());
        assertEquals("接收(target)", log.getValue().getNewValue());
        assertEquals("人员交接：人员调整", log.getValue().getOperationComment());
    }

    @Test void normalSourceAndSharedCandidateAreAlsoSupported() {
        source("0", false);
        ordinary("candidate", null, "todo");
        assertEquals(1, service.transfer(request(List.of("candidate"), false)));
        verify(engine).setAssignee("candidate", "target");
    }

    @Test void targetIsRecheckedUnderLockAndAllAbnormalStatesAreRejected() {
        for (var target : List.of(user("target", "1", false), user("target", "0", true), user("target", null, false))) {
            when(directory.lockHandoverUser("target-id")).thenReturn(Optional.of(target));
            assertThrows(BusinessConflictException.class, () -> service.transfer(request(List.of("a"), false)));
        }
        verifyNoInteractions(engine, tasks, logs);
    }

    @Test void rejectsSamePersonMissingReasonAndEmptyExplicitSelection() {
        assertThrows(IllegalArgumentException.class, () -> service.transfer(
                new TaskHandoverRequest("source-id", "source-id", List.of("a"), false, "原因")));
        assertThrows(IllegalArgumentException.class, () -> service.transfer(
                new TaskHandoverRequest("source-id", "target-id", List.of("a"), false, " ")));
        assertThrows(IllegalArgumentException.class, () -> service.transfer(request(List.of(), false)));
        verifyNoInteractions(engine, tasks, logs);
    }

    @Test void allSelectionUsesFullServerResultAndDeduplicatesTasks() {
        ordinary("a", "source-id", "todo"); ordinary("z", "source", "todo");
        when(handover.selectAllTaskIds("source-id")).thenReturn(List.of("z", "a", "a"));
        assertEquals(2, service.transfer(request(List.of("ignored-page-id"), true)));
        verify(engine).setAssignee("a", "target"); verify(engine).setAssignee("z", "target");
        verify(processTasks, times(1)).refreshAssignmentSummary("instance");
    }

    @Test void staleOrInjectedTaskRejectsEntireBatchBeforeAnyMutation() {
        ordinary("a", "source", "todo"); ordinary("z", "source", "todo");
        when(handover.findEligible("source-id", "z")).thenReturn(null);
        assertThrows(BusinessConflictException.class, () -> service.transfer(request(List.of("a", "z"), false)));
        verify(engine, never()).setAssignee(anyString(), anyString());
        verifyNoInteractions(sla, logs, inbox);
    }

    @Test void claimedByAnotherUserIsRejectedEvenIfMirrorIsStale() {
        ordinary("a", "someone-else", "todo");
        assertThrows(BusinessConflictException.class, () -> service.transfer(request(List.of("a"), false)));
        verify(engine, never()).setAssignee(anyString(), anyString());
    }

    @Test void localAddSignMovesBothIdentitiesAndPreservesHeldState() {
        var parent = parent("parent", "origin", false);
        var mirror = local("addsign-a", "ADD_SIGN", "hold");
        var child = child("addsign-a", parent, "HOLD");
        assertEquals(1, service.transfer(request(List.of("addsign-a"), false)));
        assertEquals("target", child.getUserId());
        assertEquals("HOLD", child.getStatus());
        assertEquals("hold", mirror.getStatus());
        verify(children).updateById(child);
        verify(projection).updateIdentity(mirror.getId(), "target", "接收(target)", "user");
        verify(engine, never()).setAssignee(anyString(), anyString());
        var order = inOrder(parents, projection, children, tasks);
        order.verify(parents).selectByIdForUpdate("parent");
        order.verify(projection).lockEngineTask("origin");
        order.verify(children).findByGeneratedTaskIdForUpdate("addsign-a");
        order.verify(tasks).selectByTaskIdForUpdate("addsign-a");
    }

    @Test void waitingSourceKeepsAlreadySubmittedApprovalAttribution() {
        ordinary("origin", "source", "waiting");
        var parent = parent("parent", "origin", true);
        parent.setSourceAction("approve"); parent.setSourceFormData("{\"amount\":10}");
        when(parents.findOpenBySourceTaskId("origin")).thenReturn(parent);
        service.transfer(request(List.of("origin"), false));
        assertEquals("source", parent.getOperatorId());
        assertEquals("approve", parent.getSourceAction());
        assertEquals("{\"amount\":10}", parent.getSourceFormData());
        verify(parents, never()).updateById(any(ProcessTaskAddSign.class));
    }

    @Test void unsubmittedSourceTransfersFutureAddSignResponsibility() {
        ordinary("origin", "source", "todo");
        var parent = parent("parent", "origin", false);
        when(parents.findOpenBySourceTaskId("origin")).thenReturn(parent);
        when(parents.updateById(parent)).thenReturn(1);
        service.transfer(request(List.of("origin"), false));
        assertEquals("target", parent.getOperatorId());
        assertFalse(parent.getSourceCompleted());
    }

    @Test void auditFailureIsNotSwallowed() {
        ordinary("a", "source", "todo");
        when(logs.insert(any(ProcessOperationLog.class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.transfer(request(List.of("a"), false)));
    }

    private void source(String status, boolean deleted) {
        when(directory.findHandoverUser("source-id")).thenReturn(Optional.of(user("source", status, deleted)));
    }

    private IdentityHandoverUser user(String name, String status, boolean deleted) {
        return new IdentityHandoverUser(name + "-id", name, "source".equals(name) ? "来源" : "接收", status, deleted);
    }

    private TaskHandoverRequest request(List<String> ids, boolean all) {
        return new TaskHandoverRequest("source-id", "target-id", ids, all, "人员调整");
    }

    private ProcessTask local(String id, String type, String status) {
        var task = new ProcessTask(); task.setId((long) id.hashCode()); task.setTaskId(id);
        task.setNodeType(type); task.setStatus(status); task.setProcessInstanceId("instance");
        when(tasks.selectByTaskId(id)).thenReturn(task); when(tasks.selectByTaskIdForUpdate(id)).thenReturn(task);
        when(handover.findEligible("source-id", id)).thenReturn(new TaskHandoverTask());
        return task;
    }

    private void ordinary(String id, String assignee, String status) {
        local(id, "USER_TASK", status);
        var query = mock(TaskQuery.class); var task = mock(Task.class);
        // createTaskQuery 的查询对象可重用，但 taskId 必须定位到对应任务，避免测试把全部任务当同一条。
        var root = engine.createTaskQuery();
        if (root == null) { root = mock(TaskQuery.class); when(engine.createTaskQuery()).thenReturn(root); }
        when(root.taskId(id)).thenReturn(query); when(query.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn(id); when(task.getAssignee()).thenReturn(assignee);
    }

    private ProcessTaskAddSign parent(String id, String sourceTaskId, boolean submitted) {
        var parent = new ProcessTaskAddSign(); parent.setId(id); parent.setSourceTaskId(sourceTaskId);
        parent.setStatus("ACTIVE"); parent.setSourceCompleted(submitted); parent.setOperatorId("source");
        when(parents.selectByIdForUpdate(id)).thenReturn(parent);
        return parent;
    }

    private ProcessTaskAddSignUser child(String taskId, ProcessTaskAddSign parent, String status) {
        var child = new ProcessTaskAddSignUser(); child.setId("child"); child.setGeneratedTaskId(taskId);
        child.setAddSignId(parent.getId()); child.setStatus(status); child.setUserId("source");
        when(children.findByGeneratedTaskId(taskId)).thenReturn(child);
        when(children.findByGeneratedTaskIdForUpdate(taskId)).thenReturn(child);
        when(children.updateById(child)).thenReturn(1);
        return child;
    }
}
