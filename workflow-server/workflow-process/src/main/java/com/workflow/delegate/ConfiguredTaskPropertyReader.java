package com.workflow.delegate;

import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;

import java.util.List;
import java.util.Map;

public final class ConfiguredTaskPropertyReader {

    private ConfiguredTaskPropertyReader() {
    }

    public static String read(BaseElement element, String propertyName) {
        if (element == null || element.getExtensionElements() == null) {
            return null;
        }
        for (List<ExtensionElement> values : element.getExtensionElements().values()) {
            String value = read(values, propertyName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String read(List<ExtensionElement> elements, String propertyName) {
        if (elements == null) {
            return null;
        }
        for (ExtensionElement element : elements) {
            if ("property".equalsIgnoreCase(element.getName())
                    && propertyName.equals(attribute(element, "name"))) {
                return attribute(element, "value");
            }
            if (element.getChildElements() != null) {
                for (List<ExtensionElement> children : element.getChildElements().values()) {
                    String value = read(children, propertyName);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String attribute(ExtensionElement element, String name) {
        for (List<ExtensionAttribute> values : element.getAttributes().values()) {
            for (ExtensionAttribute attribute : values) {
                if (name.equals(attribute.getName())) {
                    return attribute.getValue();
                }
            }
        }
        return null;
    }
}
