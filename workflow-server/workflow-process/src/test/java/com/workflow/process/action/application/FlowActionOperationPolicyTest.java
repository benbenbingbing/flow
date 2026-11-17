package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.*;
import com.workflow.contracts.process.action.spi.FlowActionProvider;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 保存/发布时的作用域、同步阻断规则与上下文写入限制，覆盖每一个标准时机。 */
class FlowActionOperationPolicyTest {
    @ParameterizedTest
    @EnumSource(FlowActionTriggerTiming.class)
    void everyTimingHasConsistentCatalogAndEnforcedPolicy(FlowActionTriggerTiming timing) {
        var catalog = new FlowActionTimingCatalog(List.of());
        var option = catalog.find(timing.name()).orElseThrow();
        assertEquals(timing.getOperationPhase(), option.getOperationPhase());
        assertEquals(timing.isBlockingOperationTiming(), option.isBlocking());
        for (var mode : FlowActionExecutionMode.values()) {
            for (var policy : FlowActionFailurePolicy.values()) {
                if (timing.isBlockingOperationTiming()
                        && (mode != FlowActionExecutionMode.IN_TRANSACTION || policy != FlowActionFailurePolicy.ROLLBACK)) {
                    assertThrows(IllegalArgumentException.class, () -> timing.validateExecution(mode.name(), policy.name()));
                } else assertDoesNotThrow(() -> timing.validateExecution(mode.name(), policy.name()));
            }
        }
        if (timing.isUserTaskOnly()) {
            assertTrue(catalog.list("PROCESS", "bpmn:Process").stream().noneMatch(item -> item.getValue().equals(timing.name())));
            assertTrue(catalog.list("NODE", "bpmn:ServiceTask").stream().noneMatch(item -> item.getValue().equals(timing.name())));
            assertTrue(catalog.list("NODE", "bpmn:UserTask").stream().anyMatch(item -> item.getValue().equals(timing.name())));
        }
    }

    @Test void savingAndPublishingCannotRelaxPreconditionOrBindItToAnotherScope() {
        var definitions = mock(ProcessDefinitionConfigMapper.class);
        var process = new ProcessDefinitionConfig();
        process.setBpmnXml("<definitions xmlns='http://www.omg.org/spec/BPMN/20100524/MODEL'><process id='p'><userTask id='review'/></process></definitions>");
        when(definitions.selectById("p")).thenReturn(process);
        var beans = mock(ApplicationContext.class);
        when(beans.getBean("handler")).thenReturn(mock(FlowActionProvider.class));
        var validator = new FlowActionConfigurationValidator(definitions, beans, new FlowActionTimingCatalog(List.of()),
                new ObjectMapper(), mock(FlowActionFailureStrategyCatalog.class));
        var action = new FlowAction();
        action.setProcessConfigId("p"); action.setScopeType("NODE"); action.setElementId("review");
        action.setTriggerTiming("TASK_SUBMIT_VALIDATING"); action.setInterfaceName("handler");
        action.setExecutionMode("IN_TRANSACTION"); action.setFailurePolicy("ROLLBACK");
        assertDoesNotThrow(() -> validator.validate(action));
        for (String policy : List.of("CONTINUE", "CUSTOM")) {
            action.setFailurePolicy(policy);
            assertThrows(IllegalArgumentException.class, () -> validator.validate(action));
        }
        action.setExecutionMode("AFTER_COMMIT"); action.setFailurePolicy("RETRY");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(action));
        action.setExecutionMode("IN_TRANSACTION"); action.setFailurePolicy("ROLLBACK"); action.setScopeType("PROCESS");
        assertThrows(RuntimeException.class, () -> validator.validate(action));
    }

    @Test void validationRejectsWritesButStartPreparationHasAnExplicitVariableContainer() {
        var context = new FlowActionContext();
        context.setTriggerTiming("PROCESS_START_VALIDATING");
        context.setVariablesSnapshot(Map.of());
        assertEquals(Map.of(), context.getVariables());
        assertNull(context.getVariable("missing"));
        assertThrows(IllegalStateException.class, () -> context.setProcessVariable("risk", "HIGH"));
        assertThrows(IllegalStateException.class, () -> context.setProcessVariables(Map.of("risk", "HIGH")));
        var startup = new java.util.LinkedHashMap<String, Object>();
        context.setVariablesSnapshot(startup); context.setTriggerTiming("PROCESS_STARTING");
        context.setProcessVariable("risk", "HIGH");
        context.setProcessVariables(Map.of("amount", 12));
        assertEquals(Map.of("risk", "HIGH", "amount", 12), startup);
    }

    @Test void customTimingSuffixDoesNotChangeItsVariableWriteContract() {
        var runtime = mock(com.workflow.contracts.process.action.port.FlowActionRuntimePort.class);
        var context = new FlowActionContext();
        context.setTriggerTiming("CUSTOM_VALIDATING");
        context.setProcessInstanceId("instance"); context.setRuntimeAccess(runtime);
        context.setProcessVariable("risk", "HIGH");
        verify(runtime).setVariable("instance", "risk", "HIGH");
    }
}
