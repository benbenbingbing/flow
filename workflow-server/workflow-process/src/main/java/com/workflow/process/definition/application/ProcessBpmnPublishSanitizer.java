package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发布前 BPMN 归一化处理器
 * 负责在流程发布前对 BPMN XML 进行清洗、转换与补全：
 * 包括 Camunda 属性转 Flowable 属性、多实例配置修正、跳过节点表达式注入、
 * 配置化任务（服务/发送/业务规则/调用活动/脚本）改写、ID 冲突消解等，确保 XML 可被 Flowable 正确部署执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessBpmnPublishSanitizer {

    private static final String FLOWABLE_NAMESPACE = "http://flowable.org/bpmn";

    /** JSON 序列化工具，用于解析节点配置 JSON */
    private final ObjectMapper objectMapper;

    /**
     * 对 BPMN XML 进行发布前的归一化处理。
     * <p>
     * 按顺序执行多步清洗与改写，最终返回可被 Flowable 部署的合规 XML。
     *
     * @param bpmnXml    原始 BPMN XML
     * @param processKey 流程标识，用于消解 ID 冲突与统一流程ID
     * @return 归一化后的 BPMN XML
     * @throws IllegalArgumentException 当配置化任务缺少必要配置（如发送任务缺少渠道、业务规则任务缺少决策表Key）时抛出
     */
    public String sanitize(String bpmnXml, String processKey) {
        String result = bpmnXml;

        result = removeDuplicateCamundaAssignments(result);
        result = convertCamundaAssignments(result);
        result = result.replaceAll("(?i)\\s+xmlns:camunda=\"[^\"]*\"", "");
        result = convertCamundaProperties(result);
        result = removeCamundaElements(result);
        result = result.replaceAll("(?i)\\s+camunda:[^=\\s]*=\"[^\"]*\"", "");
        result = result.replaceAll("(?i)\\s+resultVariable=\"[^\"]*\"", "");
        result = result.replaceAll("\\s+extensionProperties=\"[^\"]*\"", "");
        result = convertBareFlowableAttributes(result);
        result = convertMultiInstanceAttributes(result);
        result = processSkipNodeTasks(result);
        result = migrateApprovedExpressions(result);
        result = ensureFlowableNamespace(result);
        result = resolveBpmnIdConflicts(result, processKey);
        result = useProcessKey(result, processKey);
        result = removeInvalidMultiInstanceConfig(result);
        result = fixMultiInstanceAssignee(result);
        result = fixExplicitCcTasks(result);
        result = fixConfiguredServiceTasks(result);
        result = fixConfiguredSendTasks(result);
        result = fixConfiguredBusinessRuleTasks(result);
        result = fixConfiguredCallActivities(result);
        result = fixConfiguredReceiveTasks(result);
        result = fixScriptTasks(result);

        return result;
    }

    /**
     * 为服务任务和发送任务附加显式知会。
     * <p>
     * 已有主实现时在节点结束后执行知会监听器；没有主实现时将节点本身作为纯知会节点。
     */
    private String fixExplicitCcTasks(String bpmnXml) {
        String result = rewriteConfiguredElements(
                bpmnXml,
                "serviceTask",
                "ccConfig",
                (element, config) -> configureExplicitCc(element, config, "restConfig"));
        return rewriteConfiguredElements(
                result,
                "sendTask",
                "ccConfig",
                (element, config) -> configureExplicitCc(element, config, "sendConfig"));
    }

    private ConfiguredElement configureExplicitCc(
            ConfiguredElement element,
            com.fasterxml.jackson.databind.JsonNode config,
            String primaryConfigProperty) {
        String content = removeGeneratedCcListener(element.content());
        if (!config.path("enabled").asBoolean(false)
                || !containsTextValue(config.path("timings"), "EXPLICIT")) {
            return element.withContent(content);
        }

        String delegateExpression = attributeValue(
                element.startTag(),
                "delegateExpression");
        boolean hasConfiguredPrimary =
                readPropertyValue(content, primaryConfigProperty) != null;
        boolean hasStandardPrimary =
                hasAttribute(element.startTag(), "class")
                || hasAttribute(element.startTag(), "expression")
                || (hasAttribute(element.startTag(), "delegateExpression")
                    && !"${ccNotificationDelegate}".equals(delegateExpression));

        if (!hasConfiguredPrimary && !hasStandardPrimary) {
            String startTag = removeAttributes(
                    element.startTag(),
                    "class",
                    "expression",
                    "delegateExpression");
            return element
                    .withStartTag(setQualifiedAttribute(
                            startTag,
                            "delegateExpression",
                            "${ccNotificationDelegate}"))
                    .withContent(content);
        }

        String startTag = "${ccNotificationDelegate}".equals(delegateExpression)
                ? removeAttributes(element.startTag(), "delegateExpression")
                : element.startTag();
        String listener = "<flowable:executionListener event=\"end\" "
                + "delegateExpression=\"${ccNotificationDelegate}\" />";
        return element
                .withStartTag(startTag)
                .withContent(appendToExtensionElements(content, listener));
    }

    private boolean containsTextValue(
            com.fasterxml.jackson.databind.JsonNode values,
            String expected) {
        if (!values.isArray()) {
            return false;
        }
        for (com.fasterxml.jackson.databind.JsonNode value : values) {
            if (expected.equalsIgnoreCase(value.asText(""))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAttribute(String startTag, String name) {
        return Pattern.compile(
                "(?i)\\s+(?:flowable:)?" + Pattern.quote(name) + "=\"[^\"]*\"")
                .matcher(startTag)
                .find();
    }

    private String removeGeneratedCcListener(String content) {
        return content.replaceAll(
                "(?i)<flowable:executionListener\\b"
                        + "[^>]*delegateExpression=\"\\$\\{ccNotificationDelegate}\""
                        + "[^>]*/>",
                "");
    }

    /**
     * 改写配置化的服务任务：将扩展属性 restConfig 解析后，
     * 设置为统一的服务任务代理表达式，并按需注入结果变量名。
     */
    private String fixConfiguredServiceTasks(String bpmnXml) {
        return rewriteConfiguredElements(bpmnXml, "serviceTask", "restConfig", (element, config) -> {
            String url = config.path("url").asText("");
            if (url.isBlank()) {
                throw new IllegalArgumentException("REST 服务任务必须配置请求URL: " + element.id());
            }
            if ("multipart/form-data".equalsIgnoreCase(
                    config.path("contentType").asText(""))) {
                throw new IllegalArgumentException(
                        "REST 服务任务暂不支持 multipart/form-data: " + element.id());
            }
            validateJsonObjectDocument(config.path("headers").asText(""), "REST 请求头", element.id());
            validateJsonObjectDocument(config.path("queryParams").asText(""), "REST 查询参数", element.id());
            validateJsonObjectDocument(config.path("resultMapping").asText(""), "REST 结果映射", element.id());
            String startTag = removeAttributes(
                    element.startTag(),
                    "class", "expression", "delegateExpression", "type");
            startTag = setQualifiedAttribute(startTag, "delegateExpression", "${restServiceTaskDelegate}");
            String resultVariable = readPropertyValue(element.content(), "serviceResultVariable");
            if (resultVariable != null && !resultVariable.isBlank()) {
                startTag = setQualifiedAttribute(startTag, "resultVariableName", resultVariable);
            }
            return element.withStartTag(startTag);
        });
    }

    /**
     * 改写配置化的发送任务：校验渠道与接收人，并将 sendTask 转为 serviceTask 绑定发送代理。
     *
     * @throws IllegalArgumentException 当缺少发送渠道或接收人时抛出
     */
    private String fixConfiguredSendTasks(String bpmnXml) {
        return rewriteConfiguredElements(bpmnXml, "sendTask", "sendConfig", (element, config) -> {
            if (!config.path("channels").isArray() || config.path("channels").isEmpty()) {
                throw new IllegalArgumentException("发送任务至少需要配置一个发送渠道: " + element.id());
            }
            for (com.fasterxml.jackson.databind.JsonNode channel : config.path("channels")) {
                String value = channel.asText("");
                if (!"message".equalsIgnoreCase(value)
                        && !"in_app".equalsIgnoreCase(value)) {
                    throw new IllegalArgumentException(
                            "发送任务当前仅支持站内信渠道: " + element.id());
                }
            }
            if (config.path("to").asText("").isBlank()) {
                throw new IllegalArgumentException("发送任务必须配置接收人: " + element.id());
            }
            String startTag = element.startTag()
                    .replaceFirst("(?i)<(bpmn:)?sendTask\\b", "<$1serviceTask");
            startTag = removeAttributes(startTag, "type", "class", "expression", "delegateExpression");
            startTag = setQualifiedAttribute(startTag, "delegateExpression", "${configuredSendTaskDelegate}");
            return element.withTagName("serviceTask").withStartTag(startTag);
        });
    }

    /**
     * 改写配置化的业务规则任务：校验决策表Key，并将 businessRuleTask 转为 serviceTask 绑定 DMN 代理。
     *
     * @throws IllegalArgumentException 当缺少决策表Key时抛出
     */
    private String fixConfiguredBusinessRuleTasks(String bpmnXml) {
        return rewriteConfiguredElements(bpmnXml, "businessRuleTask", "ruleConfig", (element, config) -> {
            String decisionRef = config.path("decisionRef").asText("");
            if (decisionRef.isBlank()) {
                throw new IllegalArgumentException("业务规则任务必须配置决策表Key: " + element.id());
            }
            validateJsonObjectDocument(
                    config.path("inputVariables").asText(""),
                    "业务规则输入变量",
                    element.id());
            String startTag = element.startTag()
                    .replaceFirst("(?i)<(bpmn:)?businessRuleTask\\b", "<$1serviceTask");
            startTag = removeAttributes(startTag, "type", "class", "expression", "delegateExpression");
            startTag = setQualifiedAttribute(startTag, "delegateExpression", "${configuredDmnTaskDelegate}");
            return element.withTagName("serviceTask").withStartTag(startTag);
        });
    }

    /**
     * 改写配置化的调用活动：设置子流程Key、调用类型、业务Key及输入输出参数映射。
     */
    private String fixConfiguredCallActivities(String bpmnXml) {
        return rewriteConfiguredElements(bpmnXml, "callActivity", "callConfig", (element, config) -> {
            String calledElement = config.path("calledElement").asText("");
            if (calledElement.isBlank()) {
                throw new IllegalArgumentException("调用活动必须配置子流程Key: " + element.id());
            }
            String startTag = setAttribute(element.startTag(), "calledElement", calledElement);
            String callActivityType = config.path("callActivityType").asText("bpmn");
            startTag = "cmmn".equalsIgnoreCase(callActivityType)
                    ? setQualifiedAttribute(startTag, "calledElementType", "cmmn")
                    : removeAttributes(startTag, "calledElementType");
            String businessKey = config.path("businessKey").asText("");
            startTag = businessKey.isBlank()
                    ? removeAttributes(startTag, "businessKey")
                    : setQualifiedAttribute(startTag, "businessKey", businessKey);

            String content = removeGeneratedCallMappings(element.content());
            String mappings = callMappings(config.path("inputParameters").asText(""), "in")
                    + callMappings(config.path("outputParameters").asText(""), "out");
            if (!mappings.isEmpty()) {
                content = appendToExtensionElements(content, mappings);
            }
            return element.withStartTag(startTag).withContent(content);
        });
    }

    /**
     * 为配置了超时的接收任务生成中断式定时边界事件。
     *
     * <p>定时器先进入平台超时处理代理，再复用接收任务原有出线。continue 策略会设置
     * 超时变量后继续；error 策略由代理抛出异常并交给 Flowable 作业重试/失败机制处理。</p>
     */
    private String fixConfiguredReceiveTasks(String bpmnXml) {
        if (!bpmnXml.contains("receiveConfig")
                && !bpmnXml.contains("__receive_timeout")) {
            return bpmnXml;
        }
        try {
            Document document = parseXml(bpmnXml);
            List<Element> receiveTasks = elementsByLocalName(document, "receiveTask");
            boolean changed = false;
            for (Element receiveTask : receiveTasks) {
                String receiveTaskId = receiveTask.getAttribute("id");
                if (receiveTaskId.isBlank()) {
                    throw new IllegalArgumentException("接收任务缺少节点ID");
                }
                Element container = (Element) receiveTask.getParentNode();
                changed |= removeGeneratedReceiveTimeout(container, receiveTaskId);

                String configDocument = readPropertyValue(receiveTask, "receiveConfig");
                if (configDocument == null || configDocument.isBlank()) {
                    continue;
                }
                com.fasterxml.jackson.databind.JsonNode config =
                        objectMapper.readTree(configDocument);
                if (!config.path("hasTimeout").asBoolean(false)) {
                    continue;
                }

                int timeout = strictPositiveInteger(config.path("timeout"), receiveTaskId);
                String unit = config.path("timeoutUnit")
                        .asText("MINUTE")
                        .toUpperCase(Locale.ROOT);
                String action = config.path("timeoutAction")
                        .asText("error")
                        .toLowerCase(Locale.ROOT);
                String duration = receiveTimeoutDuration(timeout, unit, receiveTaskId);
                if (!"continue".equals(action) && !"error".equals(action)) {
                    throw new IllegalArgumentException(
                            "接收任务超时处理仅支持 continue 或 error: " + receiveTaskId);
                }

                appendReceiveTimeout(
                        document,
                        container,
                        receiveTask,
                        duration,
                        action);
                changed = true;
            }
            return changed ? writeXml(document) : bpmnXml;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "接收任务超时配置处理失败: " + exception.getMessage(),
                    exception);
        }
    }

    private void appendReceiveTimeout(
            Document document,
            Element container,
            Element receiveTask,
            String duration,
            String action) {
        String receiveTaskId = receiveTask.getAttribute("id");
        String prefix = receiveTask.getPrefix();
        String namespace = receiveTask.getNamespaceURI();
        String boundaryId = receiveTaskId + "__receive_timeout";
        String handlerId = receiveTaskId + "__receive_timeout_handler";
        String boundaryFlowId = receiveTaskId + "__receive_timeout_boundary_flow";
        List<Element> outgoingFlows = directSequenceFlows(container, receiveTaskId);

        Element boundary = createBpmnElement(
                document,
                namespace,
                prefix,
                "boundaryEvent");
        boundary.setAttribute("id", boundaryId);
        boundary.setAttribute("name", "接收任务超时");
        boundary.setAttribute("attachedToRef", receiveTaskId);
        boundary.setAttribute("cancelActivity", "true");
        appendReferenceElement(
                document,
                boundary,
                namespace,
                prefix,
                "outgoing",
                boundaryFlowId);
        Element timerDefinition = createBpmnElement(
                document,
                namespace,
                prefix,
                "timerEventDefinition");
        Element timeDuration = createBpmnElement(
                document,
                namespace,
                prefix,
                "timeDuration");
        timeDuration.setTextContent(duration);
        timerDefinition.appendChild(timeDuration);
        boundary.appendChild(timerDefinition);

        Element handler = createBpmnElement(
                document,
                namespace,
                prefix,
                "serviceTask");
        handler.setAttribute("id", handlerId);
        handler.setAttribute(
                "name",
                "error".equals(action) ? "接收任务超时异常" : "接收任务超时继续");
        handler.setAttributeNS(
                FLOWABLE_NAMESPACE,
                "flowable:delegateExpression",
                "${receiveTaskTimeoutDelegate}");
        appendReceiveTimeoutProperties(
                document,
                handler,
                namespace,
                prefix,
                receiveTaskId,
                action);
        appendReferenceElement(
                document,
                handler,
                namespace,
                prefix,
                "incoming",
                boundaryFlowId);

        List<Element> timeoutFlows = new ArrayList<>();
        for (Element outgoingFlow : outgoingFlows) {
            String originalFlowId = outgoingFlow.getAttribute("id");
            if (originalFlowId.isBlank()) {
                throw new IllegalArgumentException(
                        "接收任务出线缺少ID: " + receiveTaskId);
            }
            String timeoutFlowId =
                    receiveTaskId + "__receive_timeout_flow__" + originalFlowId;
            Element timeoutFlow = (Element) outgoingFlow.cloneNode(true);
            timeoutFlow.setAttribute("id", timeoutFlowId);
            timeoutFlow.setAttribute("sourceRef", handlerId);
            appendReferenceElement(
                    document,
                    handler,
                    namespace,
                    prefix,
                    "outgoing",
                    timeoutFlowId);
            timeoutFlows.add(timeoutFlow);
        }

        Element boundaryFlow = createBpmnElement(
                document,
                namespace,
                prefix,
                "sequenceFlow");
        boundaryFlow.setAttribute("id", boundaryFlowId);
        boundaryFlow.setAttribute("sourceRef", boundaryId);
        boundaryFlow.setAttribute("targetRef", handlerId);

        container.appendChild(boundary);
        container.appendChild(handler);
        container.appendChild(boundaryFlow);
        timeoutFlows.forEach(container::appendChild);
    }

    private void appendReceiveTimeoutProperties(
            Document document,
            Element handler,
            String namespace,
            String prefix,
            String receiveTaskId,
            String action) {
        Element extensionElements = createBpmnElement(
                document,
                namespace,
                prefix,
                "extensionElements");
        Element properties = document.createElementNS(
                FLOWABLE_NAMESPACE,
                "flowable:properties");
        properties.appendChild(flowableProperty(
                document,
                "receiveTaskId",
                receiveTaskId));
        properties.appendChild(flowableProperty(
                document,
                "receiveTimeoutAction",
                action));
        extensionElements.appendChild(properties);
        handler.appendChild(extensionElements);
    }

    private Element flowableProperty(
            Document document,
            String name,
            String value) {
        Element property = document.createElementNS(
                FLOWABLE_NAMESPACE,
                "flowable:property");
        property.setAttribute("name", name);
        property.setAttribute("value", value);
        return property;
    }

    private void appendReferenceElement(
            Document document,
            Element parent,
            String namespace,
            String prefix,
            String localName,
            String value) {
        Element reference = createBpmnElement(
                document,
                namespace,
                prefix,
                localName);
        reference.setTextContent(value);
        parent.appendChild(reference);
    }

    private Element createBpmnElement(
            Document document,
            String namespace,
            String prefix,
            String localName) {
        String qualifiedName = prefix == null || prefix.isBlank()
                ? localName
                : prefix + ":" + localName;
        return document.createElementNS(namespace, qualifiedName);
    }

    private List<Element> directSequenceFlows(
            Element container,
            String sourceRef) {
        List<Element> result = new ArrayList<>();
        NodeList children = container.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element
                    && "sequenceFlow".equals(element.getLocalName())
                    && sourceRef.equals(element.getAttribute("sourceRef"))) {
                result.add(element);
            }
        }
        return result;
    }

    private boolean removeGeneratedReceiveTimeout(
            Element container,
            String receiveTaskId) {
        String generatedPrefix = receiveTaskId + "__receive_timeout";
        List<Node> generated = new ArrayList<>();
        NodeList children = container.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element
                    && element.getAttribute("id").startsWith(generatedPrefix)) {
                generated.add(node);
            }
        }
        generated.forEach(container::removeChild);
        return !generated.isEmpty();
    }

    private String receiveTimeoutDuration(
            int timeout,
            String unit,
            String receiveTaskId) {
        return switch (unit) {
            case "MINUTE" -> "PT" + timeout + "M";
            case "HOUR" -> "PT" + timeout + "H";
            case "DAY" -> "P" + timeout + "D";
            default -> throw new IllegalArgumentException(
                    "接收任务超时单位仅支持 MINUTE、HOUR、DAY: " + receiveTaskId);
        };
    }

    private int strictPositiveInteger(
            com.fasterxml.jackson.databind.JsonNode value,
            String receiveTaskId) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(
                    "接收任务超时时间必须是正整数: " + receiveTaskId);
        }
        int timeout = value.intValue();
        if (timeout < 1) {
            throw new IllegalArgumentException(
                    "接收任务超时时间必须大于0: " + receiveTaskId);
        }
        return timeout;
    }

    private List<Element> elementsByLocalName(
            Document document,
            String localName) {
        List<Element> result = new ArrayList<>();
        NodeList elements = document.getElementsByTagNameNS("*", localName);
        for (int index = 0; index < elements.getLength(); index++) {
            result.add((Element) elements.item(index));
        }
        return result;
    }

    private String readPropertyValue(
            Element element,
            String propertyName) {
        NodeList properties = element.getElementsByTagNameNS(
                FLOWABLE_NAMESPACE,
                "property");
        for (int index = 0; index < properties.getLength(); index++) {
            Element property = (Element) properties.item(index);
            if (propertyName.equals(property.getAttribute("name"))) {
                return property.getAttribute("value");
            }
        }
        return null;
    }

    private Document parseXml(String bpmnXml) throws Exception {
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
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(bpmnXml)));
    }

    private String writeXml(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private void validateJsonObjectDocument(
            String document,
            String label,
            String elementId) {
        if (document == null || document.isBlank()) {
            return;
        }
        try {
            if (!objectMapper.readTree(document).isObject()) {
                throw new IllegalArgumentException(
                        label + "必须是 JSON 对象: " + elementId);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    label + "不是合法 JSON: " + elementId,
                    exception);
        }
    }

    /**
     * 通用配置化元素改写器。
     * <p>
     * 按标签名匹配所有元素，读取其扩展属性中的配置 JSON，交由 rewriter 改写后回填。
     * 无配置或解析异常时保留原元素；配置非法（IllegalArgumentException）则向上抛出。
     *
     * @param bpmnXml     BPMN XML
     * @param tagName     目标标签名（如 serviceTask）
     * @param propertyName 配置属性名（如 restConfig）
     * @param rewriter    元素改写回调
     * @return 改写后的 BPMN XML
     */
    private String rewriteConfiguredElements(
            String bpmnXml,
            String tagName,
            String propertyName,
            ConfiguredElementRewriter rewriter) {
        Pattern pattern = Pattern.compile(
                "(?i)<(bpmn:)?" + tagName + "\\b([^>]*)>([\\s\\S]*?)</\\1" + tagName + ">",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(bpmnXml);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1) == null ? "" : matcher.group(1);
            String startTag = "<" + prefix + tagName + matcher.group(2) + ">";
            String content = matcher.group(3);
            String configJson = readPropertyValue(content, propertyName);
            if (configJson == null || configJson.isBlank()) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            try {
                com.fasterxml.jackson.databind.JsonNode config = objectMapper.readTree(configJson);
                ConfiguredElement configuredElement = new ConfiguredElement(
                        prefix,
                        tagName,
                        startTag,
                        content,
                        attributeValue(startTag, "id"));
                ConfiguredElement rewritten = rewriter.rewrite(configuredElement, config);
                matcher.appendReplacement(result, Matcher.quoteReplacement(rewritten.xml()));
            } catch (IllegalArgumentException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalArgumentException(
                        "节点配置解析失败: " + attributeValue(startTag, "id") + ", " + exception.getMessage(),
                        exception);
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String readPropertyValue(String content, String propertyName) {
        Pattern nameFirst = Pattern.compile(
                "(?i)<flowable:property\\b[^>]*name=\"" + Pattern.quote(propertyName)
                        + "\"[^>]*value=\"([^\"]*)\"");
        Matcher matcher = nameFirst.matcher(content);
        if (matcher.find()) {
            return decodeXml(matcher.group(1));
        }
        Pattern valueFirst = Pattern.compile(
                "(?i)<flowable:property\\b[^>]*value=\"([^\"]*)\"[^>]*name=\""
                        + Pattern.quote(propertyName) + "\"");
        matcher = valueFirst.matcher(content);
        return matcher.find() ? decodeXml(matcher.group(1)) : null;
    }

    private String removeAttributes(String startTag, String... names) {
        String result = startTag;
        for (String name : names) {
            result = result.replaceAll(
                    "(?i)\\s+(?:flowable:)?" + Pattern.quote(name) + "=\"[^\"]*\"",
                    "");
        }
        return result;
    }

    private String setQualifiedAttribute(String startTag, String name, String value) {
        return setAttributeInternal(startTag, "flowable:" + name, value);
    }

    private String setAttribute(String startTag, String name, String value) {
        return setAttributeInternal(startTag, name, value);
    }

    private String setAttributeInternal(String startTag, String qualifiedName, String value) {
        String result = startTag.replaceAll(
                "(?i)\\s+" + Pattern.quote(qualifiedName) + "=\"[^\"]*\"",
                "");
        int closingBracket = result.lastIndexOf('>');
        if (closingBracket < 0) {
            return result;
        }
        return result.substring(0, closingBracket)
                + " "
                + qualifiedName
                + "=\""
                + escapeXml(value)
                + "\">";
    }

    private String attributeValue(String startTag, String name) {
        Matcher matcher = Pattern.compile(
                "(?i)\\b" + Pattern.quote(name) + "=\"([^\"]*)\"")
                .matcher(startTag);
        return matcher.find() ? decodeXml(matcher.group(1)) : "";
    }

    private String callMappings(String json, String direction) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            com.fasterxml.jackson.databind.JsonNode mappings = objectMapper.readTree(json);
            if (!mappings.isObject()) {
                throw new IllegalArgumentException("调用活动参数必须是 JSON 对象");
            }
            StringBuilder xml = new StringBuilder();
            Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = mappings.fields();
            while (fields.hasNext()) {
                Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> field = fields.next();
                String source = field.getValue().asText("");
                if (source.isBlank()) {
                    continue;
                }
                String sourceAttribute = source.contains("${")
                        ? "sourceExpression"
                        : "source";
                xml.append("<flowable:")
                        .append(direction)
                        .append(' ')
                        .append(sourceAttribute)
                        .append("=\"")
                        .append(escapeXml(source))
                        .append("\" target=\"")
                        .append(escapeXml(field.getKey()))
                        .append("\" />");
            }
            return xml.toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException("调用活动参数映射 JSON 无效: " + exception.getMessage(), exception);
        }
    }

    private String removeGeneratedCallMappings(String content) {
        return content.replaceAll(
                "(?i)<flowable:(?:in|out)\\b[^>]*/>",
                "");
    }

    private String appendToExtensionElements(String content, String extensionXml) {
        if (content.matches("(?is).*?</bpmn:extensionElements>.*")) {
            return content.replaceFirst(
                    "(?i)</bpmn:extensionElements>",
                    Matcher.quoteReplacement(extensionXml + "</bpmn:extensionElements>"));
        }
        if (content.matches("(?is).*?</extensionElements>.*")) {
            return content.replaceFirst(
                    "(?i)</extensionElements>",
                    Matcher.quoteReplacement(extensionXml + "</extensionElements>"));
        }
        return "<bpmn:extensionElements>" + extensionXml + "</bpmn:extensionElements>" + content;
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    @FunctionalInterface
    private interface ConfiguredElementRewriter {
        ConfiguredElement rewrite(
                ConfiguredElement element,
                com.fasterxml.jackson.databind.JsonNode config);
    }

    private record ConfiguredElement(
            String prefix,
            String tagName,
            String startTag,
            String content,
            String id) {
        private ConfiguredElement withTagName(String value) {
            return new ConfiguredElement(prefix, value, startTag, content, id);
        }

        private ConfiguredElement withStartTag(String value) {
            return new ConfiguredElement(prefix, tagName, value, content, id);
        }

        private ConfiguredElement withContent(String value) {
            return new ConfiguredElement(prefix, tagName, startTag, value, id);
        }

        private String xml() {
            return startTag + content + "</" + prefix + tagName + ">";
        }
    }

    private String removeDuplicateCamundaAssignments(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll(
                "(<userTask[^>]*?flowable:assignee=\"[^\"]*\"[^>]*?)\\s+camunda:assignee=\"[^\"]*\"",
                "$1");
        result = result.replaceAll(
                "(<userTask[^>]*?)\\s+camunda:assignee=\"[^\"]*\"([^>]*?flowable:assignee=\"[^\"]*\"[^>]*)",
                "$1$2");
        result = result.replaceAll(
                "(<userTask[^>]*?flowable:candidateGroups=\"[^\"]*\"[^>]*?)\\s+camunda:candidateGroups=\"[^\"]*\"",
                "$1");
        result = result.replaceAll(
                "(<userTask[^>]*?)\\s+camunda:candidateGroups=\"[^\"]*\"([^>]*?flowable:candidateGroups=\"[^\"]*\"[^>]*)",
                "$1$2");
        result = result.replaceAll(
                "(<userTask[^>]*?flowable:candidateUsers=\"[^\"]*\"[^>]*?)\\s+camunda:candidateUsers=\"[^\"]*\"",
                "$1");
        result = result.replaceAll(
                "(<userTask[^>]*?)\\s+camunda:candidateUsers=\"[^\"]*\"([^>]*?flowable:candidateUsers=\"[^\"]*\"[^>]*)",
                "$1$2");
        return result;
    }

    private String convertCamundaAssignments(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll("camunda:candidateGroups=\"([^\"]*)\"", "flowable:candidateGroups=\"$1\"");
        result = result.replaceAll("camunda:candidateUsers=\"([^\"]*)\"", "flowable:candidateUsers=\"$1\"");
        result = result.replaceAll("camunda:assignee=\"([^\"]*)\"", "flowable:assignee=\"$1\"");
        return result;
    }

    private String convertCamundaProperties(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll("(?i)<camunda:Properties", "<flowable:Properties");
        result = result.replaceAll("(?i)</camunda:Properties>", "</flowable:Properties>");
        result = result.replaceAll("(?i)<camunda:Property", "<flowable:Property");
        result = result.replaceAll("(?i)</camunda:Property>", "</flowable:Property>");
        return result;
    }

    private String removeCamundaElements(String bpmnXml) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)<camunda:(?!properties|property)[^>]*>[\\s\\S]*?</camunda:[^>]*>",
                java.util.regex.Pattern.DOTALL);
        String result = bpmnXml;
        for (int i = 0; i < 10; i++) {
            java.util.regex.Matcher matcher = pattern.matcher(result);
            if (!matcher.find()) {
                break;
            }
            result = matcher.replaceAll("");
        }
        return result;
    }

    private String convertBareFlowableAttributes(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll("(?<!flowable:)candidateGroups=\"([^\"]*)\"", "flowable:candidateGroups=\"$1\"");
        result = result.replaceAll("(?<!flowable:)candidateUsers=\"([^\"]*)\"", "flowable:candidateUsers=\"$1\"");
        result = result.replaceAll("(?<!flowable:)\\sassignee=\"([^\"]*)\"", " flowable:assignee=\"$1\"");
        return result;
    }

    private String convertMultiInstanceAttributes(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll(
                "(?i)(<multiInstanceLoopCharacteristics[^>]*?)(?<!flowable:)collection=\"([^\"]*)\"",
                "$1flowable:collection=\"$2\"");
        result = result.replaceAll(
                "(?i)(<multiInstanceLoopCharacteristics[^>]*?)(?<!flowable:)elementVariable=\"([^\"]*)\"",
                "$1flowable:elementVariable=\"$2\"");
        return result;
    }

    private String ensureFlowableNamespace(String bpmnXml) {
        if (bpmnXml.contains("xmlns:flowable")) {
            return bpmnXml;
        }
        return bpmnXml.replace(
                "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\"");
    }

    private String useProcessKey(String bpmnXml, String processKey) {
        String result = bpmnXml.replaceAll(
                "<((?:[A-Za-z_][\\w.-]*:)?process)\\s+id=\"[^\"]+\"",
                "<$1 id=\"" + processKey + "\"");
        return result.replaceAll(
                "(<bpmndi:BPMNPlane[^>]*\\s)bpmnElement=\"[^\"]+\"",
                "$1bpmnElement=\"" + processKey + "\"");
    }

    private String removeInvalidMultiInstanceConfig(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll(
                "(?i)<bpmn:multiInstanceLoopCharacteristics\\s+isSequential=\"(?:true|false)\"\\s*/>",
                "");
        result = result.replaceAll("(?i)<bpmn:multiInstanceLoopCharacteristics\\s*/>", "");

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)<bpmn:multiInstanceLoopCharacteristics[^>]*?>[\\s\\S]*?</bpmn:multiInstanceLoopCharacteristics>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group();
            boolean valid = tag.toLowerCase().contains("collection=")
                    || tag.toLowerCase().contains("flowable:collection=")
                    || tag.toLowerCase().contains("<bpmn:loopcardinality")
                    || tag.toLowerCase().contains("<bpmn:loopdatainputref");
            if (!valid) {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String fixMultiInstanceAssignee(String bpmnXml) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)<(bpmn:)?userTask\\b([^>]*)>([\\s\\S]*?)</\\1userTask>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(bpmnXml);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String fullTag = matcher.group(0);
            String startTag = fullTag.substring(0, fullTag.indexOf('>') + 1);
            String content = matcher.group(3);
            if (!content.toLowerCase().contains("multiinstanceloopcharacteristics")) {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fullTag));
                continue;
            }

            java.util.regex.Matcher evMatcher = java.util.regex.Pattern
                    .compile("(?i)(?:flowable:)?elementVariable=\"([^\"]*)\"")
                    .matcher(content);
            String elementVar = evMatcher.find() ? evMatcher.group(1) : "assignee";
            String newStartTag = startTag;
            if (!newStartTag.toLowerCase().contains("flowable:assignee=")) {
                newStartTag = newStartTag.replace(">", " flowable:assignee=\"${" + elementVar + "}\">");
            }

            String prefix = matcher.group(1) != null ? matcher.group(1) : "";
            String newFullTag = newStartTag + content + "</" + prefix + "userTask>";
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(newFullTag));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String fixScriptTasks(String bpmnXml) {
        return rewriteConfiguredElements(
                bpmnXml,
                "scriptTask",
                "scriptConfig",
                (element, config) -> {
                    String script = config.path("script").asText("")
                            .replaceAll("(?i)<script[^>]*>", "")
                            .replaceAll("(?i)</script>", "")
                            .trim();
                    if (script.isEmpty()) {
                        throw new IllegalArgumentException(
                                "脚本任务必须配置脚本内容: " + element.id());
                    }
                    String scriptFormat = config.path("scriptFormat")
                            .asText("")
                            .trim();
                    if (scriptFormat.isEmpty()) {
                        throw new IllegalArgumentException(
                                "脚本任务必须配置脚本类型: " + element.id());
                    }
                    if (!"groovy".equalsIgnoreCase(scriptFormat)) {
                        throw new IllegalArgumentException(
                                "脚本任务当前仅支持 Groovy: " + element.id());
                    }
                    String newContent = element.content().replaceAll(
                            "(?is)<(?:bpmn:)?script\\b[^>]*>.*?</(?:bpmn:)?script>",
                            "");
                    String startTag = element.startTag()
                            .replaceFirst(
                                    "(?i)<(bpmn:)?scriptTask\\b",
                                    "<$1serviceTask");
                    startTag = removeAttributes(
                            startTag,
                            "type",
                            "class",
                            "expression",
                            "delegateExpression",
                            "scriptFormat",
                            "resultVariable",
                            "autoStoreVariables");
                    startTag = setQualifiedAttribute(
                            startTag,
                            "delegateExpression",
                            "${configuredScriptTaskDelegate}");
                    return element
                            .withTagName("serviceTask")
                            .withStartTag(startTag)
                            .withContent(newContent);
                });
    }

    private String processSkipNodeTasks(String bpmnXml) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<bpmn:userTask([^>]*)>(.*?)</bpmn:userTask>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(bpmnXml);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String content = matcher.group(2);
            if (content.contains("name=\"skipNode\" value=\"true\"") && !attrs.contains("flowable:skipExpression")) {
                attrs += " flowable:skipExpression=\"${skipNodeEnabled}\"";
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    "<bpmn:userTask" + attrs + ">" + content + "</bpmn:userTask>"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 迁移旧的 approved 布尔条件表达式为字符串比较。
     *
     * <p>历史流程的网关条件写的是 {@code ${approved == true}} / {@code ${approved == false}}，
     * 但 approved 变量已统一为字符串 "approve"/"reject"，布尔比较会导致条件永远不成立。
     * 发布时把 {@code approved == true} 改为 {@code approved == 'approve'}，
     * {@code approved == false} 改为 {@code approved == 'reject'}（兼容 == 与 !=）。</p>
     */
    private String migrateApprovedExpressions(String bpmnXml) {
        String result = bpmnXml;
        // approved == true  →  approved == 'approve'
        result = result.replaceAll("approved\\s*==\\s*true\\b", "approved == 'approve'");
        // true == approved  →  'approve' == approved
        result = result.replaceAll("\\btrue\\s*==\\s*approved", "'approve' == approved");
        // approved == false  →  approved == 'reject'
        result = result.replaceAll("approved\\s*==\\s*false\\b", "approved == 'reject'");
        // false == approved  →  'reject' == approved
        result = result.replaceAll("\\bfalse\\s*==\\s*approved", "'reject' == approved");
        // approved != true  →  approved != 'approve'
        result = result.replaceAll("approved\\s*!=\\s*true\\b", "approved != 'approve'");
        // approved != false  →  approved != 'reject'
        result = result.replaceAll("approved\\s*!=\\s*false\\b", "approved != 'reject'");
        return result;
    }

    private String resolveBpmnIdConflicts(String bpmnXml, String processKey) {
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("id=\"([^\"]+)\"");
        java.util.regex.Matcher idMatcher = idPattern.matcher(bpmnXml);
        java.util.Set<String> allIds = new java.util.HashSet<>();
        while (idMatcher.find()) {
            allIds.add(idMatcher.group(1));
        }

        java.util.regex.Matcher processIdMatcher = java.util.regex.Pattern
                .compile("<(?:(?:[A-Za-z_][\\w.-]*):)?process\\s+id=\"([^\"]+)\"")
                .matcher(bpmnXml);
        String currentProcessId = processIdMatcher.find() ? processIdMatcher.group(1) : null;
        boolean hasConflict = allIds.stream().anyMatch(id -> id.equals(processKey) && !id.equals(currentProcessId));
        if (!hasConflict) {
            return bpmnXml;
        }

        String newId = processKey + "_" + System.currentTimeMillis();
        while (allIds.contains(newId)) {
            newId = processKey + "_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
        }

        String result = bpmnXml;
        result = result.replaceAll("(id=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        result = result.replaceAll("(sourceRef=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        result = result.replaceAll("(targetRef=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        result = result.replaceAll("(bpmnElement=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        result = result.replaceAll("(default=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        return result;
    }

    private String decodeXml(String value) {
        return value.replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&lt;", "<")
                .replace("&#60;", "<")
                .replace("&gt;", ">")
                .replace("&#62;", ">")
                .replace("&amp;", "&")
                .replace("&#38;", "&");
    }
}
