package com.workflow.delegate;

import com.workflow.process.task.infrastructure.flowable.ReceiveTaskTimeoutDelegate;

import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接收任务超时代理单元测试。
 */
class ReceiveTaskTimeoutDelegateTest {

    private final ReceiveTaskTimeoutDelegate delegate =
            new ReceiveTaskTimeoutDelegate();

    @Test
    void continueStrategySetsTimeoutVariables() {
        DelegateExecution execution = execution("receive-payment", "continue");

        delegate.execute(execution);

        verify(execution).setVariable("receiveTaskTimedOut", true);
        verify(execution).setVariable(
                "receiveTaskTimeoutActivityId",
                "receive-payment");
        verify(execution).setVariable("receive-payment_timedOut", true);
    }

    @Test
    void errorStrategyThrowsAndStopsTheTimeoutPath() {
        DelegateExecution execution = execution("receive-payment", "error");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> delegate.execute(execution));

        assertTrue(exception.getMessage().contains("receive-payment"));
    }

    private DelegateExecution execution(String receiveTaskId, String action) {
        DelegateExecution execution = mock(DelegateExecution.class);
        ServiceTask task = new ServiceTask();
        ExtensionElement properties = extensionElement("properties");
        properties.addChildElement(property("receiveTaskId", receiveTaskId));
        properties.addChildElement(property("receiveTimeoutAction", action));
        task.addExtensionElement(properties);
        when(execution.getCurrentFlowElement()).thenReturn(task);
        return execution;
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
