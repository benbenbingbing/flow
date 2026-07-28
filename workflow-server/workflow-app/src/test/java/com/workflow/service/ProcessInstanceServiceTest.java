package com.workflow.service;

import com.workflow.process.instance.application.ProcessInstanceService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;

import com.workflow.process.task.api.request.ReceiveTaskTriggerRequest;
import com.workflow.process.instance.api.response.ProcessProgressDTO;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.ReceiveTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 流程实例服务单元测试。
 *
 * <p>被测对象：{@link ProcessInstanceService}，覆盖按流程 key 获取 BPMN XML、流程不存在时返回 null、
 * 服务依赖注入等场景。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProcessInstanceServiceTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private HistoryService historyService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private TaskService taskService;

    @Mock
    private ProcessDefinitionConfigMapper processConfigMapper;

    @Mock
    private ProcessInstanceAccessService processInstanceAccessService;

    @InjectMocks
    private ProcessInstanceService processInstanceService;

    /** 测试按流程 key 获取 BPMN XML：验证返回的 XML 与配置中一致 */
    @Test
    void testGetBpmnXmlByProcessKey() {
        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setProcessKey("leave_process");
        config.setBpmnXml("<bpmn>...</bpmn>");
        
        when(processConfigMapper.findByProcessKey("leave_process")).thenReturn(Optional.of(config));

        String result = processInstanceService.getBpmnXmlByProcessKey("leave_process");

        assertNotNull(result);
        assertEquals("<bpmn>...</bpmn>", result);
    }

    /** 测试按不存在的流程 key 获取 BPMN XML：验证流程定义查询也为空时返回 null */
    @Test
    void testGetBpmnXmlByProcessKeyNotFound() {
        when(processConfigMapper.findByProcessKey("not_exist")).thenReturn(Optional.empty());

        ProcessDefinitionQuery processDefQuery = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefQuery);
        when(processDefQuery.processDefinitionKey("not_exist")).thenReturn(processDefQuery);
        when(processDefQuery.latestVersion()).thenReturn(processDefQuery);
        when(processDefQuery.singleResult()).thenReturn(null);

        String result = processInstanceService.getBpmnXmlByProcessKey("not_exist");

        assertNull(result);
    }

    /** 测试服务及其依赖被正确注入：验证各 Mock 与被测服务均非空 */
    @Test
    void testServiceInjected() {
        assertNotNull(processInstanceService);
        assertNotNull(runtimeService);
        assertNotNull(historyService);
        assertNotNull(repositoryService);
        assertNotNull(taskService);
        assertNotNull(processConfigMapper);
    }

    /** 匹配消息名时应触发唯一的接收任务执行实例并传递变量 */
    @Test
    void triggerReceiveTaskValidatesMessageAndContinuesExecution() {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getProcessDefinitionId()).thenReturn("definition-1");
        stubRunningProcess(instance);

        Execution execution = mock(Execution.class);
        when(execution.getId()).thenReturn("execution-1");
        when(execution.getActivityId()).thenReturn("receive-payment");
        when(execution.getProcessInstanceId()).thenReturn("instance-1");
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId("instance-1")).thenReturn(executionQuery);
        when(executionQuery.activityId("receive-payment")).thenReturn(executionQuery);
        when(executionQuery.list()).thenReturn(List.of(execution));

        BpmnModel model = new BpmnModel();
        model.addProcess(new org.flowable.bpmn.model.Process());
        model.getMainProcess().addFlowElement(receiveTask(
                "receive-payment",
                "paymentCallback"));
        when(repositoryService.getBpmnModel("definition-1")).thenReturn(model);

        ReceiveTaskTriggerRequest request = new ReceiveTaskTriggerRequest();
        request.setActivityId("receive-payment");
        request.setMessageRef("paymentCallback");
        request.setVariables(Map.of("paymentStatus", "SUCCESS"));

        String executionId = processInstanceService.triggerReceiveTask(
                "instance-1",
                request);

        assertEquals("execution-1", executionId);
        verify(runtimeService).trigger(
                "execution-1",
                Map.of("paymentStatus", "SUCCESS"));
    }

    /** 消息名不匹配时必须拒绝触发，避免错误回调推进流程 */
    @Test
    void triggerReceiveTaskRejectsUnexpectedMessage() {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getProcessDefinitionId()).thenReturn("definition-1");
        stubRunningProcess(instance);

        Execution execution = mock(Execution.class);
        when(execution.getId()).thenReturn("execution-1");
        when(execution.getActivityId()).thenReturn("receive-payment");
        when(execution.getProcessInstanceId()).thenReturn("instance-1");
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.executionId("execution-1")).thenReturn(executionQuery);
        when(executionQuery.singleResult()).thenReturn(execution);

        BpmnModel model = new BpmnModel();
        model.addProcess(new org.flowable.bpmn.model.Process());
        model.getMainProcess().addFlowElement(receiveTask(
                "receive-payment",
                "paymentCallback"));
        when(repositoryService.getBpmnModel("definition-1")).thenReturn(model);

        ReceiveTaskTriggerRequest request = new ReceiveTaskTriggerRequest();
        request.setExecutionId("execution-1");
        request.setMessageRef("differentMessage");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> processInstanceService.triggerReceiveTask(
                        "instance-1",
                        request));

        assertTrue(exception.getMessage().contains("消息标识不匹配"));
        verify(runtimeService, never()).trigger(anyString(), anyMap());
    }

    private void stubRunningProcess(ProcessInstance instance) {
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId("instance-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(instance);
    }

    private ReceiveTask receiveTask(String id, String messageRef) {
        ReceiveTask task = new ReceiveTask();
        task.setId(id);
        ExtensionElement properties = extensionElement("properties");
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute("name", "receiveConfig"));
        property.addAttribute(new ExtensionAttribute(
                "value",
                "{\"messageRef\":\"" + messageRef + "\"}"));
        properties.addChildElement(property);
        task.addExtensionElement(properties);
        return task;
    }

    private ExtensionElement extensionElement(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
