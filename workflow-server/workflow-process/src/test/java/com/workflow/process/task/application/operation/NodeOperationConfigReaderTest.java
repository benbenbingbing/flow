package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeOperationConfigReaderTest {

    private final NodeOperationConfigReader reader =
            new NodeOperationConfigReader(new ObjectMapper());

    @Test
    void returnsEmptyWhenAllSimpleSwitchesAreMissing() {
        assertTrue(reader.read(new UserTask()).isEmpty());
        assertTrue(reader.read(userTaskWithAssigneeConfig("""
                {"assigneeType": "user", "assigneeValue": "reviewer"}
                """)).isEmpty());
    }

    @Test
    void defaultsOmittedSimpleSwitchesToAllowed() {
        Optional<NodeOperationConfig> result = reader.read(
                userTaskWithAssigneeConfig("""
                        {"allowTransfer": false}
                        """));

        assertTrue(result.isPresent());
        assertFalse(result.orElseThrow().allowTransfer());
        assertTrue(result.orElseThrow().allowAddSign());
        assertTrue(result.orElseThrow().allowTerminate());
    }

    @Test
    void readsAllThreeExplicitSwitches() {
        NodeOperationConfig config = reader.read(userTaskWithAssigneeConfig("""
                {
                  "allowTransfer": false,
                  "allowAddSign": true,
                  "allowTerminate": false
                }
                """)).orElseThrow();

        assertFalse(config.allowTransfer());
        assertTrue(config.allowAddSign());
        assertFalse(config.allowTerminate());
    }

    @Test
    void rejectsNonBooleanSwitchValues() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reader.read(userTaskWithAssigneeConfig("""
                        {"allowAddSign": "false"}
                        """)));

        assertTrue(exception.getMessage().contains("allowAddSign"));
    }

    private UserTask userTaskWithAssigneeConfig(String config) {
        UserTask task = new UserTask();
        ExtensionElement properties = extensionElement("properties");
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute("name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute("value", config));
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
