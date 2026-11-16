package com.workflow.process.condition.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 将顺序流业务接口配置编译为受控引擎条件；不执行业务判断。 */
@Component
@RequiredArgsConstructor
public class SequenceFlowConditionBpmnCompiler {
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private final SequenceFlowConditionEvaluator evaluator;
    private final ObjectMapper objectMapper;

    /** 校验实现和参数，拒绝条件会被忽略的并行网关出口及默认流上的接口配置。 */
    public boolean compile(Document document) {
        boolean changed = false;
        var flows = document.getElementsByTagNameNS(BPMN_NS, "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i);
            var properties = flow.getElementsByTagNameNS("*", "property");
            String configJson = null;
            for (int j = 0; j < properties.getLength(); j++) {
                Element property = (Element) properties.item(j);
                if (!SequenceFlowConditionConfig.PROPERTY.equals(property.getAttribute("name"))) continue;
                if (configJson != null) throw invalid(flow, "业务条件接口配置重复");
                configJson = property.getAttribute("value");
            }
            if (configJson == null) continue;
            Element source = findElement(document, flow.getAttribute("sourceRef"));
            if (source == null || "parallelGateway".equals(source.getLocalName())
                    || "eventBasedGateway".equals(source.getLocalName())) {
                throw invalid(flow, "来源节点不支持业务条件接口，请在并行汇聚后添加条件网关");
            }
            if (flow.getAttribute("id").equals(source.getAttribute("default"))) {
                throw invalid(flow, "默认流不能配置业务条件接口");
            }
            try {
                evaluator.requireProvider(SequenceFlowConditionConfig.parse(configJson, objectMapper));
            } catch (RuntimeException exception) {
                throw invalid(flow, exception.getMessage());
            }
            // 重编译消除旧表达式；扩展属性是此类型条件唯一的设计配置。
            for (Node child = flow.getFirstChild(); child != null;) {
                Node next = child.getNextSibling();
                if (child instanceof Element element && "conditionExpression".equals(element.getLocalName())) flow.removeChild(child);
                child = next;
            }
            Element condition = document.createElementNS(BPMN_NS, "bpmn:conditionExpression");
            condition.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "bpmn:tFormalExpression");
            condition.setTextContent(SequenceFlowConditionConfig.expression(flow.getAttribute("id")));
            flow.appendChild(condition);
            changed = true;
        }
        return changed;
    }

    private Element findElement(Document document, String id) {
        var elements = document.getElementsByTagNameNS(BPMN_NS, "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (id.equals(element.getAttribute("id"))) return element;
        }
        return null;
    }

    private IllegalArgumentException invalid(Element flow, String message) {
        return new IllegalArgumentException("SEQUENCE_FLOW_CONDITION_INVALID: " + message + ", element=" + flow.getAttribute("id"));
    }
}
