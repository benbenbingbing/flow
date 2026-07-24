package com.workflow.service.cc;

import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 流程知会配置服务。
 *
 * <p>从 BPMN 模型的扩展属性中读取流程/节点级别的知会配置（ccConfig），
 * 供运行时判断是否允许人工知会及触发自动知会。</p>
 */
@Service
public class ProcessCcConfigService {
    private final RepositoryService repositoryService;

    public ProcessCcConfigService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * 查询指定流程/节点的知会配置JSON。
     *
     * @param processDefinitionId 流程定义ID，为空时返回 null
     * @param nodeId              节点ID，为空时取主流程的配置
     * @return 知会配置JSON字符串，不存在则返回 null
     */
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

    /** 递归在扩展元素中查找指定名称的 property 属性值 */
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

    /** 递归在扩展元素及其子元素中查找指定名称的 property 属性值 */
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

    /** 从扩展元素的属性集合中按名称取值 */
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
