package com.workflow.process.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流程动作 BPMN 注入器单元测试。
 *
 * <p>被测对象为 {@link ProcessFlowActionBpmnInjector}，验证注入时移除遗留的
 * 连线执行监听器(sequenceFlowExecutionListener)，以及无动作时不改动 BPMN。</p>
 */
class ProcessFlowActionBpmnInjectorTest {

    /**
     * 注入时应移除遗留的 sequenceFlowExecutionListener 但保留自定义监听器。
     *
     * <p>场景：连线含两个执行监听器，断言输出不含 sequenceFlowExecutionListener，
     * 仍包含 customListener。</p>
     */
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

    /** 无动作时注入应原样返回 BPMN XML，不做任何改动 */
    @Test
    void shouldSkipInjectionWhenNoActions() {
        ProcessFlowActionBpmnInjector injector = new ProcessFlowActionBpmnInjector();

        String bpmnXml = "<definitions></definitions>";
        String result = injector.inject("process-1", bpmnXml);

        assertEquals(bpmnXml, result);
    }
}
