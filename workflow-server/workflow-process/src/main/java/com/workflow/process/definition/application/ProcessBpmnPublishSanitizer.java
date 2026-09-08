package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.process.sla.calendar.application.WorkCalendarResolutionSnapshot;
import com.workflow.process.sla.calendar.application.WorkCalendarService;
import com.workflow.process.sla.calendar.application.WorkCalendarSnapshot;
import com.workflow.process.sla.policy.application.TaskSlaPolicyService;
import com.workflow.process.sla.policy.application.TaskSlaPolicySnapshot;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidationRequest;
import com.workflow.contracts.process.assignment.spi.PersonResolverConfigurationValidator;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser.LegacyAssignment;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver;
import com.workflow.process.instance.application.WorkflowReservedVariables;
import com.workflow.process.task.application.nextapproval.NextApproverSelectionNormalizer;
import com.workflow.process.task.application.nextapproval.NextApproverSelectionNormalizer.NormalizedSelection;
import com.workflow.process.task.infrastructure.MultiInstanceVariableNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发布前 BPMN 归一化处理器
 * 负责在流程发布前对 BPMN XML 进行清洗、转换与补全：
 * 包括强制节点同步执行、Camunda 属性转 Flowable 属性、多实例配置修正、跳过节点表达式注入、
 * 配置化任务（服务/发送/业务规则/调用活动/脚本）改写、ID 冲突消解等，确保 XML 可被 Flowable 正确部署执行。
 */
@Slf4j
@Service
public class ProcessBpmnPublishSanitizer {

    private static final String BPMN_NAMESPACE =
            "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String FLOWABLE_NAMESPACE = "http://flowable.org/bpmn";
    /** JSON 序列化工具，用于解析节点配置 JSON */
    private final ObjectMapper objectMapper;
    private final TaskSlaPolicyService taskSlaPolicyService;
    private final WorkCalendarService workCalendarService;

    @Autowired
    private PersonResolverRuntimeService personResolverRuntimeService;

    /** 解析器自身拥有 extraParams 语义，发布器只负责路由校验请求。 */
    @Autowired(required = false)
    private List<PersonResolverConfigurationValidator>
            personResolverConfigurationValidators = List.of();

    @Autowired
    public ProcessBpmnPublishSanitizer(
            ObjectMapper objectMapper,
            TaskSlaPolicyService taskSlaPolicyService,
            WorkCalendarService workCalendarService) {
        this.objectMapper = objectMapper;
        this.taskSlaPolicyService = taskSlaPolicyService;
        this.workCalendarService = workCalendarService;
    }

