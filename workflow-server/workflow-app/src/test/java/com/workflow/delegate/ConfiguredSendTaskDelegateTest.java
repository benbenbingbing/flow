package com.workflow.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.service.ProcessCcRuntimeService;
import com.workflow.service.cc.CcRuntimeContext;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguredSendTaskDelegateTest {

    @Test
    void resolvesRecipientsAndTemplatesBeforeTriggeringInAppNotification() throws Exception {
        ProcessCcRuntimeService ccRuntimeService = mock(ProcessCcRuntimeService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ProcessDefinitionQuery definitionQuery = mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId("definition-1")).thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definition.getKey()).thenReturn("expense_flow");
        when(definition.getName()).thenReturn("费用审批");
        when(ccRuntimeService.trigger(
                org.mockito.ArgumentMatchers.any(CcRuntimeContext.class),
                anyString())).thenReturn(2);

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getCurrentFlowElement()).thenReturn(serviceTask(
                "sendConfig",
                """
                {
                  "to":"admin,${reviewer}",
                  "channels":["message"],
                  "subject":"审批通知-${businessNo}",
                  "content":"请处理 ${businessNo}",
                  "templateKey":"approval_notice"
                }
                """));
        when(execution.getVariable("reviewer")).thenReturn("reviewer1");
        when(execution.getVariable("businessNo")).thenReturn("BX-1001");
        when(execution.getVariables()).thenReturn(Map.of(
                "reviewer", "reviewer1",
                "businessNo", "BX-1001"));
        when(execution.getProcessInstanceId()).thenReturn("instance-1");
        when(execution.getProcessDefinitionId()).thenReturn("definition-1");
        when(execution.getProcessInstanceBusinessKey()).thenReturn("business-1");
        when(execution.getCurrentActivityId()).thenReturn("send-task-1");
        when(execution.getCurrentActivityName()).thenReturn("发送审批通知");

        new ConfiguredSendTaskDelegate(
                ccRuntimeService,
                repositoryService,
                objectMapper).execute(execution);

        ArgumentCaptor<CcRuntimeContext> contextCaptor =
                ArgumentCaptor.forClass(CcRuntimeContext.class);
        ArgumentCaptor<String> configCaptor = ArgumentCaptor.forClass(String.class);
        verify(ccRuntimeService).trigger(contextCaptor.capture(), configCaptor.capture());
        CcRuntimeContext context = contextCaptor.getValue();
        assertEquals("expense_flow", context.processKey());
        assertEquals("发送审批通知", context.nodeName());

        JsonNode ccConfig = objectMapper.readTree(configCaptor.getValue());
        assertEquals("IN_APP", ccConfig.path("channels").get(0).asText());
        assertEquals("admin", ccConfig.path("recipientRules").get(0).path("values").get(0).asText());
        assertEquals("reviewer1", ccConfig.path("recipientRules").get(0).path("values").get(1).asText());
        assertEquals(
                "审批通知-BX-1001 - 请处理 BX-1001 [approval_notice]",
                ccConfig.path("summary").asText());
        verify(execution).setVariable("send-task-1_sendCount", 2);
    }

    private ServiceTask serviceTask(String propertyName, String propertyValue) {
        ServiceTask task = new ServiceTask();
        ExtensionElement properties = extensionElement("properties");
        properties.addChildElement(property(propertyName, propertyValue));
        task.addExtensionElement(properties);
        return task;
    }

    private ExtensionElement property(String name, String value) {
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute("name", name));
        property.addAttribute(new ExtensionAttribute("value", value));
        return property;
    }

    private ExtensionElement extensionElement(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
