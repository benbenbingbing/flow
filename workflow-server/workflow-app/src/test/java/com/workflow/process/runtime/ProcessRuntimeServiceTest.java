package com.workflow.process.runtime;

import com.workflow.process.instance.application.ProcessRuntimeService;
import com.workflow.core.database.JdbcLockedRow;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;

import com.workflow.contracts.process.ProcessStartRequest;
import com.workflow.contracts.process.ProcessStartResult;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.instance.application.WorkflowReservedVariables;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessRuntimeServiceTest {
        @Test
        void transitionModeDoesNotImplicitlySetBusinessStatusWhenStarting() {
                Fixture fixture = new Fixture();
                ProcessRuntimeService service = fixture.service();
                var policy = mock(com.workflow.process.status.application.ProcessEntityStatusPolicy.class);
                when(policy.usesTransitions("definition-v3")).thenReturn(true);
                org.springframework.test.util.ReflectionTestUtils.setField(service, "statusPolicy", policy);
                var result = service.start(new ProcessStartRequest("process-config-1", "expense", "data-1",
                        "EXP-1", "admin", "管理员", "PENDING", Map.of(), Map.of()));
                org.junit.jupiter.api.Assertions.assertNull(result.entityStatus());
                assertEquals("RUNNING", result.processStatus());
        }


        @Test
        void startsFlowableAndReturnsRuntimeFields() {
                Fixture fixture = new Fixture();
                ProcessStartRequest request = new ProcessStartRequest(
                                "process-config-1",
                                "expense",
                                "data-1",
                                "EXP-1",
                                "admin",
                                "管理员",
                                "PENDING",
                                Map.of("amount", 100, "code", "forged-form-code"),
                                Map.of(
                                        "code", "forged-variable-code",
                                        "_FLOWABLE_SKIP_EXPRESSION_ENABLED", false,
                                        "_ACTIVITI_SKIP_EXPRESSION_ENABLED", false,
                                        "skipNodeEnabled", false));

                ProcessStartResult result = fixture.service().start(request);

                verify(fixture.processDefinitionConfigMapper).selectById("process-config-1");
                @SuppressWarnings("unchecked")
                ArgumentCaptor<Map<String, Object>> variableCaptor = ArgumentCaptor.forClass(Map.class);
                verify(fixture.runtimeService).startProcessInstanceById(
                                eq("definition-v3"),
                                eq("data-1"),
                                variableCaptor.capture());
                assertEquals("expense", variableCaptor.getValue().get("entityCode"));
                assertEquals("admin", variableCaptor.getValue().get("initiator"));
                assertEquals(100, variableCaptor.getValue().get("amount"));
                assertEquals("EXP-1", variableCaptor.getValue().get("code"));
                assertFalse(variableCaptor.getValue().containsKey("dataNo"));
                assertEquals(true, variableCaptor.getValue().get(
                                WorkflowReservedVariables
                                                .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE));
                assertEquals(true, variableCaptor.getValue().get(
                                WorkflowReservedVariables
                                                .LEGACY_SKIP_NODE_ENABLED_VARIABLE));
                assertFalse(variableCaptor.getValue().containsKey(
                                WorkflowReservedVariables
                                                .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE));
                verify(fixture.identityService).setAuthenticatedUserId("admin");
                verify(fixture.multiInstanceCollectionListener)
                                .prepareVariables(eq("definition-v3"), anyMap());
                verify(fixture.processTaskService).syncTasksFromFlowable("pi-1");
                assertEquals("pi-1", result.processInstanceId());
                assertEquals("PENDING", result.entityStatus());
                assertEquals("task-1", result.currentTaskId());
        }

        @Test
        void unsafeHistoricalSkipExpressionDoesNotReceiveEnableSwitch() {
                Fixture fixture = new Fixture();
                UserTask unsafeTask = new UserTask();
                unsafeTask.setId("unsafe-review");
                unsafeTask.setSkipExpression(
                                "${dangerousService.execute()}");
                org.flowable.bpmn.model.Process process =
                                new org.flowable.bpmn.model.Process();
                process.addFlowElement(unsafeTask);
                BpmnModel model = new BpmnModel();
                model.addProcess(process);
                when(fixture.repositoryService.getBpmnModel(
                                "definition-v3")).thenReturn(model);
                ProcessStartRequest request = new ProcessStartRequest(
                                "process-config-1", "expense", "data-1",
                                "EXP-1", "admin", "管理员", "PENDING",
                                Map.of(), Map.of());

                fixture.service().start(request);

                @SuppressWarnings("unchecked")
                ArgumentCaptor<Map<String, Object>> variables =
                                ArgumentCaptor.forClass(Map.class);
                verify(fixture.runtimeService).startProcessInstanceById(
                                eq("definition-v3"), eq("data-1"),
                                variables.capture());
                assertFalse(variables.getValue().containsKey(
                                WorkflowReservedVariables
                                                .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE));
                assertFalse(variables.getValue().containsKey(
                                WorkflowReservedVariables
                                                .LEGACY_SKIP_NODE_ENABLED_VARIABLE));
        }

        private static class Fixture {
                final ProcessDefinitionConfigMapper processDefinitionConfigMapper = mock(
                                ProcessDefinitionConfigMapper.class);
                final RepositoryService repositoryService = mock(RepositoryService.class);
                final RuntimeService runtimeService = mock(RuntimeService.class);
                final IdentityService identityService = mock(IdentityService.class);
                final org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
                final EntityProcessLinkMapper entityProcessLinkMapper = mock(EntityProcessLinkMapper.class);
                final JdbcLockedRow lockedRows = mock(JdbcLockedRow.class);
                final ProcessTaskService processTaskService = mock(ProcessTaskService.class);
                final MultiInstanceCollectionListener multiInstanceCollectionListener = mock(
                                MultiInstanceCollectionListener.class);
                Fixture() {
                        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
                        config.setId("process-config-1");
                        config.setProcessKey("expense_flow");
                        config.setProcessName("费用审批");
                        config.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
                        when(processDefinitionConfigMapper.selectById("process-config-1")).thenReturn(config);

                        ProcessDefinitionQuery definitionQuery = mock(
                                        ProcessDefinitionQuery.class,
                                        org.mockito.Mockito.RETURNS_SELF);
                        ProcessDefinition deployed = mock(ProcessDefinition.class);
                        when(repositoryService.createProcessDefinitionQuery())
                                        .thenReturn(definitionQuery);
                        when(definitionQuery.singleResult()).thenReturn(deployed);
                        when(deployed.getId()).thenReturn("definition-v3");
                        BpmnModel safeModel = new BpmnModel();
                        safeModel.addProcess(
                                        new org.flowable.bpmn.model.Process());
                        when(repositoryService.getBpmnModel("definition-v3"))
                                        .thenReturn(safeModel);

                        ProcessInstance processInstance = mock(ProcessInstance.class);
                        when(processInstance.getId()).thenReturn("pi-1");
                        when(runtimeService.startProcessInstanceById(eq("definition-v3"), eq("data-1"), anyMap()))
                                        .thenReturn(processInstance);

                        EntityProcessLink processLink = new EntityProcessLink();
                        processLink.setId("link-1");
                        processLink.setRequestId("request-1");
                        processLink.setState("PENDING");
                        processLink.setGeneration(1);
                        org.mockito.Mockito.doAnswer(invocation -> {
                                Map<String, Object> values = invocation.getArgument(1);
                                processLink.setId((String) values.get("id"));
                                processLink.setRequestId((String) values.get("request_id"));
                                return null;
                        }).when(lockedRows).ensureAndLock(eq("entity_process_link"), anyMap(), any());
                        when(entityProcessLinkMapper.selectForUpdate("expense", "data-1", 1))
                                        .thenReturn(processLink);
                        when(entityProcessLinkMapper.activate(anyString(), anyString(), eq("pi-1")))
                                        .thenReturn(1);

                        Task task = mock(Task.class);
                        when(task.getId()).thenReturn("task-1");
                        when(task.getName()).thenReturn("费用审批");
                        when(task.getAssignee()).thenReturn("admin");
                        TaskQuery taskQuery = mock(TaskQuery.class);
                        when(taskService.createTaskQuery()).thenReturn(taskQuery);
                        when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
                        when(taskQuery.active()).thenReturn(taskQuery);
                        when(taskQuery.listPage(0, 1)).thenReturn(java.util.List.of(task));
                }

                ProcessRuntimeService service() {
                        return new ProcessRuntimeService(
                                        processDefinitionConfigMapper,
                                        repositoryService,
                                        runtimeService,
                                        identityService,
                                        taskService,
                                        processTaskService,
                                        multiInstanceCollectionListener,
                                        entityProcessLinkMapper, lockedRows, () -> java.time.LocalDateTime.of(2026, 9, 22, 3, 0));
                }
        }
}
