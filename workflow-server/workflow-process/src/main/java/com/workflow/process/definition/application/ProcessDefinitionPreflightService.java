package com.workflow.process.definition.application;

import com.workflow.contracts.action.FlowActionDesignPort;
import com.workflow.process.configuration.infrastructure.persistence.mapper.AssigneeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.record.AssigneeConfig;
import com.workflow.process.configuration.infrastructure.persistence.record.NodeConfig;
import com.workflow.process.definition.api.response.ProcessDefinitionDiffDTO;
import com.workflow.process.definition.api.response.ProcessPublishPreviewDTO;
import com.workflow.process.definition.api.response.ProcessValidationIssueDTO;
import com.workflow.process.definition.api.response.ProcessValidationIssueDTO.Severity;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 流程发布预检服务。
 *
 * <p>统一执行 BPMN 结构、办理人、表达式、动作、表单发布依赖与运行影响检查，输出可定位到
 * BPMN 元素的稳定问题码。预检与正式发布共用本服务，避免前端检查与服务端发布规则漂移。</p>
 */
@Service
@RequiredArgsConstructor
public class ProcessDefinitionPreflightService {

    private static final Set<String> FLOW_NODE_TYPES = Set.of(
            "startEvent", "endEvent", "intermediateCatchEvent", "intermediateThrowEvent",
            "userTask", "serviceTask", "sendTask", "receiveTask", "manualTask",
            "businessRuleTask", "scriptTask", "callActivity", "subProcess",
            "exclusiveGateway", "inclusiveGateway", "parallelGateway", "eventBasedGateway");

    private final ProcessDefinitionConfigMapper processMapper;
    private final ProcessVersionHistoryMapper versionHistoryMapper;
    private final NodeConfigMapper nodeConfigMapper;
    private final AssigneeConfigMapper assigneeConfigMapper;
    private final FlowActionDesignPort flowActionDesignPort;
    private final ProcessBpmnPublishSanitizer bpmnPublishSanitizer;
    private com.workflow.process.assignment.application.EmptyAssigneePolicyBpmnValidator emptyAssigneePolicyBpmnValidator;
    private com.workflow.process.task.application.operation.NodeOperationPolicyBpmnValidator
            nodeOperationPolicyBpmnValidator;
    private final ProcessPublishHistoryService publishHistoryService;
    private final RuntimeService runtimeService;

    /** 对当前持久化草稿执行完整发布预检。 */
    @Transactional(readOnly = true)
    public ProcessPublishPreviewDTO preview(String processId) {
        ProcessDefinitionConfig config = processMapper.selectById(processId);
        if (config == null) {
            throw new IllegalArgumentException("Process not found: " + processId);
        }
        return preview(config);
    }

