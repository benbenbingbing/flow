package com.workflow.process.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessFlowActionBpmnInjectorTest {

    @Test
    void shouldRemoveLegacyExecutionListener() {
        ProcessFlowActionBpmnInjector injector = new ProcessFlowActionBpmnInjector();

        String bpmnXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
                + "xmlns:flowable=\"http://flowable.org/bpmn\">"
                + "<process id=\"test\" name=\"测试流程\" isExecutable=\"true\">"
                + "<startEvent id=\"StartEvent_1\" name=\"开始\"><outgoing>Flow_1</outgoing></startEvent>"
                + "<userTask id=\"UserTask_1\" name=\"任务1\"><incoming>Flow_1</incoming></userTask>"
                + "<sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"UserTask_1\">"
                + "<extensionElements>"
                + "<flowable:executionListener event=\"take\" delegateExpression=\"${sequenceFlowExecutionListener}\"/>"
                + "<flowable:executionListener event=\"take\" delegateExpression=\"${customListener}\"/>"
                + "</extensionElements>"
                + "</sequenceFlow>"
                + "</process>"
                + "</definitions>";

        String result = injector.inject("process-1", bpmnXml);

        assertNotNull(result);
        assertFalse(result.contains("delegateExpression=\"${sequenceFlowExecutionListener}\""));
        assertTrue(result.contains("delegateExpression=\"${customListener}\""));
    }

    @Test
    void shouldSkipInjectionWhenNoActions() {
        ProcessFlowActionBpmnInjector injector = new ProcessFlowActionBpmnInjector();

        String bpmnXml = "<definitions></definitions>";
        String result = injector.inject("process-1", bpmnXml);

        assertEquals(bpmnXml, result);
    }
}
