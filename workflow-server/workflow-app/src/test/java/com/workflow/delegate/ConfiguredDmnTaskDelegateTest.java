package com.workflow.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.dmn.api.DecisionExecutionAuditContainer;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.ExecuteDecisionBuilder;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguredDmnTaskDelegateTest {

    @Test
    void mapsInputsAndWritesSingleDecisionResultBackToProcessVariables() {
        DmnDecisionService decisionService = mock(DmnDecisionService.class);
        ExecuteDecisionBuilder builder = mock(ExecuteDecisionBuilder.class);
        DecisionExecutionAuditContainer audit = new DecisionExecutionAuditContainer();
        audit.setDecisionResult(List.of(Map.of(
                "approvalLevel", "DIRECTOR",
                "riskScore", 82)));
        when(decisionService.createExecuteDecisionBuilder()).thenReturn(builder);
        when(builder.decisionKey("expense_risk")).thenReturn(builder);
        when(builder.instanceId("instance-1")).thenReturn(builder);
        when(builder.executionId("execution-1")).thenReturn(builder);
        when(builder.activityId("rule-task-1")).thenReturn(builder);
        when(builder.tenantId("tenant-1")).thenReturn(builder);
        when(builder.variables(org.mockito.ArgumentMatchers.anyMap())).thenReturn(builder);
        when(builder.executeWithAuditTrail()).thenReturn(audit);

        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getCurrentFlowElement()).thenReturn(serviceTask(
                "ruleConfig",
                """
                {
                  "decisionRef":"expense_risk",
                  "inputVariables":"{\\"amount\\":\\"${expenseAmount}\\",\\"region\\":\\"CN\\"}",
                  "resultVariable":"decisionResult",
                  "mapDecisionResult":true
                }
                """));
        when(execution.getVariable("expenseAmount")).thenReturn(12800);
        when(execution.getProcessInstanceId()).thenReturn("instance-1");
        when(execution.getId()).thenReturn("execution-1");
        when(execution.getCurrentActivityId()).thenReturn("rule-task-1");
        when(execution.getTenantId()).thenReturn("tenant-1");

        new ConfiguredDmnTaskDelegate(
                decisionService,
                new ObjectMapper()).execute(execution);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variablesCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(builder).variables(variablesCaptor.capture());
        assertEquals(12800, variablesCaptor.getValue().get("amount"));
        assertEquals("CN", variablesCaptor.getValue().get("region"));
        verify(execution).setVariable(
                "decisionResult",
                Map.of("approvalLevel", "DIRECTOR", "riskScore", 82));
        verify(execution).setVariable("approvalLevel", "DIRECTOR");
        verify(execution).setVariable("riskScore", 82);
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