    /**
     * 对已锁定或已读取的流程草稿执行预检。
     *
     * <p>正式发布在数据库行锁内调用该方法，确保预检结论和实际部署内容一致。</p>
     */
    public ProcessPublishPreviewDTO preview(ProcessDefinitionConfig config) {
        List<ProcessValidationIssueDTO> issues = new ArrayList<>();
        String bpmnXml = config.getBpmnXml();
        ParsedBpmn parsed = null;

        if (!StringUtils.hasText(bpmnXml)) {
            addIssue(issues, "BPMN_XML_REQUIRED", Severity.BLOCKER, null, null,
                    "流程尚未配置 BPMN XML", "请先在流程设计器完成流程图并保存草稿", config.getId());
        } else {
            try {
                parsed = parse(bpmnXml);
                validateStructure(config, parsed, issues);
                validateAssignments(config, parsed, issues);
            } catch (Exception exception) {
                addIssue(issues, "BPMN_XML_INVALID", Severity.BLOCKER, null, null,
                        "BPMN XML 无法解析: " + safeMessage(exception),
                        "请修复 XML 结构或重新保存流程图", config.getId());
            }

            // 可执行扩展白名单与发布净化器必须在预检阶段运行，防止部署时才暴露安全或依赖问题。
            try {
                BpmnExecutableContentValidator.validate(bpmnXml);
                if (emptyAssigneePolicyBpmnValidator != null) {
                    emptyAssigneePolicyBpmnValidator.validate(bpmnXml);
                }
                if (nodeOperationPolicyBpmnValidator != null) {
                    nodeOperationPolicyBpmnValidator.validate(bpmnXml);
                }
                bpmnPublishSanitizer.sanitize(
                        bpmnXml,
                        config.getProcessKey(),
                        config.getId());
            } catch (RuntimeException exception) {
                addIssue(issues, stableCode(exception, "BPMN_EXECUTABLE_INVALID"),
                        Severity.BLOCKER, elementIdFrom(exception), null,
                        safeMessage(exception), "请按问题提示修复对应节点的执行配置", config.getId());
            }
        }

        try {
            flowActionDesignPort.validateForPublish(config.getId());
        } catch (RuntimeException exception) {
            addIssue(issues, stableCode(exception, "FLOW_ACTION_INVALID"), Severity.BLOCKER,
                    elementIdFrom(exception), null, safeMessage(exception),
                    "请检查流程动作的触发时机、接口和输入输出映射", config.getId());
        }

        try {
            publishHistoryService.prepareNodeFormsSnapshot(config.getId());
        } catch (RuntimeException exception) {
            addIssue(issues, stableCode(exception, "NODE_FORM_DEPENDENCY_INVALID"), Severity.BLOCKER,
                    elementIdFrom(exception), null, safeMessage(exception),
                    "请发布或重新绑定节点所引用的表单版本", config.getId());
        }

        List<ProcessVersionHistory> versions = safeVersions(config.getId());
        ProcessDefinitionDiffDTO diff = buildDiff(config, parsed, versions);
        long activeInstances = activeInstanceCount(config, issues);

        issues.sort(Comparator
                .comparing((ProcessValidationIssueDTO value) -> severityOrder(value.severity()))
                .thenComparing(ProcessValidationIssueDTO::code)
                .thenComparing(value -> value.elementId() == null ? "" : value.elementId()));
        long blockers = issues.stream().filter(ProcessValidationIssueDTO::blocking).count();
        long warnings = issues.stream().filter(value -> value.severity() == Severity.WARNING).count();
        long revision = revisionOf(config);
        String draftHash = draftHashOf(config);
        String previewToken = previewToken(config.getId(), revision, draftHash, issues, diff);

        return new ProcessPublishPreviewDTO(
                config.getId(), revision, draftHash, basePublishedVersionOf(config),
                blockers == 0, blockers, warnings, List.copyOf(issues), diff,
                activeInstances, versions.size(), previewToken, Instant.now());
    }

    /** 返回当前草稿的结构化差异。 */
    @Transactional(readOnly = true)
    public ProcessDefinitionDiffDTO diff(String processId) {
        ProcessDefinitionConfig config = processMapper.selectById(processId);
        if (config == null) {
            throw new IllegalArgumentException("Process not found: " + processId);
        }
        ParsedBpmn parsed = null;
        if (StringUtils.hasText(config.getBpmnXml())) {
            try {
                parsed = parse(config.getBpmnXml());
            } catch (Exception ignored) {
                // XML 解析错误由 validate/preview 返回阻断项，差异接口仍返回元数据变化。
            }
        }
        return buildDiff(config, parsed, safeVersions(processId));
    }

