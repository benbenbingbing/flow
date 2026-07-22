package com.workflow.service.cc;

import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProcessCcConfigService {
    private final RepositoryService repositoryService;

    public ProcessCcConfigService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public String findConfig(String processDefinitionId, String nodeId) {
        if (processDefinitionId == null) {
            return null;
        }
        BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
        if (model == null) {
            return null;
        }
        BaseElement element = nodeId == null ? model.getMainProcess() : model.getMainProcess().getFlowElement(nodeId, true);
        return findProperty(element, "ccConfig");
    }

    private String findProperty(BaseElement element, String propertyName) {
        if (element == null || element.getExtensionElements() == null) {
            return null;
        }
        for (List<ExtensionElement> elements : element.getExtensionElements().values()) {
            for (ExtensionElement extension : elements) {
                String value = findProperty(extension, propertyName);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String findProperty(ExtensionElement element, String propertyName) {
        if ("property".equals(element.getName())) {
            String name = attribute(element, "name");
            if (propertyName.equals(name)) {
                return attribute(element, "value");
            }
        }
        if (element.getChildElements() != null) {
            for (List<ExtensionElement> children : element.getChildElements().values()) {
                for (ExtensionElement child : children) {
                    String value = findProperty(child, propertyName);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private String attribute(ExtensionElement element, String name) {
        for (Map.Entry<String, List<ExtensionAttribute>> entry : element.getAttributes().entrySet()) {
            for (ExtensionAttribute attribute : entry.getValue()) {
                if (name.equals(attribute.getName())) {
                    return attribute.getValue();
                }
            }
        }
        return null;
    }
}