    /**
     * 兼容不涉及 SLA 的轻量单元测试。
     */
    public ProcessBpmnPublishSanitizer(ObjectMapper objectMapper) {
        this(objectMapper, null, null);
    }

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
        return sanitize(bpmnXml, processKey, null);
    }

    /**
     * 带流程配置身份执行发布净化，使人员解析器可校验流程绑定实体。
     *
     * @param processConfigId 流程配置 ID；轻量测试或无绑定上下文时可为空
     */
    public String sanitize(
            String bpmnXml,
            String processKey,
            String processConfigId) {
        // 发布是最后一道权威边界，确保存量草稿也无法把异步节点部署到运行时。
        String result = ProcessBpmnSynchronousExecutionNormalizer.normalize(
                bpmnXml);
        result = normalizeBpmnElementPrefixes(result);

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
        result = normalizeProcessIdentity(result, processKey);
        result = normalizeDataObjectNames(result);
        result = removeInvalidMultiInstanceConfig(result);
        result = fixMultiInstanceAssignee(result);
        result = fixExplicitCcTasks(result);
        result = fixConfiguredServiceTasks(result);
        result = fixConfiguredSendTasks(result);
        result = fixConfiguredBusinessRuleTasks(result);
        result = fixConfiguredCallActivities(result);
        result = fixConfiguredReceiveTasks(result);
        result = fixConfiguredUserTaskSlas(result);
        result = validateNextApproverSelections(result, processConfigId);
        result = installEntryDynamicResolverCollectionHandlers(result);
        result = fixScriptTasks(result);
        validateProtectedMultiInstanceVariables(result);
        BpmnExecutableContentValidator.validate(result);

        return result;
    }

    /**
     * 将标准 BPMN 命名空间的元素前缀收敛为 {@code bpmn:}。
     *
     * <p>发布器需要兼容外部工具常用的 {@code bpmn2:} 乃至默认命名空间。
     * 后续历史字符串净化链以 {@code bpmn:} 为规范前缀；若它已被业务扩展
     * 命名空间占用，发布必须明确拒绝，不能覆盖绑定后静默改变扩展语义。</p>
     */
    private String normalizeBpmnElementPrefixes(String bpmnXml) {
        try {
            Document document = parseXml(bpmnXml);
            String canonicalPrefix = selectCanonicalBpmnPrefix(document);
            NodeList elements = document.getElementsByTagNameNS(
                    BPMN_NAMESPACE, "*");
            boolean changed = false;
            for (int index = 0; index < elements.getLength(); index++) {
                Element element = (Element) elements.item(index);
                if (!canonicalPrefix.equals(element.getPrefix())) {
                    element.setPrefix(canonicalPrefix);
                    changed = true;
                }
            }
            String boundNamespace = document.getDocumentElement()
                    .lookupNamespaceURI(canonicalPrefix);
            if (!BPMN_NAMESPACE.equals(boundNamespace)) {
                changed = true;
            }
            if (!changed) {
                return bpmnXml;
            }
            document.getDocumentElement().setAttributeNS(
                    XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                    "xmlns:" + canonicalPrefix,
                    BPMN_NAMESPACE);
            return writeXml(document);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "BPMN_NAMESPACE_NORMALIZATION_INVALID: 无法归一化 BPMN 命名空间",
                    exception);
        }
    }

    /** 确认规范前缀未被扩展命名空间占用，避免发布净化改变 XML 语义。 */
    private String selectCanonicalBpmnPrefix(Document document) {
        if (isPrefixBoundToOtherNamespace(document, "bpmn")) {
            throw new IllegalArgumentException(
                    "BPMN_PREFIX_CONFLICT: bpmn 前缀已绑定到非 BPMN 命名空间");
        }
        return "bpmn";
    }

    private boolean isPrefixBoundToOtherNamespace(
            Document document,
            String prefix) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (prefix.equals(element.getPrefix())
                    && !BPMN_NAMESPACE.equals(element.getNamespaceURI())) {
                return true;
            }
            NamedNodeMap attributes = element.getAttributes();
            for (int attributeIndex = 0;
                    attributeIndex < attributes.getLength();
                    attributeIndex++) {
                Node attribute = attributes.item(attributeIndex);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(
                        attribute.getNamespaceURI())
                        && prefix.equals(attribute.getLocalName())
                        && !BPMN_NAMESPACE.equals(attribute.getNodeValue())) {
                    return true;
                }
                if (prefix.equals(attribute.getPrefix())
                        && !BPMN_NAMESPACE.equals(
                        attribute.getNamespaceURI())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 为必须在节点进入时取最新人员的多实例解析器写入 collection handler。
     *
     * <p>该 handler 在引擎真正读取 collection 时解析人员，这一时点早于
     * ACTIVITY_STARTED。相对职务由此读取当前任职，实体用户字段则读取此前
     * 任一表单刚保存的权威实体记录；两者都能在创建 0 个实例前失败关闭。</p>
     */
    private String installEntryDynamicResolverCollectionHandlers(
            String bpmnXml) {
        try {
            Document document = parseXml(bpmnXml);
            Map<String, Element> allElements = new LinkedHashMap<>();
            NodeList everyElement = document.getElementsByTagNameNS("*", "*");
            for (int index = 0; index < everyElement.getLength(); index++) {
                Element element = (Element) everyElement.item(index);
                if (StringUtils.hasText(element.getAttribute("id"))) {
                    allElements.putIfAbsent(
                            element.getAttribute("id"), element);
                }
            }
            Map<String, PublishedAssignmentNode> userTasks =
                    new LinkedHashMap<>();
            for (Element element : elementsByLocalName(document, "userTask")) {
                String id = element.getAttribute("id");
                if (StringUtils.hasText(id)) {
                    userTasks.put(id, new PublishedAssignmentNode(
                            id,
                            element,
                            readMergedAssignmentConfig(element),
                            hasMultiInstanceLoop(element)));
                }
            }

            boolean changed = false;
            for (PublishedAssignmentNode current : userTasks.values()) {
                if (!current.multiInstance()) {
                    continue;
                }
                PublishedAssignmentNode terminal =
                        NodeAssignmentReferenceResolver
                                .isEffectiveNodeReference(
                                        current.assigneeConfig(),
                                        current.multiInstance())
                                ? resolvePublishedReference(
                                current, userTasks, allElements)
                                : current;
                if (!usesEntryDynamicResolver(
                        terminal.assigneeConfig(),
                        terminal.multiInstance())) {
                    continue;
                }
                Element loop = firstDescendant(
                        current.element(),
                        "multiInstanceLoopCharacteristics");
                if (loop == null) {
                    throw nextApproverConfigError(
                            current.id(), "多实例循环配置缺失");
                }
                installCollectionHandler(
                        document, current.element(), loop);
                changed = true;
            }
            return changed ? writeXml(document) : bpmnXml;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "动态人员解析器多实例 handler 无法写入 BPMN",
                    exception);
        }
    }

    /** 仅这些内置解析器要求等到节点进入时读取可变的权威业务状态。 */
    private boolean usesEntryDynamicResolver(
            Map<String, Object> config,
            boolean multiInstanceSource) {
        String resolverCode = configuredPersonResolverCode(
                config, multiInstanceSource);
        return com.workflow.process.assignment.relative
                .RelativeOrgPositionConfig.RESOLVER_CODE
                .equals(resolverCode)
                || com.workflow.process.assignment.entity
                .EntityUserReferenceFieldConfig.RESOLVER_CODE
                .equals(resolverCode);
    }

    private String configuredPersonResolverCode(
            Map<String, Object> config,
            boolean multiInstanceSource) {
        return LegacyMultiInstanceAssignmentParser
                .effectiveResolver(config, multiInstanceSource)
                .resolverCode();
    }

    /** 判断基础办理人配置是否声明了受控人员解析器。 */
    private boolean usesPersonResolver(
            Map<String, Object> config,
            boolean multiInstanceSource) {
        return StringUtils.hasText(configuredPersonResolverCode(
                config, multiInstanceSource));
    }

    private Element firstDescendant(Element parent, String localName) {
        NodeList elements = parent.getElementsByTagNameNS("*", localName);
        return elements.getLength() == 0
                ? null : (Element) elements.item(0);
    }

    private void installCollectionHandler(
            Document document,
            Element userTask,
            Element loop) {
        String rawCollection = loop.getAttributeNS(
                FLOWABLE_NAMESPACE, "collection");
        if (!StringUtils.hasText(rawCollection)) {
            rawCollection = loop.getAttribute("flowable:collection");
        }
        if (!StringUtils.hasText(rawCollection)) {
            rawCollection = loop.getAttribute("collection");
        }
        String preservedCollectionVariable = readFlowableProperty(
                userTask,
                MultiInstanceVariableNames.ENTRY_DYNAMIC_COLLECTION_PROPERTY);
        if (MultiInstanceVariableNames.ENTRY_DYNAMIC_COLLECTION_LITERAL
                .equals(rawCollection)
                && StringUtils.hasText(preservedCollectionVariable)) {
            // 二次发布净化时 loop 已是安全字面量，必须保留首轮记录的
            // 业务变量名，不能把内部 seed 反写成审计/覆盖变量。
            rawCollection = preservedCollectionVariable;
        }
        String collectionVariable = simpleCollectionVariable(
                rawCollection);
        if (!StringUtils.hasText(collectionVariable)) {
            throw nextApproverConfigError(
                    userTask.getAttribute("id"),
                    "节点进入期动态解析器的多实例 collection 必须是简单流程变量");
        }
        upsertFlowableProperty(
                document,
                userTask,
                MultiInstanceVariableNames.ENTRY_DYNAMIC_COLLECTION_PROPERTY,
                collectionVariable);
        // Flowable 7.2 会先求值 collection、随后才调用 handler。改成无需
        // 变量的安全字面量，使主流程、嵌入流程和 CallActivity 子流程都能进入
        // handler；原业务变量名已保存到节点扩展属性供覆盖和审计使用。
        loop.removeAttribute("collection");
        loop.setAttributeNS(
                FLOWABLE_NAMESPACE,
                "flowable:collection",
                MultiInstanceVariableNames.ENTRY_DYNAMIC_COLLECTION_LITERAL);

        Element extensionElements = null;
        for (Node child = loop.getFirstChild();
                child != null;
                child = child.getNextSibling()) {
            if (child instanceof Element element
                    && "extensionElements".equals(element.getLocalName())) {
                extensionElements = element;
                break;
            }
        }
        if (extensionElements == null) {
            extensionElements = document.createElementNS(
                    "http://www.omg.org/spec/BPMN/20100524/MODEL",
                    "extensionElements");
            loop.insertBefore(extensionElements, loop.getFirstChild());
        }
        List<Node> previousHandlers = new ArrayList<>();
        for (Node child = extensionElements.getFirstChild();
                child != null;
                child = child.getNextSibling()) {
            if (child instanceof Element element
                    && FLOWABLE_NAMESPACE.equals(element.getNamespaceURI())
                    && "collection".equals(element.getLocalName())) {
                previousHandlers.add(child);
            }
        }
        previousHandlers.forEach(extensionElements::removeChild);
        Element handler = document.createElementNS(
                FLOWABLE_NAMESPACE, "flowable:collection");
        handler.setAttributeNS(
                FLOWABLE_NAMESPACE,
                "flowable:delegateExpression",
                "${relativeOrgPositionCollectionHandler}");
        extensionElements.appendChild(handler);
    }

    /** 在用户任务扩展属性中保存平台生成的动态 collection 契约。 */
    private void upsertFlowableProperty(
            Document document,
            Element task,
            String name,
            String value) {
        Element extensionElements = null;
        for (Node child = task.getFirstChild();
                child != null;
                child = child.getNextSibling()) {
            if (child instanceof Element element
                    && "extensionElements".equals(
                    element.getLocalName())) {
                extensionElements = element;
                break;
            }
        }
        if (extensionElements == null) {
            extensionElements = createBpmnElement(
                    document,
                    task.getNamespaceURI(),
                    task.getPrefix(),
                    "extensionElements");
            task.insertBefore(extensionElements, task.getFirstChild());
        }
        Element properties = null;
        for (Node child = extensionElements.getFirstChild();
                child != null;
                child = child.getNextSibling()) {
            if (child instanceof Element element
                    && FLOWABLE_NAMESPACE.equals(
                    element.getNamespaceURI())
                    && "properties".equals(element.getLocalName())) {
                properties = element;
                break;
            }
        }
        if (properties == null) {
            properties = document.createElementNS(
                    FLOWABLE_NAMESPACE, "flowable:properties");
            extensionElements.appendChild(properties);
        }
        for (Node child = properties.getFirstChild();
                child != null;
                child = child.getNextSibling()) {
            if (child instanceof Element property
                    && FLOWABLE_NAMESPACE.equals(
                    property.getNamespaceURI())
                    && "property".equals(property.getLocalName())
                    && name.equals(property.getAttribute("name"))) {
                property.setAttribute("value", value);
                return;
            }
        }
        Element property = document.createElementNS(
                FLOWABLE_NAMESPACE, "flowable:property");
        property.setAttribute("name", name);
        property.setAttribute("value", value);
        properties.appendChild(property);
    }

    private String readFlowableProperty(
            Element task,
            String name) {
        NodeList properties = task.getElementsByTagNameNS(
                FLOWABLE_NAMESPACE, "property");
        for (int index = 0; index < properties.getLength(); index++) {
            Element property = (Element) properties.item(index);
            if (name.equals(property.getAttribute("name"))) {
                return property.getAttribute("value");
            }
        }
        return null;
    }

    private String simpleCollectionVariable(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if ((value.startsWith("${") || value.startsWith("#{"))
                && value.endsWith("}")) {
            value = value.substring(2, value.length() - 1).trim();
        }
        return value.matches("[A-Za-z_][A-Za-z0-9_]*")
                ? value : null;
    }

    /**
     * 发布时校验下一节点审批人展示/修改配置，防止无效策略进入不可变部署。
     */
    private String validateNextApproverSelections(
            String bpmnXml,
            String processConfigId) {
        String validated = rewriteConfiguredElements(
                bpmnXml,
                "userTask",
                "assigneeConfig",
                (element, assigneeConfig) -> {
                    assigneeConfig = mergeDeployedAssignmentConfig(
                            element, assigneeConfig);
                    validateAssignmentConfigVersion(
                            element.id(), assigneeConfig);
                    boolean multiInstance = element.content()
                            .toLowerCase(Locale.ROOT)
                            .contains("multiinstanceloopcharacteristics");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> baseAssignment =
                            objectMapper.convertValue(
                                    assigneeConfig, Map.class);
                    if (!multiInstance
                            && usesPersonResolver(
                            baseAssignment, false)) {
                        // 普通任务即使隐藏“下一审批人”区域，也会在运行时使用
                        // 基础 resolver；所有受控解析器都必须经过发布校验。
                        validateNodeAssignmentResolver(
                                element.id(), assigneeConfig, false,
                                processConfigId);
                    }
                    var selection = assigneeConfig.path(
                            "nextApproverSelection");
                    if (selection.isMissingNode() || selection.isNull()) {
                        // 隐藏的 v2 多实例没有前序人工覆盖入口，基础配置必须
                        // 能直接生成参与人，防止空 collection 跳过审批。
                        if (multiInstance) {
                            validateEnumerableNodeAssignment(
                                    element, assigneeConfig,
                                    processConfigId);
                        }
                        return element;
                    }
                    if (!selection.isObject()) {
                        throw nextApproverConfigError(
                                element.id(),
                                "nextApproverSelection 必须是对象");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> selectionMap =
                            objectMapper.convertValue(
                                    selection, Map.class);
                    NormalizedSelection normalized;
                    try {
                        normalized = NextApproverSelectionNormalizer
                                .normalize(selectionMap);
                    } catch (IllegalArgumentException exception) {
                        throw nextApproverConfigError(
                                element.id(), exception.getMessage());
                    }
                    int version = normalized.version();
                    if (version != 1) {
                        throw nextApproverConfigError(
                                element.id(),
                                "不支持的配置版本: " + version);
                    }
                    boolean visible = normalized.visible();
                    boolean editable = normalized.editable();
                    if (editable && !visible) {
                        throw nextApproverConfigError(
                                element.id(),
                                "editable=true 时 visible 必须为 true");
                    }
                    if (normalized.invalidSourceShape()) {
                        throw nextApproverConfigError(
                                element.id(),
                                "source 必须是对象、字符串或范围数组");
                    }
                    String type = normalized.sourceType() == null
                            ? ""
                            : normalized.sourceType()
                            .trim()
                            .toUpperCase(Locale.ROOT);
                    if (type.isBlank()) {
                        if (visible || editable) {
                            throw nextApproverConfigError(
                                    element.id(),
                                    "展示或修改下一审批人时必须配置 source");
                        }
                        if (multiInstance) {
                            validateEnumerableNodeAssignment(
                                    element, assigneeConfig,
                                    processConfigId);
                        }
                        return element;
                    }
                    // 可编辑节点若有独立的受控范围/解析器，可以没有默认
                    // 人员，等待前序人工覆盖；NODE_ASSIGNMENT、只读或隐藏
                    // 节点仍必须能从基础配置直接得到参与人。
                    boolean independentEditableSource = visible
                            && editable
                            && ("SCOPE".equals(type)
                            || "RESOLVER".equals(type));
                    if (multiInstance
                            && !independentEditableSource) {
                        validateEnumerableNodeAssignment(
                                element, assigneeConfig,
                                processConfigId);
                    } else if (multiInstance) {
                        validateEffectiveMultiInstanceResolver(
                                element, assigneeConfig,
                                processConfigId);
                    }
                    if ("SCOPE".equals(type)) {
                        var rules = objectMapper.valueToTree(
                                normalized.rawScopes());
                        if (!rules.isArray()) {
                            throw nextApproverConfigError(
                                    element.id(),
                                    "SCOPE source.rules 必须是数组");
                        }
                        if ((visible || editable) && rules.isEmpty()) {
                            throw nextApproverConfigError(
                                    element.id(),
                                    "SCOPE source.rules 不能为空");
                        }
                        Set<String> supported = Set.of(
                                "ALL_USERS",
                                "USER",
                                "ROLE",
                                "GROUP",
                                "ORGANIZATION");
                        for (var rule : rules) {
                            if (!rule.isObject()) {
                                throw nextApproverConfigError(
                                        element.id(),
                                        "SCOPE rule 必须是对象");
                            }
                            String ruleType = rule.path("type")
                                    .asText("")
                                    .trim()
                                    .toUpperCase(Locale.ROOT);
                            if (!supported.contains(ruleType)) {
                                throw nextApproverConfigError(
                                        element.id(),
                                        "不支持的 SCOPE rule.type: "
                                                + ruleType);
                            }
                            if (!"ALL_USERS".equals(ruleType)
                                    && (!rule.path("values").isArray()
                                    || rule.path("values").isEmpty())) {
                                throw nextApproverConfigError(
                                        element.id(),
                                        ruleType + " rule.values 不能为空");
                            }
                            if (rule.has("includeChildren")
                                    && !rule.path("includeChildren")
                                    .isBoolean()) {
                                throw nextApproverConfigError(
                                        element.id(),
                                        "includeChildren 必须是布尔值");
                            }
                        }
                    } else if ("RESOLVER".equals(type)) {
                        String resolverCode = normalized.resolverCode() == null
                                ? "" : normalized.resolverCode().trim();
                        if (resolverCode.isBlank()) {
                            throw nextApproverConfigError(
                                    element.id(),
                                    "RESOLVER resolverCode 不能为空");
                        }
                        if (normalized.extraParams() != null
                                && !(normalized.extraParams()
                                instanceof Map<?, ?>)) {
                            throw nextApproverConfigError(
                                    element.id(),
                                    "RESOLVER extraParams 必须是对象");
                        }
                        if (personResolverRuntimeService == null) {
                            throw nextApproverConfigError(
                                    element.id(),
                                    "RESOLVER 校验服务不可用");
                        }
                        try {
                            personResolverRuntimeService.requireConfigured(
                                    resolverCode,
                                    PersonResolveUsage.CANDIDATE);
                        } catch (RuntimeException exception) {
                            throw nextApproverConfigError(
                                    element.id(),
                                    "RESOLVER 不可用: "
                                            + exception.getMessage());
                        }
                        validateResolverConfiguration(
                                element.id(),
                                resolverCode,
                                PersonResolveUsage.CANDIDATE,
                                "CANDIDATE",
                                false,
                                processConfigId,
                                normalized.extraParams());
                    } else if ("NODE_ASSIGNMENT".equals(type)) {
                        // 隐藏配置是设计器默认占位，不得反向要求旧节点已经配置
                        // 可枚举办理人；真正启用展示/改选时才执行安全校验。
                        if ((visible || editable)
                                && !multiInstance) {
                            validateEnumerableNodeAssignment(
                                    element, assigneeConfig,
                                    processConfigId);
                        }
                    } else {
                        throw nextApproverConfigError(
                                element.id(),
                                "source.type 仅支持 SCOPE 或 RESOLVER，或使用 NODE_ASSIGNMENT");
                    }
                    return element;
                });
        validateEditableMultiInstanceCollections(validated);
        validateNodeAssignmentReferences(validated, processConfigId);
        return validated;
    }

    /**
     * 历史流程可能把会签人员写在 multiInstanceConfig。发布校验与运行时
     * 使用同一保序并集视图，避免只校验 assigneeConfig 而遗漏实际参与人。
     */
    @SuppressWarnings("unchecked")
    private com.fasterxml.jackson.databind.JsonNode
            mergeDeployedAssignmentConfig(
            ConfiguredElement element,
            com.fasterxml.jackson.databind.JsonNode assigneeConfig) {
        try {
            Map<String, Object> primary = objectMapper.convertValue(
                    assigneeConfig, Map.class);
            Map<String, Object> fallback = Map.of();
            String multiInstanceDocument = readPropertyValue(
                    element.content(), "multiInstanceConfig");
            if (StringUtils.hasText(multiInstanceDocument)) {
                fallback = objectMapper.readValue(
                        multiInstanceDocument, Map.class);
            }
            return objectMapper.valueToTree(
                    LegacyMultiInstanceAssignmentParser.mergeConfigs(
                            primary, fallback));
        } catch (Exception exception) {
            throw nextApproverConfigError(
                    element.id(),
                    "multiInstanceConfig 不是合法 JSON: "
                            + exception.getMessage());
        }
    }

    /**
     * 校验统一办理人配置版本。未声明版本的部署按历史格式读取；版本 2 表示
     * 多实例与普通任务都使用 assigneeType 等基础字段，禁止静默猜测未来版本。
     */
    private void validateAssignmentConfigVersion(
            String nodeId,
            com.fasterxml.jackson.databind.JsonNode assigneeConfig) {
        var rawVersion = assigneeConfig.path("assignmentConfigVersion");
        if (rawVersion.isMissingNode() || rawVersion.isNull()) {
            return;
        }
        if (!rawVersion.isIntegralNumber()) {
            throw nextApproverConfigError(
                    nodeId,
                    "assignmentConfigVersion 必须是整数");
        }
        if (rawVersion.asInt() != 2) {
            throw nextApproverConfigError(
                    nodeId,
                    "不支持的 assignmentConfigVersion: "
                            + rawVersion.asInt());
        }
    }

    /**
     * NODE_ASSIGNMENT 必须能从目标节点的基础配置枚举出人员。
     * 表达式可能调用 Bean 或依赖未信任上下文，预览无法与 Flowable
     * 任务创建保持一致，因此在发布边界明确拒绝。
     */
    private void validateEnumerableNodeAssignment(
            ConfiguredElement element,
            com.fasterxml.jackson.databind.JsonNode assigneeConfig,
            String processConfigId) {
        boolean multiInstance = element.content()
                .toLowerCase(Locale.ROOT)
                .contains("multiinstanceloopcharacteristics");
        validateEnumerableNodeAssignment(
                element,
                assigneeConfig,
                multiInstance,
                !multiInstance,
                !multiInstance,
                processConfigId,
                multiInstance,
                multiInstance ? "MULTI_INSTANCE" : null);
    }

    /**
     * @param outputMultiInstance 输出模式来自引用者
     * @param sourceMultiInstance 真正提供规则的 UserTask 是否为多实例
     * @param outputAssignmentMode 引用者运行时实际 direct/candidate/MI 模式
     * @param allowBpmnFallback 是否允许使用规则源的 BPMN 字面量属性
     * @param inspectBpmnExpressions 是否检查规则源的 BPMN 动态表达式
     */
    private void validateEnumerableNodeAssignment(
            ConfiguredElement element,
            com.fasterxml.jackson.databind.JsonNode assigneeConfig,
            boolean outputMultiInstance,
            boolean allowBpmnFallback,
            boolean inspectBpmnExpressions,
            String processConfigId,
            boolean sourceMultiInstance,
            String outputAssignmentMode) {
        String effectiveOutputMode = StringUtils.hasText(
                outputAssignmentMode)
                ? outputAssignmentMode
                : assigneeConfig.path("assignmentMode")
                .asText(outputMultiInstance
                        ? "MULTI_INSTANCE" : "DIRECT");
        if (sourceMultiInstance
                && assigneeConfig.path("assignmentConfigVersion")
                .asInt(0) != 2
                && validateLegacyMultiInstanceAssignment(
                element,
                assigneeConfig,
                processConfigId,
                outputMultiInstance
                        ? PersonResolveUsage.MULTI_INSTANCE
                        : PersonResolveUsage.ASSIGNEE,
                effectiveOutputMode,
                outputMultiInstance)) {
            return;
        }
        String rawType = assigneeConfig.path("assigneeType")
                .asText("")
                .trim()
                .toLowerCase(Locale.ROOT);
        String type = "interface".equals(rawType)
                ? "resolver" : rawType;
        switch (type) {
            case "user" -> {
                rejectAssignmentExpressions(
                        element,
                        outputMultiInstance,
                        inspectBpmnExpressions,
                        assigneeConfig.path("assigneeValue"),
                        assigneeConfig.path("candidateUsers"));
                if (!hasConfiguredValues(
                        assigneeConfig.path("assigneeValue"))
                        && !hasConfiguredValues(
                        assigneeConfig.path("candidateUsers"))
                        && !hasLiteralBpmnAssignment(
                        element,
                        allowBpmnFallback,
                        "assignee",
                        "candidateUsers")) {
                    throw nextApproverConfigError(
                            element.id(),
                            "基础固定人员配置不能为空");
                }
            }
            case "group", "role" -> {
                rejectAssignmentExpressions(
                        element,
                        outputMultiInstance,
                        inspectBpmnExpressions,
                        assigneeConfig.path("assigneeValue"));
                if (!hasConfiguredValues(
                        assigneeConfig.path("assigneeValue"))
                        && !hasLiteralBpmnAssignment(
                        element,
                        allowBpmnFallback,
                        "candidateGroups")) {
                    throw nextApproverConfigError(
                            element.id(),
                            "基础组或角色配置不能为空");
                }
            }
            case "candidate" -> {
                rejectAssignmentExpressions(
                        element,
                        outputMultiInstance,
                        inspectBpmnExpressions,
                        assigneeConfig.path("assigneeValue"),
                        assigneeConfig.path("candidateUsers"));
                if (!hasConfiguredValues(
                        assigneeConfig.path("candidateUsers"))
                        && !hasConfiguredValues(
                        assigneeConfig.path("assigneeValue"))
                        && !hasLiteralBpmnAssignment(
                        element,
                        allowBpmnFallback,
                        "candidateUsers",
                        "candidateGroups")) {
                    throw nextApproverConfigError(
                            element.id(),
                            "基础候选人配置不能为空");
                }
            }
            case "resolver" -> validateNodeAssignmentResolver(
                    element.id(), assigneeConfig, outputMultiInstance,
                    processConfigId, effectiveOutputMode);
            case "node_reference", "nodereference" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> values = objectMapper.convertValue(
                        assigneeConfig, Map.class);
                String referencedNodeId =
                        NodeAssignmentReferenceResolver.referencedNodeId(
                                values);
                if (!StringUtils.hasText(referencedNodeId)) {
                    throw nextApproverConfigError(
                            element.id(),
                            "node_reference 缺少 referencedNodeId");
                }
                if (containsExpression(referencedNodeId)) {
                    throw nextApproverConfigError(
                            element.id(),
                            "referencedNodeId 必须是字面量节点 ID");
                }
                // 目标存在性、类型、环和终端规则由整张部署图统一校验。
            }
            case "expression" -> throw nextApproverConfigError(
                    element.id(),
                    (outputMultiInstance ? "多实例" : "NODE_ASSIGNMENT")
                            + "不支持无法安全枚举的表达式办理人");
            case "" -> {
                rejectAssignmentExpressions(
                        element,
                        outputMultiInstance,
                        inspectBpmnExpressions);
                if (!hasLiteralBpmnAssignment(
                        element,
                        allowBpmnFallback,
                        "assignee",
                        "candidateUsers",
                        "candidateGroups")) {
                    throw nextApproverConfigError(
                            element.id(),
                            "缺少可用的基础办理人配置");
                }
            }
            default -> throw nextApproverConfigError(
                    element.id(),
                    "不支持的基础办理人类型: " + rawType);
        }
    }

    /**
     * 校验无 v2 标记的历史多实例来源，并返回是否命中了历史格式。
     * 发布校验与运行时都采用 legacy-first，避免校验基础 resolver、运行却调用
     * collection resolver 的用途漂移。
     */
    @SuppressWarnings("unchecked")
    private boolean validateLegacyMultiInstanceAssignment(
            ConfiguredElement element,
            com.fasterxml.jackson.databind.JsonNode config,
            String processConfigId,
            PersonResolveUsage usage,
            String assignmentMode,
            boolean outputMultiInstance) {
        Map<String, Object> values = objectMapper.convertValue(
                config, Map.class);
        LegacyAssignment legacy =
                LegacyMultiInstanceAssignmentParser.parse(values);
        if (!legacy.effective()) {
            // 旧设计器可能透传全空字段；它们不构成真实历史来源，继续校验
            // 基础办理人配置，避免仅打开并发布节点就改变运行语义。
            return false;
        }
        if (legacy.resolver()) {
            var extraParams = config.path("collectionExtraParams");
            if (config.has("collectionExtraParams")
                    && !extraParams.isObject()) {
                throw nextApproverConfigError(
                        element.id(),
                        "历史多实例 collectionExtraParams 必须是对象");
            }
            validateConfiguredResolver(
                    element.id(),
                    legacy.resolverCode(),
                    usage,
                    "历史多实例人员解析器");
            validateResolverConfiguration(
                    element.id(),
                    legacy.resolverCode(),
                    usage,
                    assignmentMode,
                    outputMultiInstance,
                    processConfigId,
                    legacy.resolverExtraParams());
            return true;
        }
        if (legacy.containsExpression()) {
            throw nextApproverConfigError(
                    element.id(),
                    "历史多实例人员配置包含无法安全枚举的表达式");
        }
        return true;
    }

    /**
     * 可编辑独立范围允许没有默认人员，但已配置的默认 resolver 仍必须
     * 按真实 legacy/v2 来源完成目录、用途和静态参数校验。
     */
    @SuppressWarnings("unchecked")
    private void validateEffectiveMultiInstanceResolver(
            ConfiguredElement element,
            com.fasterxml.jackson.databind.JsonNode config,
            String processConfigId) {
        Map<String, Object> values = objectMapper.convertValue(
                config, Map.class);
        if (LegacyMultiInstanceAssignmentParser
                .usesLegacyMultiInstanceAssignment(values, true)) {
            LegacyAssignment legacy =
                    LegacyMultiInstanceAssignmentParser.parse(values);
            if (legacy.resolver()) {
                validateLegacyMultiInstanceAssignment(
                        element,
                        config,
                        processConfigId,
                        PersonResolveUsage.MULTI_INSTANCE,
                        "MULTI_INSTANCE",
                        true);
            }
            return;
        }
        var effective = LegacyMultiInstanceAssignmentParser
                .effectiveResolver(values, true);
        if (effective.configured()) {
            validateNodeAssignmentResolver(
                    element.id(), config, true, processConfigId);
        }
    }

    private boolean hasLiteralBpmnAssignment(
            ConfiguredElement element,
            boolean allowBpmnFallback,
            String... attributeNames) {
        if (!allowBpmnFallback) {
            return false;
        }
        for (String attributeName : attributeNames) {
            String value = attributeValue(
                    element.startTag(), attributeName);
            if (StringUtils.hasText(value)
                    && !containsExpression(value)) {
                return true;
            }
        }
        return false;
    }

    private void rejectAssignmentExpressions(
            ConfiguredElement element,
            boolean outputMultiInstance,
            boolean inspectBpmnExpressions,
            com.fasterxml.jackson.databind.JsonNode... configuredValues) {
        for (var value : configuredValues) {
            if (containsExpression(value)) {
                throw nextApproverConfigError(
                        element.id(),
                        (outputMultiInstance ? "多实例" : "NODE_ASSIGNMENT")
                                + "基础办理人包含无法安全枚举的表达式");
            }
        }
        // 多实例的 assignee=${elementVariable} 是 Flowable 必需的技术属性，
        // 不属于基础人员来源；普通任务的动态属性则无法用于预览候选边界。
        if (inspectBpmnExpressions
                && (containsExpression(attributeValue(
                element.startTag(), "assignee"))
                || containsExpression(attributeValue(
                element.startTag(), "candidateUsers"))
                || containsExpression(attributeValue(
                element.startTag(), "candidateGroups")))) {
            throw nextApproverConfigError(
                    element.id(),
                    "BPMN 办理人属性包含无法安全枚举的表达式");
        }
    }

    private boolean containsExpression(
            com.fasterxml.jackson.databind.JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return false;
        }
        if (value.isArray()) {
            for (var item : value) {
                if (containsExpression(item)) {
                    return true;
                }
            }
            return false;
        }
        return containsExpression(value.asText(""));
    }

    private boolean containsExpression(String value) {
        return StringUtils.hasText(value)
                && (value.contains("${") || value.contains("#{"));
    }

    private void validateNodeAssignmentResolver(
            String nodeId,
            com.fasterxml.jackson.databind.JsonNode assigneeConfig,
            boolean multiInstance,
            String processConfigId) {
        validateNodeAssignmentResolver(
                nodeId,
                assigneeConfig,
                multiInstance,
                processConfigId,
                null);
    }

    private void validateNodeAssignmentResolver(
            String nodeId,
            com.fasterxml.jackson.databind.JsonNode assigneeConfig,
            boolean multiInstance,
            String processConfigId,
            String assignmentModeOverride) {
        String resolverCode = assigneeConfig.path("resolverCode")
                .asText(assigneeConfig.path("interfaceName").asText(""))
                .trim();
        if (assigneeConfig.has("extraParams")
                && !assigneeConfig.path("extraParams").isObject()) {
            throw nextApproverConfigError(
                    nodeId,
                    "基础办理人 extraParams 必须是对象");
        }
        PersonResolveUsage usage = multiInstance
                ? PersonResolveUsage.MULTI_INSTANCE
                : PersonResolveUsage.ASSIGNEE;
        validateConfiguredResolver(
                nodeId,
                resolverCode,
                usage,
                "NODE_ASSIGNMENT 人员解析器");
        @SuppressWarnings("unchecked")
        Map<String, Object> extraParams = assigneeConfig.path("extraParams")
                .isObject()
                ? objectMapper.convertValue(
                        assigneeConfig.path("extraParams"), Map.class)
                : Map.of();
        validateResolverConfiguration(
                nodeId,
                resolverCode,
                usage,
                StringUtils.hasText(assignmentModeOverride)
                        ? assignmentModeOverride
                        : assigneeConfig.path("assignmentMode")
                        .asText(multiInstance
                                ? "MULTI_INSTANCE" : "DIRECT"),
                multiInstance,
                processConfigId,
                extraParams);
    }

    /**
     * 将解析器特有的配置交给对应 validator；相对职务属于安全关键内置解析器，
     * 若实现未注册则必须阻断发布，不能仅依赖目录记录。
     */
    private void validateResolverConfiguration(
            String nodeId,
            String resolverCode,
            PersonResolveUsage usage,
            String assignmentMode,
            boolean multiInstance,
            String processConfigId,
            Object rawExtraParams) {
        PersonResolverConfigurationValidator validator =
                personResolverConfigurationValidators == null
                        ? null
                        : personResolverConfigurationValidators.stream()
                        .filter(value -> resolverCode.equals(
                                value.resolverCode()))
                        .findFirst()
                        .orElse(null);
        if (validator == null) {
            if (com.workflow.process.assignment.relative
                    .RelativeOrgPositionConfig.RESOLVER_CODE
                    .equals(resolverCode)
                    || com.workflow.process.assignment.entity
                    .EntityUserReferenceFieldConfig.RESOLVER_CODE
                    .equals(resolverCode)) {
                throw nextApproverConfigError(
                        nodeId,
                        resolverCode + " 配置校验器未注册");
            }
            return;
        }
        Map<String, Object> extraParams;
        if (rawExtraParams == null) {
            extraParams = Map.of();
        } else if (rawExtraParams instanceof Map<?, ?> map) {
            extraParams = new LinkedHashMap<>();
            map.forEach((key, value) ->
                    extraParams.put(String.valueOf(key), value));
        } else {
            throw nextApproverConfigError(
                    nodeId, "resolver extraParams 必须是对象");
        }
        try {
            validator.validate(
                    new PersonResolverConfigurationValidationRequest(
                            usage,
                            assignmentMode,
                            multiInstance,
                            processConfigId,
                            extraParams));
        } catch (IllegalArgumentException exception) {
            throw nextApproverConfigError(
                    nodeId,
                    "resolver 配置无效: " + exception.getMessage());
        }
    }

    private void validateConfiguredResolver(
            String nodeId,
            String resolverCode,
            PersonResolveUsage usage,
            String label) {
        if (!StringUtils.hasText(resolverCode)) {
            throw nextApproverConfigError(
                    nodeId,
                    label + "不能为空");
        }
        if (personResolverRuntimeService == null) {
            throw nextApproverConfigError(
                    nodeId,
                    label + "校验服务不可用");
        }
        try {
            personResolverRuntimeService.requireConfigured(
                    resolverCode, usage);
        } catch (RuntimeException exception) {
            throw nextApproverConfigError(
                    nodeId,
                    label + "不支持 "
                            + usage
                            + ": "
                            + exception.getMessage());
        }
    }

    private boolean hasConfiguredValues(
            com.fasterxml.jackson.databind.JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return false;
        }
        if (value.isArray()) {
            for (var item : value) {
                if (hasConfiguredValues(item)) {
                    return true;
                }
            }
            return false;
        }
        return value.isTextual()
                ? StringUtils.hasText(value.asText())
                : !value.asText("").isBlank();
    }

    private void validateEditableMultiInstanceCollections(
            String bpmnXml) {
        Pattern tasks = Pattern.compile(
                "(?is)<(?:[A-Za-z0-9_]+:)?userTask\\b([^>]*)>(.*?)</(?:[A-Za-z0-9_]+:)?userTask>");
        Matcher matcher = tasks.matcher(bpmnXml);
        Map<String, String> owners = new LinkedHashMap<>();
        Set<String> editableCollections = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            String start = matcher.group(1);
            String content = matcher.group(2);
            String nodeId = attributeValue("<userTask " + start + ">", "id");
            String assigneeDocument = readPropertyValue(
                    content, "assigneeConfig");
            boolean editable = false;
            if (assigneeDocument != null) {
                try {
                    var selection = objectMapper.readTree(assigneeDocument)
                            .path("nextApproverSelection");
                    if (selection.isObject()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> selectionMap =
                                objectMapper.convertValue(
                                        selection, Map.class);
                        editable = NextApproverSelectionNormalizer
                                .normalize(selectionMap)
                                .editable();
                    }
                } catch (Exception exception) {
                    throw nextApproverConfigError(
                            nodeId, "assigneeConfig 不是合法 JSON");
                }
            }
            Matcher loop = Pattern.compile(
                    "(?is)<(?:[A-Za-z0-9_]+:)?multiInstanceLoopCharacteristics\\b([^>]*)>")
                    .matcher(content);
            if (!loop.find()) {
                loop = Pattern.compile(
                        "(?is)<(?:[A-Za-z0-9_]+:)?multiInstanceLoopCharacteristics\\b([^>]*)/>")
                        .matcher(content);
                if (!loop.find()) {
                    continue;
                }
            }
            String collection = attributeValue(
                    "<multiInstanceLoopCharacteristics "
                            + loop.group(1)
                            + ">",
                    "collection");
            if (!StringUtils.hasText(collection)) {
                collection = attributeValue(
                        "<multiInstanceLoopCharacteristics "
                                + loop.group(1)
                                + ">",
                        "flowable:collection");
            }
            if (!StringUtils.hasText(collection)) {
                if (editable) {
                    throw nextApproverConfigError(
                            nodeId,
                            "可改选的多实例节点必须配置 collection 流程变量");
                }
                continue;
            }
            String collectionVariable = collection.trim();
            if ((collectionVariable.startsWith("${")
                    || collectionVariable.startsWith("#{"))
                    && collectionVariable.endsWith("}")) {
                collectionVariable = collectionVariable.substring(
                        2, collectionVariable.length() - 1).trim();
            }
            if (MultiInstanceVariableNames.ENTRY_DYNAMIC_COLLECTION_LITERAL
                    .equals(collectionVariable)) {
                // 二次净化时所有动态节点的 loop 都使用同一安全 seed，
                // collection 所有权仍必须按各任务保存的原业务变量判断。
                String preserved = readPropertyValue(
                        content,
                        MultiInstanceVariableNames
                                .ENTRY_DYNAMIC_COLLECTION_PROPERTY);
                if (StringUtils.hasText(preserved)) {
                    collectionVariable = preserved.trim();
                }
            }
            if (!collectionVariable.matches(
                    "[A-Za-z_][A-Za-z0-9_]*")) {
                if (editable) {
                    throw nextApproverConfigError(
                            nodeId,
                            "多实例 collection 必须是简单流程变量: "
                                    + collection);
                }
                continue;
            }
            boolean generatedCollection = collectionVariable.equals(
                    MultiInstanceVariableNames.buildCollectionVariableName(
                            nodeId))
                    || MultiInstanceVariableNames.LEGACY_COLLECTION_VARIABLE
                    .equals(collectionVariable);
            if ((!generatedCollection
                    && WorkflowReservedVariables
                    .isProtectedContextVariable(collectionVariable))
                    || MultiInstanceVariableNames
                    .ENTRY_DYNAMIC_COLLECTION_LITERAL
                    .equals(collectionVariable)) {
                throw nextApproverConfigError(
                        nodeId,
                        "多实例 collection 不能覆盖平台保留流程变量: "
                                + collectionVariable);
            }
            String previous = owners.putIfAbsent(
                    collectionVariable, nodeId);
            if (previous != null
                    && !previous.equals(nodeId)
                    && (editable
                    || editableCollections.contains(collectionVariable))) {
                throw nextApproverConfigError(
                        nodeId,
                        "可改选的多实例节点不能共用 collection "
                                + collectionVariable
                                + "，已被节点 "
                                + previous
                                + " 使用");
            }
            if (editable) {
                editableCollections.add(collectionVariable);
            }
        }
    }

    /**
     * 校验部署态多实例变量不得覆盖平台与 Flowable 保留上下文。
     *
     * <p>{@code elementVariable} 会作为 execution-local 变量写入每个子执行；
     * 若允许复用 skipExpression 开关，就能遮蔽根作用域的安全值。这里在
     * 所有发布改写完成后使用 namespace-aware DOM 复核，同时覆盖任意合法
     * XML 前缀与历史草稿的无前缀属性。</p>
     *
     * @param bpmnXml 即将进入 Flowable 部署的 BPMN XML
     */
    private void validateProtectedMultiInstanceVariables(
            String bpmnXml) {
        try {
            Document document = parseXml(bpmnXml);
            for (Element loop : elementsByLocalName(
                    document, "multiInstanceLoopCharacteristics")) {
                Element activity = loop.getParentNode() instanceof Element
                        ? (Element) loop.getParentNode() : null;
                String nodeId = activity == null
                        ? "" : activity.getAttribute("id");

                String elementVariable = flowableAttribute(
                        loop, "elementVariable").trim();
                if (WorkflowReservedVariables.isProtectedContextVariable(
                        elementVariable)) {
                    throw multiInstanceVariableError(
                            nodeId,
                            "elementVariable 不能覆盖平台保留流程变量: "
                                    + elementVariable);
                }

                String collectionVariable = simpleCollectionVariable(
                        flowableAttribute(loop, "collection"));
                boolean generatedCollection = StringUtils.hasText(
                        collectionVariable)
                        && (collectionVariable.equals(
                        MultiInstanceVariableNames
                                .buildCollectionVariableName(nodeId))
                        || MultiInstanceVariableNames
                                .LEGACY_COLLECTION_VARIABLE
                                .equals(collectionVariable));
                if (!generatedCollection
                        && WorkflowReservedVariables
                        .isProtectedContextVariable(collectionVariable)) {
                    throw multiInstanceVariableError(
                            nodeId,
                            "collection 不能覆盖平台保留流程变量: "
                                    + collectionVariable);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "多实例变量安全校验无法解析 BPMN XML",
                    exception);
        }
    }

    private IllegalArgumentException multiInstanceVariableError(
            String nodeId,
            String detail) {
        return new IllegalArgumentException(
                "多实例变量配置无效: nodeId="
                        + nodeId
                        + ", "
                        + detail);
    }

    /**
     * 对整张发布模型执行 node_reference 图校验，并按引用者的输出模式校验
     * 终端人员规则。引用目标的多实例属性不得改变当前节点的 resolver usage。
     */
    private void validateNodeAssignmentReferences(
            String bpmnXml,
            String processConfigId) {
        final Document document;
        try {
            document = parseXml(bpmnXml);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "审批人节点引用校验无法解析 BPMN XML", exception);
        }
        Map<String, Element> allElements = new LinkedHashMap<>();
        NodeList everyElement = document.getElementsByTagNameNS("*", "*");
        for (int index = 0; index < everyElement.getLength(); index++) {
            Element element = (Element) everyElement.item(index);
            String id = element.getAttribute("id");
            if (StringUtils.hasText(id)) {
                allElements.putIfAbsent(id, element);
            }
        }

        Map<String, PublishedAssignmentNode> userTasks =
                new LinkedHashMap<>();
        for (Element element : elementsByLocalName(document, "userTask")) {
            String id = element.getAttribute("id");
            if (!StringUtils.hasText(id)) {
                continue;
            }
            userTasks.put(id, new PublishedAssignmentNode(
                    id,
                    element,
                    readMergedAssignmentConfig(element),
                    hasMultiInstanceLoop(element)));
        }

        for (PublishedAssignmentNode current : userTasks.values()) {
            if (!NodeAssignmentReferenceResolver
                    .isEffectiveNodeReference(
                            current.assigneeConfig(),
                            current.multiInstance())) {
                continue;
            }
            PublishedAssignmentNode terminal = resolvePublishedReference(
                    current, userTasks, allElements);
            validateAssignmentConfigVersion(
                    terminal.id(),
                    objectMapper.valueToTree(
                            terminal.assigneeConfig()));
            ConfiguredElement validationElement =
                    referenceValidationElement(current, terminal);
            validateEnumerableNodeAssignment(
                    validationElement,
                    objectMapper.valueToTree(
                            terminal.assigneeConfig()),
                    current.multiInstance(),
                    true,
                    true,
                    processConfigId,
                    terminal.multiInstance(),
                    publishedAssignmentMode(current, terminal));
        }
    }

    private PublishedAssignmentNode resolvePublishedReference(
            PublishedAssignmentNode current,
            Map<String, PublishedAssignmentNode> userTasks,
            Map<String, Element> allElements) {
        PublishedAssignmentNode node = current;
        Set<String> visited = new java.util.LinkedHashSet<>();
        List<String> chain = new ArrayList<>();
        for (int depth = 0;
                depth <= NodeAssignmentReferenceResolver.MAX_REFERENCE_DEPTH;
                depth++) {
            if (!visited.add(node.id())) {
                chain.add(node.id());
                throw nextApproverConfigError(
                        current.id(),
                        "审批人节点引用形成环: "
                                + String.join(" -> ", chain));
            }
            chain.add(node.id());
            if (!NodeAssignmentReferenceResolver
                    .isEffectiveNodeReference(
                            node.assigneeConfig(),
                            node.multiInstance())) {
                return node;
            }
            if (depth
                    == NodeAssignmentReferenceResolver.MAX_REFERENCE_DEPTH) {
                throw nextApproverConfigError(
                        current.id(),
                        "审批人节点引用超过最大深度 "
                                + NodeAssignmentReferenceResolver
                                .MAX_REFERENCE_DEPTH);
            }
            String referencedNodeId =
                    NodeAssignmentReferenceResolver.referencedNodeId(
                            node.assigneeConfig());
            if (!StringUtils.hasText(referencedNodeId)) {
                throw nextApproverConfigError(
                        node.id(),
                        "node_reference 缺少 referencedNodeId");
            }
            if (containsExpression(referencedNodeId)) {
                throw nextApproverConfigError(
                        node.id(),
                        "referencedNodeId 必须是字面量节点 ID");
            }
            PublishedAssignmentNode referenced =
                    userTasks.get(referencedNodeId);
            if (referenced == null) {
                String detail = allElements.containsKey(referencedNodeId)
                        ? "引用目标不是 UserTask: " + referencedNodeId
                        : "引用目标不存在: " + referencedNodeId;
                throw nextApproverConfigError(node.id(), detail);
            }
            node = referenced;
        }
        throw nextApproverConfigError(
                current.id(), "审批人节点引用深度校验失败");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMergedAssignmentConfig(
            Element userTask) {
        String nodeId = userTask.getAttribute("id");
        try {
            Map<String, Object> assigneeConfig = Map.of();
            String assigneeDocument = readPropertyValue(
                    userTask, "assigneeConfig");
            if (StringUtils.hasText(assigneeDocument)) {
                assigneeConfig = objectMapper.readValue(
                        assigneeDocument, Map.class);
            }
            Map<String, Object> multiInstanceConfig = Map.of();
            String multiInstanceDocument = readPropertyValue(
                    userTask, "multiInstanceConfig");
            if (StringUtils.hasText(multiInstanceDocument)) {
                multiInstanceConfig = objectMapper.readValue(
                        multiInstanceDocument, Map.class);
            }
            return LegacyMultiInstanceAssignmentParser.mergeConfigs(
                    assigneeConfig, multiInstanceConfig);
        } catch (Exception exception) {
            throw nextApproverConfigError(
                    nodeId, "人员配置不是合法 JSON: "
                            + exception.getMessage());
        }
    }

    private boolean hasMultiInstanceLoop(Element userTask) {
        return userTask.getElementsByTagNameNS(
                "*", "multiInstanceLoopCharacteristics")
                .getLength() > 0;
    }

    private String publishedAssignmentMode(
            PublishedAssignmentNode current,
            PublishedAssignmentNode terminal) {
        return NodeAssignmentReferenceResolver.assignmentMode(
                current.multiInstance(),
                terminal.multiInstance(),
                flowableAttribute(terminal.element(), "assignee"),
                attributeValues(flowableAttribute(
                        terminal.element(), "candidateUsers")),
                attributeValues(flowableAttribute(
                        terminal.element(), "candidateGroups")),
                terminal.assigneeConfig());
    }

    private List<String> attributeValues(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 构造校验视图：循环模式来自引用者，字面量 BPMN 分配属性来自终端源。
     */
    private ConfiguredElement referenceValidationElement(
            PublishedAssignmentNode current,
            PublishedAssignmentNode terminal) {
        StringBuilder startTag = new StringBuilder("<userTask");
        for (String attribute : List.of(
                "assignee", "candidateUsers", "candidateGroups")) {
            String value = flowableAttribute(
                    terminal.element(), attribute);
            if ("assignee".equals(attribute)
                    && isTechnicalMultiInstanceAssignee(
                    terminal, value)) {
                continue;
            }
            if (StringUtils.hasText(value)) {
                startTag.append(' ')
                        .append(attribute)
                        .append("=\"")
                        .append(escapeXml(value))
                        .append("\"");
            }
        }
        startTag.append('>');
        String content = current.multiInstance()
                ? "<multiInstanceLoopCharacteristics/>" : "";
        return new ConfiguredElement(
                "", "userTask", startTag.toString(), content, current.id());
    }

    /** 仅忽略源 MI 节点绑定 elementVariable 的 Flowable 技术 assignee。 */
    private boolean isTechnicalMultiInstanceAssignee(
            PublishedAssignmentNode source,
            String assignee) {
        if (!source.multiInstance() || !StringUtils.hasText(assignee)) {
            return false;
        }
        NodeList loops = source.element().getElementsByTagNameNS(
                "*", "multiInstanceLoopCharacteristics");
        if (loops.getLength() == 0) {
            return false;
        }
        Element loop = (Element) loops.item(0);
        String variable = flowableAttribute(loop, "elementVariable");
        if (!StringUtils.hasText(variable)) {
            variable = loop.getAttribute("elementVariable");
        }
        if (!StringUtils.hasText(variable)) {
            return false;
        }
        String normalized = assignee.trim();
        return normalized.equals("${" + variable.trim() + "}")
                || normalized.equals("#{" + variable.trim() + "}");
    }

    private String flowableAttribute(
            Element element,
            String localName) {
        String value = element.getAttributeNS(
                FLOWABLE_NAMESPACE, localName);
        if (!StringUtils.hasText(value)) {
            value = element.getAttribute("flowable:" + localName);
        }
        if (!StringUtils.hasText(value)) {
            value = element.getAttribute(localName);
        }
        return value;
    }

    private IllegalArgumentException nextApproverConfigError(
            String nodeId,
            String detail) {
        return new IllegalArgumentException(
                "下一审批人配置无效: nodeId="
                        + nodeId
                        + ", "
                        + detail);
    }

    private String fixConfiguredUserTaskSlas(String bpmnXml) {
        return rewriteConfiguredElements(
                bpmnXml,
                "userTask",
                "slaConfig",
                (element, config) -> {
                    if (!config.path("enabled").asBoolean(false)) {
                        return element;
                    }
                    String policyCode =
                            config.path("policyCode").asText("").trim();
                    if (policyCode.isBlank()) {
                        throw new IllegalArgumentException(
                                "用户任务启用SLA后必须选择策略: "
                                        + element.id());
                    }
                    TaskSlaPolicySnapshot policy =
                            requireTaskSlaPolicyService()
                                    .publishedSnapshot(policyCode);
                    String source = config.path("calendarSource")
                            .asText("SYSTEM_DEFAULT")
                            .trim()
                            .toUpperCase(Locale.ROOT);
                    if (!Set.of(
                                    "NODE",
                                    "PROCESS",
                                    "BUSINESS_DEPT",
                                    "STARTER_DEPT",
                                    "SYSTEM_DEFAULT")
                            .contains(source)) {
                        throw new IllegalArgumentException(
                                "不支持的SLA日历来源: "
                                        + source
                                        + ", nodeId="
                                        + element.id());
                    }

                    ObjectNode published =
                            config.deepCopy();
                    published.put("calendarSource", source);
                    published.set(
                            "policySnapshot",
                            objectMapper.valueToTree(policy));
                    if ("NODE".equals(source)
                            || "PROCESS".equals(source)) {
                        String field = "NODE".equals(source)
                                ? "calendarCode"
                                : "processCalendarCode";
                        String calendarCode =
                                config.path(field).asText("").trim();
                        if (calendarCode.isBlank()) {
                            throw new IllegalArgumentException(
                                    "SLA固定日历不能为空: nodeId="
                                            + element.id());
                        }
                        WorkCalendarSnapshot calendar =
                                requireWorkCalendarService()
                                        .findPublishedSnapshotByCode(
                                                calendarCode);
                        published.set(
                                "calendarSnapshot",
                                objectMapper.valueToTree(calendar));
                        published.remove(
                                "calendarResolutionSnapshot");
                    } else {
                        if ("BUSINESS_DEPT".equals(source)
                                && config.path("businessFieldCode")
                                .asText("").isBlank()) {
                            throw new IllegalArgumentException(
                                    "业务归属日历必须选择部门字段: nodeId="
                                            + element.id());
                        }
                        WorkCalendarResolutionSnapshot resolution =
                                requireWorkCalendarService()
                                        .resolutionSnapshot();
                        published.set(
                                "calendarResolutionSnapshot",
                                objectMapper.valueToTree(resolution));
                        published.remove("calendarSnapshot");
                    }
                    published.put("snapshotVersion", 1);
                    return element.withContent(
                            replacePropertyValue(
                                    element.content(),
                                    "slaConfig",
                                    writeJson(published)));
                });
    }

    private TaskSlaPolicyService requireTaskSlaPolicyService() {
        if (taskSlaPolicyService == null) {
            throw new IllegalStateException("SLA策略服务未初始化");
        }
        return taskSlaPolicyService;
    }

    private WorkCalendarService requireWorkCalendarService() {
        if (workCalendarService == null) {
            throw new IllegalStateException("工作日历服务未初始化");
        }
        return workCalendarService;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("用户任务SLA快照序列化失败", exception);
        }
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
            String contentType = config.path("contentType")
                    .asText("application/json");
            if (!"application/json".equalsIgnoreCase(contentType)) {
                throw new IllegalArgumentException(
                        "REST 服务任务仅支持 application/json: "
                                + element.id());
            }
            validateJsonObjectDocument(config.path("headers").asText(""), "REST 请求头", element.id());
            validateJsonObjectDocument(config.path("queryParams").asText(""), "REST 查询参数", element.id());
            validateJsonObjectDocument(config.path("resultMapping").asText(""), "REST 结果映射", element.id());
            validateJsonContainerDocument(
                    config.path("body").asText(""),
                    "REST 请求体",
                    element.id());
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

    private void validateJsonContainerDocument(
            String document,
            String label,
            String elementId) {
        if (document == null || document.isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    objectMapper.readTree(document);
            if (!node.isObject() && !node.isArray()) {
                throw new IllegalArgumentException(
                        label + "必须是 JSON 对象或数组: " + elementId);
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

    private String replacePropertyValue(
            String content,
            String propertyName,
            String value) {
        String escaped = escapeXml(value);
        Pattern nameFirst = Pattern.compile(
                "(?i)(<flowable:property\\b[^>]*name=\""
                        + Pattern.quote(propertyName)
                        + "\"[^>]*value=\")([^\"]*)(\")");
        Matcher matcher = nameFirst.matcher(content);
        if (matcher.find()) {
            return matcher.replaceFirst(
                    Matcher.quoteReplacement(
                            matcher.group(1)
                                    + escaped
                                    + matcher.group(3)));
        }
        Pattern valueFirst = Pattern.compile(
                "(?i)(<flowable:property\\b[^>]*value=\")([^\"]*)(\"[^>]*name=\""
                        + Pattern.quote(propertyName)
                        + "\")");
        matcher = valueFirst.matcher(content);
        if (matcher.find()) {
            return matcher.replaceFirst(
                    Matcher.quoteReplacement(
                            matcher.group(1)
                                    + escaped
                                    + matcher.group(3)));
        }
        throw new IllegalArgumentException(
                "节点扩展属性不存在: " + propertyName);
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

    private record PublishedAssignmentNode(
            String id,
            Element element,
            Map<String, Object> assigneeConfig,
            boolean multiInstance) {
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

    /**
     * 将平台管理的唯一可执行流程 ID 归一化为流程 Key，并同步所有直接引用。
     *
     * <p>协作图的 {@code BPMNPlane} 引用的是 collaboration，而不是 process，不能像旧实现一样
     * 全局覆盖。多流程协作图也只能有一个 {@code isExecutable=true} 的主流程；其余非执行流程
     * 仅用于表达外部参与者，必须保留各自 ID。</p>
     *
     * @param bpmnXml 原始 BPMN XML
     * @param processKey 平台流程 Key，也是部署后的主流程 ID
     * @return 引用关系一致的 BPMN XML
     * @throws IllegalArgumentException 多流程协作图没有唯一可执行主流程、流程 Key 与其他元素 ID
     *         冲突，或 XML 无法安全归一化时抛出
     */
    private String normalizeProcessIdentity(
            String bpmnXml,
            String processKey) {
        try {
            Document document = parseXml(bpmnXml);
            List<Element> processes = elementsByLocalName(
                    document, "process");
            if (processes.isEmpty()) {
                return bpmnXml;
            }

            Element executableProcess = resolveExecutableProcess(processes);
            String previousProcessId = executableProcess.getAttribute("id");
            if (previousProcessId.isBlank()) {
                throw new IllegalArgumentException(
                        "BPMN_EXECUTABLE_PROCESS_ID_MISSING: 可执行主流程缺少 ID");
            }
            if (processKey == null || processKey.isBlank()) {
                throw new IllegalArgumentException(
                        "BPMN_PROCESS_KEY_REQUIRED: 发布流程缺少 processKey");
            }
            if (previousProcessId.equals(processKey)) {
                return bpmnXml;
            }

            // 自动重命名任意冲突元素无法覆盖 BPMN 的全部 QName/IDREF 语义，发布边界选择明确阻断。
            Element conflictingElement = findElementById(
                    document, processKey, executableProcess);
            if (conflictingElement != null) {
                throw new IllegalArgumentException(
                        "BPMN_PROCESS_KEY_ID_CONFLICT: processKey 与其他 BPMN 元素 ID 冲突, element="
                                + processKey);
            }

            executableProcess.setAttribute("id", processKey);
            updateAttributeReferences(
                    document,
                    "participant",
                    "processRef",
                    previousProcessId,
                    processKey);
            updateAttributeReferences(
                    document,
                    null,
                    "bpmnElement",
                    previousProcessId,
                    processKey);
            return writeXml(document);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "BPMN_PROCESS_IDENTITY_INVALID: 无法归一化流程 ID 与协作图引用: "
                            + exception.getMessage(),
                    exception);
        }
    }

    /** 多流程文档必须显式且唯一地标记平台负责部署的可执行主流程。 */
    private Element resolveExecutableProcess(List<Element> processes) {
        if (processes.size() == 1) {
            return processes.get(0);
        }
        List<Element> executableProcesses = processes.stream()
                .filter(process -> "true".equalsIgnoreCase(
                        process.getAttribute("isExecutable")))
                .toList();
        if (executableProcesses.size() != 1) {
            throw new IllegalArgumentException(
                    "BPMN_EXECUTABLE_PROCESS_AMBIGUOUS: 多流程协作图必须且只能包含一个 "
                            + "isExecutable=true 的主流程");
        }
        return executableProcesses.get(0);
    }

    /** 查找除主流程自身外占用了目标流程 Key 的 BPMN 元素。 */
    private Element findElementById(
            Document document,
            String id,
            Element ignoredElement) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (element != ignoredElement
                    && id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    /**
     * 仅更新指向旧主流程 ID 的引用。elementLocalName 为空时检查所有元素，供 DI 引用使用。
     */
    private void updateAttributeReferences(
            Document document,
            String elementLocalName,
            String attributeName,
            String previousValue,
            String nextValue) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            String localName = element.getLocalName() == null
                    ? element.getTagName()
                    : element.getLocalName();
            if (elementLocalName != null
                    && !elementLocalName.equals(localName)) {
                continue;
            }
            String currentValue = element.getAttribute(attributeName);
            String updatedValue = renameIdOrQNameReference(
                    element,
                    currentValue,
                    previousValue,
                    nextValue);
            if (!currentValue.equals(updatedValue)) {
                element.setAttribute(attributeName, updatedValue);
            }
        }
    }

    /** 裸 ID 直接替换；QName 仅替换 local part，并保留其已绑定前缀。 */
    private String renameIdOrQNameReference(
            Element context,
            String currentValue,
            String previousValue,
            String nextValue) {
        if (previousValue.equals(currentValue)) {
            return nextValue;
        }
        int separator = currentValue.indexOf(':');
        if (separator <= 0
                || separator != currentValue.lastIndexOf(':')
                || !previousValue.equals(
                currentValue.substring(separator + 1))) {
            return currentValue;
        }
        String prefix = currentValue.substring(0, separator);
        String referenceNamespace = context.lookupNamespaceURI(prefix);
        if (referenceNamespace == null) {
            return currentValue;
        }
        String targetNamespace = context.getOwnerDocument()
                .getDocumentElement()
                .getAttribute("targetNamespace");
        // 同 local part 的外部 QName 不指向当前 definitions，不能随主流程改名。
        if (!targetNamespace.isBlank()
                && !targetNamespace.equals(referenceNamespace)) {
            return currentValue;
        }
        return prefix + ":" + nextValue;
    }

    /**
     * 补齐 Flowable 运行时要求的数据对象名称。
     *
     * <p>bpmn-js 将画布名称保存在 {@code dataObjectReference.name}，底层 {@code dataObject}
     * 可能仍然无名；Flowable 会在部署校验阶段以 {@code flowable-data-object-missing-name}
     * 拒绝。发布前优先同步引用名称，引用也无名时返回稳定阻断码，使预检与真实部署一致。</p>
     *
     * @param bpmnXml BPMN XML
     * @return 已补齐底层数据对象名称的 BPMN XML
     * @throws IllegalArgumentException 数据对象及其引用均未配置名称时抛出
     */
    private String normalizeDataObjectNames(String bpmnXml) {
        try {
            Document document = parseXml(bpmnXml);
            List<Element> dataObjects = elementsByLocalName(
                    document, "dataObject");
            if (dataObjects.isEmpty()) {
                return bpmnXml;
            }
            List<Element> references = elementsByLocalName(
                    document, "dataObjectReference");
            boolean changed = false;
            for (Element dataObject : dataObjects) {
                if (!dataObject.getAttribute("name").isBlank()) {
                    continue;
                }
                String dataObjectId = dataObject.getAttribute("id");
                Element firstReference = null;
                Element namedReference = null;
                for (Element reference : references) {
                    if (!referencesDataObject(reference, dataObjectId)) {
                        continue;
                    }
                    if (firstReference == null) {
                        firstReference = reference;
                    }
                    if (!reference.getAttribute("name").isBlank()) {
                        namedReference = reference;
                        break;
                    }
                }
                if (namedReference == null) {
                    String elementId = firstReference == null
                            ? dataObjectId
                            : firstReference.getAttribute("id");
                    throw new IllegalArgumentException(
                            "BPMN_DATA_OBJECT_NAME_MISSING: 数据对象必须配置名称, element="
                                    + elementId);
                }
                dataObject.setAttribute(
                        "name",
                        namedReference.getAttribute("name").trim());
                changed = true;
            }
            return changed ? writeXml(document) : bpmnXml;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "BPMN_DATA_OBJECT_INVALID: 无法校验数据对象: "
                            + exception.getMessage(),
                    exception);
        }
    }

    /** 支持标准裸 ID 以及带命名空间前缀的 QName 引用。 */
    private boolean referencesDataObject(
            Element reference,
            String dataObjectId) {
        String value = reference.getAttribute("dataObjectRef");
        return value.equals(dataObjectId)
                || value.endsWith(":" + dataObjectId);
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
        if (Pattern.compile("(?i)<(?:bpmn:)?scriptTask\\b")
                .matcher(bpmnXml)
                .find()) {
            throw new IllegalArgumentException(
                    "SCRIPT_TASK_DISABLED: 生产环境禁止发布脚本任务，"
                            + "请迁移为已注册的流程动作");
        }
        return bpmnXml;
    }

    /**
     * 将旧版 {@code skipNode=true} 归一化为 Flowable 原生恒真表达式。
     *
     * <p>扩展属性只承担设计态三态标记；部署态以 skipExpression 为唯一权威。
     * {@code skipNode=false} 的条件表达式必须原样保留。</p>
     */
    private String processSkipNodeTasks(String bpmnXml) {
        Pattern pattern = Pattern.compile(
                "(?i)<((?:bpmn:)?userTask)\\b([^>]*)>([\\s\\S]*?)</\\1\\s*>",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(bpmnXml);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            // 捕获完整限定名，避免可选前缀未参与匹配时 Java 反向引用导致
            // 无前缀 </userTask> 永远无法命中。
            String qualifiedTagName = matcher.group(1);
            String startTag = "<" + qualifiedTagName
                    + matcher.group(2) + ">";
            String content = matcher.group(3);
            String skipNode = readPropertyValue(content, "skipNode");
            if (Boolean.parseBoolean(skipNode == null
                    ? "false" : skipNode.trim())) {
                // ALWAYS 必须覆盖历史上同时残留的条件表达式，避免两套语义竞争。
                startTag = setQualifiedAttribute(
                        removeAttributes(startTag, "skipExpression"),
                        "skipExpression",
                        "${true}");
                content = content.replaceAll(
                        "(?is)<(?:flowable:)?skipExpression\\b[^>]*>.*?</(?:flowable:)?skipExpression\\s*>",
                        "");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    startTag + content + "</" + qualifiedTagName + ">"));
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
