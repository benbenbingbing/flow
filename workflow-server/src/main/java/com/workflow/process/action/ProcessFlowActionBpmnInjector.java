package com.workflow.process.action;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * 流程动作 BPMN 兼容清理器。
 *
 * <p>流程动作已改为全局 Flowable 事件分发。发布时只移除平台历史注入的监听器，
 * 不修改用户自行配置的其他监听器。</p>
 */
@Slf4j
@Service
public class ProcessFlowActionBpmnInjector {

    private static final String LISTENER_BEAN_EXPRESSION = "${sequenceFlowExecutionListener}";

    /**
     * 清理 BPMN XML 中平台历史注入的顺序流监听器。
     *
     * @param processConfigId 流程配置 ID
     * @param bpmnXml         原始 BPMN XML
     * @return 注入后的 BPMN XML
     */
    public String inject(String processConfigId, String bpmnXml) {
        if (!StringUtils.hasText(bpmnXml)) {
            return bpmnXml;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            NodeList sequenceFlows = doc.getElementsByTagNameNS("*", "sequenceFlow");
            boolean changed = false;
            for (int i = 0; i < sequenceFlows.getLength(); i++) {
                Element sequenceFlow = (Element) sequenceFlows.item(i);
                changed |= removeLegacyListeners(sequenceFlow);
            }

            if (!changed) {
                return bpmnXml;
            }

            return toXmlString(doc);
        } catch (Exception e) {
            log.warn("清理历史流程动作监听器失败: processConfigId={}", processConfigId, e);
            return bpmnXml;
        }
    }

    private boolean removeLegacyListeners(Element sequenceFlow) {
        String nsUri = sequenceFlow.getNamespaceURI();
        Element extensionElements = findChildElement(sequenceFlow, nsUri, "extensionElements");
        if (extensionElements == null) {
            return false;
        }
        boolean changed = false;
        NodeList children = extensionElements.getChildNodes();
        for (int index = children.getLength() - 1; index >= 0; index--) {
            Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if ("executionListener".equals(element.getLocalName())
                    && LISTENER_BEAN_EXPRESSION.equals(element.getAttribute("delegateExpression"))) {
                extensionElements.removeChild(child);
                changed = true;
            }
        }
        if (!extensionElements.hasChildNodes()) {
            sequenceFlow.removeChild(extensionElements);
        }
        return changed;
    }

    private Element findChildElement(Element parent, String namespaceUri, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && localName.equals(child.getLocalName())
                    && (namespaceUri == null || namespaceUri.equals(child.getNamespaceURI()))) {
                return (Element) child;
            }
        }
        return null;
    }

    private String toXmlString(Document doc) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
