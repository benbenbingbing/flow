package com.workflow.delegate;

import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;

import java.util.List;
import java.util.Map;

/**
 * 配置化任务属性读取器
 * 从 Flowable BPMN 模型元素的扩展元素中递归查找指定 {@code property} 的 value，
 * 供配置化任务代理（服务任务、发送任务、业务规则任务等）读取节点配置时使用。
 */
public final class ConfiguredTaskPropertyReader {

    private ConfiguredTaskPropertyReader() {
    }

    /**
     * 从 BPMN 元素的扩展元素中递归查找名为 propertyName 的 property 值。
     *
     * @param element      BPMN 元素，为 null 时返回 null
     * @param propertyName 目标属性名
     * @return 属性值，未找到时返回 null
     */
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

    /**
     * 递归在扩展元素列表中查找目标属性。
     *
     * @param elements     扩展元素列表
     * @param propertyName 目标属性名
     * @return 属性值，未找到时返回 null
     */
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

    /**
     * 从扩展元素中按名称获取属性值。
     *
     * @param element 扩展元素
     * @param name    属性名
     * @return 属性值，未找到时返回 null
     */
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