    private void validateStructure(
            ProcessDefinitionConfig config,
            ParsedBpmn parsed,
            List<ProcessValidationIssueDTO> issues) {
        if (parsed.processCount() == 0) {
            addIssue(issues, "BPMN_PROCESS_MISSING", Severity.BLOCKER, null, "process",
                    "BPMN 中没有流程定义", "请创建至少一个可执行流程", config.getId());
        }
        if (parsed.startIds().isEmpty()) {
            addIssue(issues, "START_EVENT_MISSING", Severity.BLOCKER, null, "startEvent",
                    "流程缺少开始事件", "请添加开始事件并连接首个节点", config.getId());
        }
        if (parsed.endIds().isEmpty()) {
            addIssue(issues, "END_EVENT_MISSING", Severity.BLOCKER, null, "endEvent",
                    "流程缺少结束事件", "请为每条业务路径配置结束事件", config.getId());
        }
        for (String duplicateId : parsed.duplicateIds()) {
            addIssue(issues, "BPMN_ELEMENT_ID_DUPLICATE", Severity.BLOCKER, duplicateId, null,
                    "BPMN 元素 ID 重复: " + duplicateId,
                    "请重新生成重复元素的稳定 ID", config.getId());
        }
        addDataComponentModelingWarnings(config, parsed, issues);

        for (FlowEdge edge : parsed.edges()) {
            if (!parsed.flowNodes().containsKey(edge.sourceRef())) {
                addIssue(issues, "SEQUENCE_SOURCE_MISSING", Severity.BLOCKER, edge.id(), "sequenceFlow",
                        "连线来源节点不存在: " + edge.sourceRef(), "请重新连接或删除该连线", config.getId());
            }
            if (!parsed.flowNodes().containsKey(edge.targetRef())) {
                addIssue(issues, "SEQUENCE_TARGET_MISSING", Severity.BLOCKER, edge.id(), "sequenceFlow",
                        "连线目标节点不存在: " + edge.targetRef(), "请重新连接或删除该连线", config.getId());
            }
        }

        Map<String, List<FlowEdge>> outgoing = new HashMap<>();
        for (FlowEdge edge : parsed.edges()) {
            outgoing.computeIfAbsent(edge.sourceRef(), ignored -> new ArrayList<>()).add(edge);
        }
        for (Map.Entry<String, Element> entry : parsed.flowNodes().entrySet()) {
            String nodeId = entry.getKey();
            String type = localName(entry.getValue());
            List<FlowEdge> nodeOutgoing = outgoing.getOrDefault(nodeId, List.of());
            if ("exclusiveGateway".equals(type) && nodeOutgoing.size() > 1) {
                String defaultFlow = entry.getValue().getAttribute("default");
                if (!StringUtils.hasText(defaultFlow)) {
                    addIssue(issues, "GATEWAY_DEFAULT_FLOW_MISSING", Severity.BLOCKER, nodeId, type,
                            "排他网关存在多个出口但未配置默认分支",
                            "请选择一条连线作为默认分支", config.getId());
                }
                for (FlowEdge edge : nodeOutgoing) {
                    if (!edge.id().equals(defaultFlow) && !edge.hasCondition()) {
                        addIssue(issues, "GATEWAY_CONDITION_MISSING", Severity.BLOCKER, edge.id(), "sequenceFlow",
                                "排他网关的非默认连线缺少条件表达式",
                                "为该连线配置受控条件表达式", config.getId());
                    }
                }
            }
        }

        Set<String> reachable = reachableNodes(parsed);
        for (Map.Entry<String, Element> entry : parsed.flowNodes().entrySet()) {
            if (!reachable.contains(entry.getKey())) {
                addIssue(issues, "BPMN_NODE_UNREACHABLE", Severity.BLOCKER,
                        entry.getKey(), localName(entry.getValue()),
                        "节点无法从开始事件到达", "请补齐连线或删除孤立节点", config.getId());
            }
        }
    }

    /**
     * 标明 Flowable 仅保留模型、但本平台不会自动赋予运行时读写语义的数据组件。
     *
     * <p>这些提示不阻断发布，目的是避免用户把 DataStore 图元理解为持久化设施，或把普通
     * Activity 上的数据关联理解为自动变量映射。</p>
     */
    private void addDataComponentModelingWarnings(
            ProcessDefinitionConfig config,
            ParsedBpmn parsed,
            List<ProcessValidationIssueDTO> issues) {
        for (ModelingOnlyElement element : parsed.modelingOnlyElements()) {
            if ("dataStoreReference".equals(element.type())) {
                addIssue(
                        issues,
                        "BPMN_DATA_STORE_MODEL_ONLY",
                        Severity.WARNING,
                        element.id(),
                        element.type(),
                        "数据存储仅用于流程建模，不会自动持久化；实际读写需通过服务任务或实体接口实现",
                        "请配置服务任务、实体接口或其他明确的数据读写能力",
                        config.getId());
            } else {
                addIssue(
                        issues,
                        "BPMN_DATA_ASSOCIATION_MODEL_ONLY",
                        Severity.WARNING,
                        element.id(),
                        element.type(),
                        "普通任务上的数据关联仅保留模型语义，不会自动映射流程变量",
                        "请通过服务任务结果映射、流程动作或实体接口显式读写流程变量",
                        config.getId());
            }
        }
    }

