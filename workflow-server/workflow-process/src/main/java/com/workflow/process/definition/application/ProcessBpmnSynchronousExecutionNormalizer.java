package com.workflow.process.definition.application;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 将流程设计 XML 中的节点执行方式强制归一化为同步执行。
 *
 * <p>平台不再开放 BPMN 节点异步执行能力，因此草稿保存与正式发布必须共用该边界：
 * 去除 Flowable、Camunda、Activiti 的进入/离开异步属性及只对异步作业有意义的独占属性。
 * 其余引擎扩展属性与未知命名空间属性保持不变。</p>
 */
final class ProcessBpmnSynchronousExecutionNormalizer {

    private static final String BPMN_NAMESPACE =
            "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final Set<String> ENGINE_NAMESPACES = Set.of(
            "http://flowable.org/bpmn",
            "http://activiti.org/bpmn",
            "http://camunda.org/schema/1.0/bpmn");
    private static final Set<String> ENGINE_PREFIXES = Set.of(
            "flowable", "activiti", "camunda");
    private static final Set<String> UNSUPPORTED_ATTRIBUTE_NAMES = Set.of(
            "async",
            "asyncbefore",
            "asyncafter",
            "asyncleave",
            "completeasync",
            "exclusive",
            "asyncleaveexclusive",
            "nowaitstatesasyncleave");
    private static final Set<String> ASYNC_CAPABLE_BPMN_ELEMENTS = Set.of(
            "process",
            "task",
            "usertask",
            "servicetask",
            "sendtask",
            "receivetask",
            "manualtask",
            "businessruletask",
            "scripttask",
            "callactivity",
            "subprocess",
            "adhocsubprocess",
            "transaction",
            "startevent",
            "endevent",
            "intermediatecatchevent",
            "intermediatethrowevent",
            "boundaryevent",
            "exclusivegateway",
            "inclusivegateway",
            "parallelgateway",
            "complexgateway",
            "eventbasedgateway",
            "signaleventdefinition",
            "multiinstanceloopcharacteristics");
    private static final Pattern CANDIDATE_ATTRIBUTE = Pattern.compile(
            "(?i)(?:\\s|:)(?:async|asyncBefore|asyncAfter|asyncLeave|"
                    + "completeAsync|"
                    + "exclusive|asyncLeaveExclusive|"
                    + "noWaitStatesAsyncLeave)\\s*=");

    /**
     * 初始化流程BPMN{@code synchronous}执行{@code normalizer}，保存构造参数供后续方法使用。
     */
    private ProcessBpmnSynchronousExecutionNormalizer() {
    }

    /**
     * 删除不受平台支持的节点异步执行属性。
     *
     * <p>没有候选属性时原样返回，既避免无意义的 XML 重排，也允许尚未完成的普通草稿
     * 继续沿用现有校验时机。检测到异步候选属性后则必须安全解析并清理，解析失败时拒绝写入，
     * 防止通过畸形 XML 绕过同步执行边界。</p>
     *
     * @param bpmnXml BPMN 设计 XML，可为空
     * @return 已强制同步的 BPMN XML；无相关属性时返回原字符串
     * @throws IllegalArgumentException XML 含异步候选属性但无法安全解析或序列化时抛出
     */
    static String normalize(String bpmnXml) {
        if (bpmnXml == null
                || bpmnXml.isBlank()
                || !CANDIDATE_ATTRIBUTE.matcher(bpmnXml).find()) {
            return bpmnXml;
        }
        try {
            Document document = parse(bpmnXml);
            boolean changed = removeUnsupportedAttributes(document);
            return changed ? write(document) : bpmnXml;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "BPMN_SYNC_NORMALIZATION_INVALID: 无法清理节点异步执行配置",
                    exception);
        }
    }

    /**
     * 逆序删除属性，避免修改 NamedNodeMap 后跳过相邻项。
     *
     * @param document 文档，供本方法移除{@code unsupported}{@code attributes}时使用
     * @return {@code unsupported}{@code attributes}条件成立时为 true，否则为 false
     */
    private static boolean removeUnsupportedAttributes(Document document) {
        boolean changed = false;
        NodeList elements = document.getElementsByTagName("*");
        for (int elementIndex = 0;
                elementIndex < elements.getLength();
                elementIndex++) {
            Element element = (Element) elements.item(elementIndex);
            NamedNodeMap attributes = element.getAttributes();
            for (int attributeIndex = attributes.getLength() - 1;
                    attributeIndex >= 0;
                    attributeIndex--) {
                Node attribute = attributes.item(attributeIndex);
                if (isUnsupportedExecutionAttribute(element, attribute)) {
                    element.removeAttributeNode((Attr) attribute);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * 判断是否{@code unsupported}执行属性；判断结果决定调用方的后续分支。
     *
     * @param element 元素，供本方法判断是否{@code unsupported}执行属性时使用
     * @param attribute 属性，作为 {@code localName} 的输入影响后续处理
     * @return {@code unsupported}执行属性条件成立时为 true，否则为 false
     */
    private static boolean isUnsupportedExecutionAttribute(
            Element element,
            Node attribute) {
        String localName = localName(attribute).toLowerCase(Locale.ROOT);
        if (!UNSUPPORTED_ATTRIBUTE_NAMES.contains(localName)) {
            return false;
        }

        // 这些属性只在可异步 BPMN 节点上属于执行选项；尤其 completeAsync
        // 是 CallActivity 的完成作业配置，自定义扩展元素上的同名属性必须保留。
        if (!isAsyncCapableBpmnElement(element)) {
            return false;
        }

        String namespace = attribute.getNamespaceURI();
        if (namespace != null && ENGINE_NAMESPACES.contains(namespace)) {
            return true;
        }
        String prefix = attribute.getPrefix();
        if (prefix != null
                && ENGINE_PREFIXES.contains(prefix.toLowerCase(Locale.ROOT))) {
            return true;
        }

        // 兼容历史设计器写出的裸属性；仅在可异步 BPMN 元素上删除，避免误伤自定义扩展。
        return (namespace == null || namespace.isBlank())
                && isAsyncCapableBpmnElement(element);
    }

    /**
     * 判断是否{@code async}{@code capable}BPMN元素；判断结果决定调用方的后续分支。
     *
     * @param element 元素，作为 {@code contains} 的输入影响后续处理
     * @return {@code async}{@code capable}BPMN元素条件成立时为 true，否则为 false
     */
    private static boolean isAsyncCapableBpmnElement(Element element) {
        String elementNamespace = element.getNamespaceURI();
        return (elementNamespace == null
                || elementNamespace.isBlank()
                || BPMN_NAMESPACE.equals(elementNamespace))
                && ASYNC_CAPABLE_BPMN_ELEMENTS.contains(
                localName(element).toLowerCase(Locale.ROOT));
    }

    /**
     * 生成本地名称文本，供后续匹配或展示。
     *
     * @param node 节点，供本方法处理本地名称时使用
     * @return 处理后的本地名称文本，供调用方比较或展示
     */
    private static String localName(Node node) {
        if (node.getLocalName() != null) {
            return node.getLocalName();
        }
        String nodeName = node.getNodeName();
        int separator = nodeName.indexOf(':');
        return separator < 0 ? nodeName : nodeName.substring(separator + 1);
    }

    /**
     * 解析流程BPMN{@code synchronous}执行{@code normalizer}；输出作为后续校验或处理的输入。
     *
     * @param bpmnXml BPMNXML，供本方法解析流程BPMN{@code synchronous}执行{@code normalizer}时使用
     * @return 解析后的流程BPMN{@code synchronous}执行{@code normalizer}结果，供调用方继续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
    private static Document parse(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void error(SAXParseException exception)
                    throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception)
                    throws SAXException {
                throw exception;
            }
        });
        return builder.parse(new InputSource(new StringReader(bpmnXml)));
    }

    /**
     * 写入流程BPMN{@code synchronous}执行{@code normalizer}；后续读取或执行将使用更新后的状态。
     *
     * @param document 文档，作为 {@code transformer.transform} 的输入影响后续处理
     * @return 写入后的流程BPMN{@code synchronous}执行{@code normalizer}文本，供调用方比较或展示
     * @throws Exception 下游操作失败时向调用方传递
     */
    private static String write(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(
                new DOMSource(document),
                new StreamResult(writer));
        return writer.toString();
    }
}
