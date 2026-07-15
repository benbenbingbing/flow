package com.workflow.process.action;

import com.workflow.entity.FlowAction;
import com.workflow.service.FlowActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程动作 BPMN 注入器。
 *
 * <p>发布流程时，为配置了 flow_action 的顺序流注入执行监听器，使顺序流被触发时能执行对应动作。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFlowActionBpmnInjector {

    private final FlowActionService flowActionService;

    private static final String LISTENER_BEAN_EXPRESSION = "${sequenceFlowExecutionListener}";

    /**
     * 为 BPMN XML 中配置了 flow_action 的顺序流注入执行监听器。
     *
     * @param processConfigId 流程配置 ID
     * @param bpmnXml         原始 BPMN XML
     * @return 注入后的 BPMN XML
     */
    public String inject(String processConfigId, String bpmnXml) {
        if (!StringUtils.hasText(bpmnXml)) {
            return bpmnXml;
        }

        List<FlowAction> draftActions = flowActionService.findDraftActions(processConfigId);
        if (draftActions == null || draftActions.isEmpty()) {
            return bpmnXml;
        }

        Map<String, List<FlowAction>> actionsByFlowId = draftActions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getEnabled()))
                .collect(Collectors.groupingBy(FlowAction::getSequenceFlowId));

        if (actionsByFlowId.isEmpty()) {
            return bpmnXml;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            NodeList sequenceFlows = doc.getElementsByTagNameNS("*", "sequenceFlow");
            boolean injected = false;
            for (int i = 0; i < sequenceFlows.getLength(); i++) {
                Element sequenceFlow = (Element) sequenceFlows.item(i);
                String flowId = sequenceFlow.getAttribute("id");
                if (!actionsByFlowId.containsKey(flowId)) {
                    continue;
                }

                injectListener(doc, sequenceFlow);
                injected = true;
            }

            if (!injected) {
                return bpmnXml;
            }

            return toXmlString(doc);
        } catch (Exception e) {
            log.warn("注入流程动作监听器失败: processConfigId={}", processConfigId, e);
            return bpmnXml;
        }
    }

    private void injectListener(Document doc, Element sequenceFlow) {
        String nsUri = sequenceFlow.getNamespaceURI();
        String prefix = sequenceFlow.getPrefix();
        String extLocalName = "extensionElements";
        Element extensionElements = findChildElement(sequenceFlow, nsUri, extLocalName);
        if (extensionElements == null) {
            extensionElements = doc.createElementNS(nsUri, qualifiedName(prefix, extLocalName));
            sequenceFlow.appendChild(extensionElements);
        }

        Element listener = doc.createElementNS("http://flowable.org/bpmn", "flowable:executionListener");
        listener.setAttribute("event", "take");
        listener.setAttribute("delegateExpression", LISTENER_BEAN_EXPRESSION);
        extensionElements.appendChild(listener);
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

    private String qualifiedName(String prefix, String localName) {
        return StringUtils.hasText(prefix) ? prefix + ":" + localName : localName;
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
