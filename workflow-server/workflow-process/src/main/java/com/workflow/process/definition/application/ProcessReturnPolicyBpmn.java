package com.workflow.process.definition.application;

import com.workflow.process.task.application.operation.NodeHistoryReturnPolicy;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/** 在设计 BPMN 保留流程默认值，在部署 BPMN 单独写入发布候选。 */
final class ProcessReturnPolicyBpmn {
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String FLOWABLE = "http://flowable.org/bpmn";
    // 新建尚无 XML 时也须保存默认值。初始画布带坐标，设计器可直接打开。
    private static final String STARTER_BPMN = """
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="Process_1" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" name="开始"/>
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
                  <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
                    <dc:Bounds x="152" y="152" width="36" height="36"/>
                  </bpmndi:BPMNShape>
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            </bpmn:definitions>
            """;

    private ProcessReturnPolicyBpmn() {}

    /** 保存草稿或还原历史时以流程原有默认值为准，避免输入 BPMN 改变存量流程默认规则。 */
    static String preserveDefault(String previousXml, String xml) {
        return withDefault(xml, hasAutomaticDefault(previousXml));
    }

    static boolean hasAutomaticDefault(String xml) {
        if (xml == null || !xml.contains(NodeHistoryReturnPolicy.DEFAULT_PROPERTY)) return false;
        try {
            var document = ProcessBpmnSynchronousExecutionNormalizer.parse(xml);
            return processes(document.getDocumentElement()).stream()
                    .flatMap(process -> children(process, BPMN, "extensionElements").stream())
                    .flatMap(extension -> children(extension, FLOWABLE, "properties").stream())
                    .flatMap(container -> children(container, FLOWABLE, "property").stream())
                    .filter(property -> NodeHistoryReturnPolicy.DEFAULT_PROPERTY.equals(property.getAttribute("name")))
                    .anyMatch(property -> "HISTORY".equals(property.getAttribute("value")));
        } catch (Exception error) {
            throw new IllegalArgumentException("无法读取流程默认回退策略", error);
        }
    }

    /** 自动标记仅用于新建流程。旧流程用缺省值表示关闭。 */
    static String withDefault(String xml, boolean automatic) {
        if (!automatic && (xml == null || !xml.contains(NodeHistoryReturnPolicy.DEFAULT_PROPERTY))) return xml;
        String source = xml == null || xml.isBlank() ? STARTER_BPMN : xml;
        try {
            var document = ProcessBpmnSynchronousExecutionNormalizer.parse(source);
            var processes = processes(document.getDocumentElement());
            if (automatic && processes.isEmpty()) throw new IllegalArgumentException("BPMN 缺少流程节点");
            boolean changed = false;
            // 草稿可包含参与者引用的多个流程，默认值属于整份流程定义。
            for (Element process : processes) {
                changed |= setProperty(process, NodeHistoryReturnPolicy.DEFAULT_PROPERTY, automatic ? "HISTORY" : null);
            }
            return changed ? ProcessBpmnSynchronousExecutionNormalizer.write(document) : source;
        } catch (Exception error) {
            throw new IllegalArgumentException("无法保存流程默认回退策略", error);
        }
    }

    /** 部署快照由服务器覆盖生成，导入或草稿里携带的同名属性不能作为运行时配置。 */
    static String withPublishedSnapshot(String xml, String processKey, String snapshot) {
        try {
            var document = ProcessBpmnSynchronousExecutionNormalizer.parse(xml);
            boolean changed = false;
            for (Element process : processes(document.getDocumentElement())) {
                changed |= setProperty(process, NodeHistoryReturnPolicy.SNAPSHOT_PROPERTY,
                        processKey.equals(process.getAttribute("id")) ? snapshot : null);
            }
            return changed ? ProcessBpmnSynchronousExecutionNormalizer.write(document) : xml;
        } catch (Exception error) {
            throw new IllegalArgumentException("无法固化发布版回退候选", error);
        }
    }

    private static boolean setProperty(Element process, String name, String value) {
        var matching = properties(process, name);
        if (matching.size() == 1 && value != null && isCanonicalProperty(matching.get(0), process)
                && value.equals(matching.get(0).getAttribute("value"))) return false;
        matching.forEach(property -> property.getParentNode().removeChild(property));
        if (value == null) return !matching.isEmpty();

        var document = process.getOwnerDocument();
        var extension = children(process, BPMN, "extensionElements").stream().findFirst().orElse(null);
        if (extension == null) {
            extension = document.createElementNS(BPMN, "bpmn:extensionElements");
            // BPMN 扩展位于 documentation 后、其他流程子元素前。
            var anchor = process.getFirstChild();
            while (anchor != null && (!(anchor instanceof Element element)
                    || "documentation".equals(element.getLocalName()))) anchor = anchor.getNextSibling();
            process.insertBefore(extension, anchor);
        }
        var container = children(extension, FLOWABLE, "properties").stream().findFirst().orElse(null);
        if (container == null) {
            container = document.createElementNS(FLOWABLE, "flowable:properties");
            extension.appendChild(container);
        }
        var property = document.createElementNS(FLOWABLE, "flowable:property");
        property.setAttribute("name", name);
        property.setAttribute("value", value);
        container.appendChild(property);
        return true;
    }

    private static List<Element> properties(Element process, String name) {
        List<Element> matches = new ArrayList<>();
        // 运行时读取器会递归查找扩展，写入时先去掉所有同名项，防止旧容器盖住服务端生成值。
        for (Element extension : children(process, BPMN, "extensionElements")) {
            var descendants = extension.getElementsByTagNameNS("*", "*");
            for (int index = 0; index < descendants.getLength(); index++) {
                Element property = (Element) descendants.item(index);
                if ("property".equalsIgnoreCase(property.getLocalName()) && name.equals(property.getAttribute("name"))) {
                    matches.add(property);
                }
            }
        }
        return matches;
    }

    private static boolean isCanonicalProperty(Element property, Element process) {
        var container = property.getParentNode();
        var extension = container.getParentNode();
        return FLOWABLE.equals(property.getNamespaceURI()) && FLOWABLE.equals(container.getNamespaceURI())
                && "properties".equalsIgnoreCase(container.getLocalName()) && BPMN.equals(extension.getNamespaceURI())
                && "extensionElements".equals(extension.getLocalName()) && extension.getParentNode() == process;
    }

    private static List<Element> processes(Element definitions) {
        return children(definitions, BPMN, "process");
    }

    private static List<Element> children(Element parent, String namespace, String name) {
        List<Element> elements = new ArrayList<>();
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equalsIgnoreCase(element.getLocalName())
                    && namespace.equals(element.getNamespaceURI())) elements.add(element);
        }
        return elements;
    }
}