    private void validateAssignments(
            ProcessDefinitionConfig config,
            ParsedBpmn parsed,
            List<ProcessValidationIssueDTO> issues) {
        List<NodeConfig> nodeConfigs = nodeConfigMapper.findByProcessConfigId(config.getId());
        Map<String, NodeConfig> byNodeId = new HashMap<>();
        if (nodeConfigs != null) {
            for (NodeConfig nodeConfig : nodeConfigs) {
                if (StringUtils.hasText(nodeConfig.getNodeId())) {
                    byNodeId.put(nodeConfig.getNodeId(), nodeConfig);
                }
            }
        }
        for (Map.Entry<String, Element> entry : parsed.flowNodes().entrySet()) {
            if (!"userTask".equals(localName(entry.getValue()))) {
                continue;
            }
            NodeConfig nodeConfig = byNodeId.get(entry.getKey());
            if (nodeConfig != null && Boolean.TRUE.equals(nodeConfig.getSkipNode())) {
                continue;
            }
            if (hasXmlAssignment(entry.getValue()) || hasStoredAssignment(nodeConfig)) {
                continue;
            }
            addIssue(issues, "USER_TASK_ASSIGNEE_MISSING", Severity.BLOCKER,
                    entry.getKey(), "userTask", "用户任务没有配置办理人来源",
                    "请配置用户、角色、部门、动态解析器或明确的空办理人策略", config.getId());
        }
    }

    private boolean hasStoredAssignment(NodeConfig nodeConfig) {
        if (nodeConfig == null || !StringUtils.hasText(nodeConfig.getId())) {
            return false;
        }
        List<AssigneeConfig> assignees = assigneeConfigMapper.findByNodeConfigId(nodeConfig.getId());
        return assignees != null && assignees.stream().anyMatch(value ->
                value.getAssigneeType() != null && StringUtils.hasText(value.getAssigneeValue()));
    }

