package com.workflow.process.action;

import com.workflow.entity.FlowAction;
import com.workflow.service.FlowActionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessFlowActionBpmnInjectorTest {

    @Test
    void shouldInjectExecutionListenerIntoSequenceFlowWithAction() {
        FlowActionService flowActionService = mock(FlowActionService.class);
        FlowAction action = new FlowAction();
        action.setId("action-1");
        action.setSequenceFlowId("Flow_1");
        action.setEnabled(true);
        when(flowActionService.findDraftActions("process-1")).thenReturn(List.of(action));

        ProcessFlowActionBpmnInjector injector = new ProcessFlowActionBpmnInjector(flowActionService);

        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn">
                  <process id="test" name="测试流程" isExecutable="true">
                    <startEvent id="StartEvent_1" name="开始">
                      <outgoing>Flow_1</outgoing>
                    </startEvent>
                    <userTask id="UserTask_1" name="任务1">
                      <incoming>Flow_1</incoming>
                    </userTask>
                    <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="UserTask_1"/>
                  </process>
                </definitions>
                """;

        String result = injector.inject("process-1", bpmnXml);

        assertNotNull(result);
        assertTrue(result.contains("flowable:executionListener"));
        assertTrue(result.contains("event=\"take\""));
        assertTrue(result.contains("delegateExpression=\"${sequenceFlowExecutionListener}\""));
    }

    @Test
    void shouldSkipInjectionWhenNoActions() {
        FlowActionService flowActionService = mock(FlowActionService.class);
        when(flowActionService.findDraftActions("process-1")).thenReturn(List.of());

        ProcessFlowActionBpmnInjector injector = new ProcessFlowActionBpmnInjector(flowActionService);

        String bpmnXml = "<definitions></definitions>";
        String result = injector.inject("process-1", bpmnXml);

        assertEquals(bpmnXml, result);
    }
}