    private boolean hasXmlAssignment(Element userTask) {
        if (hasAssignmentAttribute(userTask)) {
            return true;
        }
        NodeList descendants = userTask.getElementsByTagName("*");
        for (int index = 0; index < descendants.getLength(); index++) {
            if (hasAssignmentAttribute((Element) descendants.item(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAssignmentAttribute(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            String name = localName(attribute).toLowerCase(Locale.ROOT);
            if ((name.contains("assignee") || name.contains("candidate")
                    || name.contains("resolver") || name.equals("collection"))
                    && StringUtils.hasText(attribute.getNodeValue())) {
                return true;
            }
        }
        return false;
    }

    private ProcessDefinitionDiffDTO buildDiff(
            ProcessDefinitionConfig config,
            ParsedBpmn current,
            List<ProcessVersionHistory> versions) {
        ProcessVersionHistory base = versions.isEmpty() ? null : versions.get(0);
        ParsedBpmn previous = null;
        if (base != null && StringUtils.hasText(base.getBpmnXml())) {
            try {
                previous = parse(base.getBpmnXml());
            } catch (Exception ignored) {
                // 历史 XML 无法解析时按全部变化处理，不能阻断当前草稿预检。
            }
        }

        List<String> metadataChanges = new ArrayList<>();
        if (base == null || !safeEquals(config.getProcessName(), base.getProcessName())) {
            metadataChanges.add("processName");
        }
        Map<String, String> currentElements = current == null ? Map.of() : current.fingerprints();
        Map<String, String> previousElements = previous == null ? Map.of() : previous.fingerprints();
        List<String> added = difference(currentElements.keySet(), previousElements.keySet());
        List<String> removed = difference(previousElements.keySet(), currentElements.keySet());
        List<String> changed = currentElements.keySet().stream()
                .filter(previousElements::containsKey)
                .filter(key -> !currentElements.get(key).equals(previousElements.get(key)))
                .sorted().toList();
        boolean hasChanges = base == null || !metadataChanges.isEmpty()
                || !added.isEmpty() || !removed.isEmpty() || !changed.isEmpty();
        return new ProcessDefinitionDiffDTO(
                base == null || base.getVersion() == null ? 0 : base.getVersion(),
                hasChanges, List.copyOf(metadataChanges), added, removed, changed);
    }

    private long activeInstanceCount(
            ProcessDefinitionConfig config,
            List<ProcessValidationIssueDTO> issues) {
        try {
            return runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey(config.getProcessKey()).active().count();
        } catch (RuntimeException exception) {
            addIssue(issues, "PROCESS_IMPACT_UNAVAILABLE", Severity.WARNING, null, null,
                    "暂时无法统计活跃流程实例", "发布前请确认流程引擎可用并重新执行预检", config.getId());
            return -1L;
        }
    }

    private Set<String> reachableNodes(ParsedBpmn parsed) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (FlowEdge edge : parsed.edges()) {
            if (parsed.flowNodes().containsKey(edge.sourceRef())
                    && parsed.flowNodes().containsKey(edge.targetRef())) {
                adjacency.computeIfAbsent(edge.sourceRef(), ignored -> new ArrayList<>())
                        .add(edge.targetRef());
            }
        }
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(parsed.startIds());
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (visited.add(current)) {
                queue.addAll(adjacency.getOrDefault(current, List.of()));
            }
        }
        return visited;
    }

    private ParsedBpmn parse(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(bpmnXml)));

        Map<String, Element> flowNodes = new LinkedHashMap<>();
        Map<String, String> fingerprints = new TreeMap<>();
        Set<String> starts = new LinkedHashSet<>();
        Set<String> ends = new LinkedHashSet<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> duplicateIds = new LinkedHashSet<>();
        List<FlowEdge> edges = new ArrayList<>();
        List<ModelingOnlyElement> modelingOnlyElements = new ArrayList<>();
        int processCount = 0;

        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            String type = localName(element);
            if ("process".equals(type)) {
                processCount++;
            }
            String id = element.getAttribute("id");
            if (StringUtils.hasText(id) && !seenIds.add(id)) {
                duplicateIds.add(id);
            }
            if (StringUtils.hasText(id)
                    && ("dataStoreReference".equals(type)
                    || "dataInputAssociation".equals(type)
                    || "dataOutputAssociation".equals(type))) {
                modelingOnlyElements.add(new ModelingOnlyElement(id, type));
            }
            if (FLOW_NODE_TYPES.contains(type) && StringUtils.hasText(id)) {
                flowNodes.put(id, element);
                fingerprints.put(id, fingerprint(element));
                if ("startEvent".equals(type)) {
                    starts.add(id);
                } else if ("endEvent".equals(type)) {
                    ends.add(id);
                }
            } else if ("sequenceFlow".equals(type) && StringUtils.hasText(id)) {
                FlowEdge edge = new FlowEdge(
                        id, element.getAttribute("sourceRef"), element.getAttribute("targetRef"),
                        hasCondition(element));
                edges.add(edge);
                fingerprints.put(id, fingerprint(element));
            }
        }
        return new ParsedBpmn(
                processCount, Map.copyOf(flowNodes), List.copyOf(edges), Set.copyOf(starts),
                Set.copyOf(ends), Set.copyOf(duplicateIds), Map.copyOf(fingerprints),
                List.copyOf(modelingOnlyElements));
    }

    private boolean hasCondition(Element sequenceFlow) {
        NodeList descendants = sequenceFlow.getElementsByTagName("*");
        for (int index = 0; index < descendants.getLength(); index++) {
            if ("conditionExpression".equals(localName(descendants.item(index)))
                    && StringUtils.hasText(descendants.item(index).getTextContent())) {
                return true;
            }
        }
        return false;
    }

    private String fingerprint(Element element) {
        StringBuilder result = new StringBuilder();
        appendFingerprint(element, result);
        return sha256(result.toString());
    }

    private void appendFingerprint(Element element, StringBuilder result) {
        result.append('<').append(localName(element));
        Map<String, String> attributes = new TreeMap<>();
        NamedNodeMap namedNodeMap = element.getAttributes();
        for (int index = 0; index < namedNodeMap.getLength(); index++) {
            Node attribute = namedNodeMap.item(index);
            if (!"id".equals(localName(attribute)) && !attribute.getNodeName().startsWith("xmlns")) {
                attributes.put(attribute.getNodeName(), attribute.getNodeValue());
            }
        }
        attributes.forEach((key, value) -> result.append('|').append(key).append('=').append(value));
        result.append('>');
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                appendFingerprint(childElement, result);
            } else if (child.getNodeType() == Node.TEXT_NODE && StringUtils.hasText(child.getTextContent())) {
                result.append(child.getTextContent().trim());
            }
        }
        result.append("</").append(localName(element)).append('>');
    }

    private List<ProcessVersionHistory> safeVersions(String processId) {
        List<ProcessVersionHistory> versions = versionHistoryMapper.findByProcessConfigId(processId);
        return versions == null ? List.of() : versions;
    }

    private String previewToken(
            String processId,
            long revision,
            String draftHash,
            List<ProcessValidationIssueDTO> issues,
            ProcessDefinitionDiffDTO diff) {
        StringBuilder input = new StringBuilder("PROCESS_PUBLISH_PREVIEW_V1|")
                .append(processId).append('|').append(revision).append('|')
                .append(draftHash).append('|').append(diff.baseVersion()).append('|').append(diff.changed());
        for (ProcessValidationIssueDTO issue : issues) {
            input.append('|').append(issue.code()).append(':').append(issue.severity())
                    .append(':').append(issue.elementId());
        }
        return sha256(input.toString());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private void addIssue(
            List<ProcessValidationIssueDTO> issues,
            String code,
            Severity severity,
            String elementId,
            String elementType,
            String message,
            String suggestion,
            String processId) {
        boolean duplicate = issues.stream().anyMatch(value ->
                value.code().equals(code) && safeEquals(value.elementId(), elementId));
        if (duplicate) {
            return;
        }
        String route = elementId == null
                ? "/process/design/" + processId
                : "/process/design/" + processId + "?elementId=" + elementId;
        issues.add(new ProcessValidationIssueDTO(
                code, severity, severity == Severity.BLOCKER,
                elementId, elementType, message, suggestion, route));
    }

    private List<String> difference(Set<String> left, Set<String> right) {
        return left.stream().filter(value -> !right.contains(value)).sorted().toList();
    }

    private long revisionOf(ProcessDefinitionConfig config) {
        return config.getDraftRevision() == null || config.getDraftRevision() < 1
                ? 1L : config.getDraftRevision();
    }

    private int basePublishedVersionOf(ProcessDefinitionConfig config) {
        if (config.getBasePublishedVersion() != null) {
            return Math.max(config.getBasePublishedVersion(), 0);
        }
        return config.getVersion() == null ? 0 : Math.max(config.getVersion(), 0);
    }

    private String draftHashOf(ProcessDefinitionConfig config) {
        return StringUtils.hasText(config.getDraftHash())
                ? config.getDraftHash() : ProcessDraftHashSupport.hash(config);
    }

    private int severityOrder(Severity severity) {
        return switch (severity) {
            case BLOCKER -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }

    private String stableCode(RuntimeException exception, String fallback) {
        String message = exception.getMessage();
        if (message == null) {
            return fallback;
        }
        int separator = message.indexOf(':');
        String candidate = separator < 0 ? message : message.substring(0, separator);
        return candidate.matches("[A-Z][A-Z0-9_]{2,80}") ? candidate : fallback;
    }

    private String elementIdFrom(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return null;
        }
        int start = message.indexOf("element=");
        if (start < 0) {
            return null;
        }
        String value = message.substring(start + "element=".length()).trim();
        int end = value.indexOf(' ');
        return end < 0 ? value : value.substring(0, end);
    }

    private String safeMessage(Throwable exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private boolean safeEquals(Object left, Object right) {
        return java.util.Objects.equals(left, right);
    }

    private static String localName(Node node) {
        if (node.getLocalName() != null) {
            return node.getLocalName();
        }
        String name = node.getNodeName();
        int separator = name.indexOf(':');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private record FlowEdge(String id, String sourceRef, String targetRef, boolean hasCondition) {
    }

    private record ModelingOnlyElement(String id, String type) {
    }

    private record ParsedBpmn(
            int processCount,
            Map<String, Element> flowNodes,
            List<FlowEdge> edges,
            Set<String> startIds,
            Set<String> endIds,
            Set<String> duplicateIds,
            Map<String, String> fingerprints,
            List<ModelingOnlyElement> modelingOnlyElements) {
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setEmptyAssigneePolicyBpmnValidator(
            com.workflow.process.assignment.application.EmptyAssigneePolicyBpmnValidator validator) {
        this.emptyAssigneePolicyBpmnValidator = validator;
    }

    /** 可选注入矩阵校验器，保留现有轻量单元测试的构造函数兼容性。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNodeOperationPolicyBpmnValidator(
            com.workflow.process.task.application.operation.NodeOperationPolicyBpmnValidator validator) {
        this.nodeOperationPolicyBpmnValidator = validator;
    }
}
