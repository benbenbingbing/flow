package com.workflow.process.definition.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver;
import com.workflow.process.task.application.nextapproval.NextApproverSelectionNormalizer;
import com.workflow.process.task.infrastructure.MultiInstanceVariableNames;
import com.workflow.process.definition.application.port.FlowActionDesignPort;
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
import java.util.Collection;
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
            "task", "userTask", "serviceTask", "sendTask", "receiveTask", "manualTask",
            "businessRuleTask", "scriptTask", "callActivity", "subProcess",
            "exclusiveGateway", "inclusiveGateway", "parallelGateway", "eventBasedGateway");

    /**
     * Flowable 默认会在当前命令内执行完并沿出线继续的节点。
     *
     * <p>事件网关、捕获事件、接收任务和普通用户任务都会形成等待态，因此不得放入此集合。
     * 调用活动是否同步返回取决于当前草稿不可见的被调用模型，同样不作为确定的同步节点；
     * 嵌入子流程和可触发服务任务则需要结合节点内容单独判断。</p>
     */
    private static final Set<String> SYNCHRONOUS_FLOW_NODE_TYPES = Set.of(
            "task", "serviceTask", "sendTask", "manualTask",
            "businessRuleTask", "scriptTask", "intermediateThrowEvent",
            "exclusiveGateway", "inclusiveGateway", "parallelGateway");

    /** Flowable 中需要外部完成信号的服务任务类型。 */
    private static final Set<String> WAITING_SERVICE_TASK_TYPES = Set.of(
            "external", "external-worker", "case");

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
    private final ObjectMapper objectMapper;

    /**
     * 对当前持久化草稿执行完整发布预检。
     *
     * <p>预检不写入业务数据，但生成节点表单快照时需要锁定表单配置行，与表单热修复
     * 发布保持一致的并发边界，因此必须使用允许 SELECT FOR UPDATE 的非只读事务。
     * 行锁在本次预检结束时释放，正式发布仍在自己的事务内重新校验并持锁。</p>
     *
     * @param processId 流程定义配置 ID
     * @return 当前草稿的发布预检结果
     * @throws IllegalArgumentException 流程定义不存在时抛出
     */
    @Transactional
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
     *
     * @param config 配置内容，决定后续预览的处理规则
     * @return 处理后的预览结果，供调用方继续处理
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

    /**
     * 返回当前草稿的结构化差异。
     *
     * @param processId 流程ID，后续用于处理差异时定位或关联目标
     * @return 处理后的差异结果，供调用方继续处理
     */
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

    /**
     * 校验{@code structure}；不满足约束时阻止后续处理。
     *
     * @param config 配置内容，决定后续{@code structure}的处理规则
     * @param parsed {@code parsed}，作为 {@code addDataComponentModelingWarnings} 的输入影响后续处理
     * @param issues {@code issues}，作为 {@code addIssue} 的输入影响后续处理
     */
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
                // 默认分支是所有条件未命中时的可选兜底；条件已覆盖业务取值时无需配置。
                // 预检不推断表达式覆盖范围，仅校验非默认出线必须明确配置条件。
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
        validateAlwaysSkipCycles(config, parsed, reachable, issues);
        for (Map.Entry<String, Element> entry : parsed.flowNodes().entrySet()) {
            if (!reachable.contains(entry.getKey())) {
                addIssue(issues, "BPMN_NODE_UNREACHABLE", Severity.BLOCKER,
                        entry.getKey(), localName(entry.getValue()),
                        "节点无法从开始事件到达", "请补齐连线或删除孤立节点", config.getId());
            }
        }
    }

    /**
     * 阻断由始终跳过用户任务与同步穿透节点组成的可达环。
     *
     * <p>原生 {@code skipExpression} 会在当前引擎命令内立即继续；此类环
     * 没有任务待办或其他等待状态来切断调用链，可导致启动/完成请求无限
     * 自旋。普通用户任务、接收任务、捕获事件和事件网关会形成等待边界；调用活动
     * 是否同步返回无法仅由当前草稿证明，按非确定同步节点处理。嵌入子流程只有在其
     * 内部没有等待节点时才视为同步穿透。</p>
     *
     * @param config 配置内容，决定后续{@code always}跳过{@code cycles}的处理规则
     * @param parsed {@code parsed}，作为 {@code findAlwaysSkipCycle} 的输入影响后续处理
     * @param reachable {@code reachable}，供本方法校验{@code always}跳过{@code cycles}时使用
     * @param issues {@code issues}，作为 {@code addIssue} 的输入影响后续处理
     */
    private void validateAlwaysSkipCycles(
            ProcessDefinitionConfig config,
            ParsedBpmn parsed,
            Set<String> reachable,
            List<ProcessValidationIssueDTO> issues) {
        Set<String> synchronousNodes = new HashSet<>();
        for (Map.Entry<String, Element> entry : parsed.flowNodes().entrySet()) {
            if (reachable.contains(entry.getKey())
                    && isSynchronousSkipPathNode(entry.getValue())) {
                synchronousNodes.add(entry.getKey());
            }
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        for (FlowEdge edge : parsed.edges()) {
            if (synchronousNodes.contains(edge.sourceRef())
                    && synchronousNodes.contains(edge.targetRef())) {
                adjacency.computeIfAbsent(
                                edge.sourceRef(), ignored -> new ArrayList<>())
                        .add(edge.targetRef());
            }
        }
        adjacency.values().forEach(values -> values.sort(String::compareTo));

        Map<String, Integer> states = new HashMap<>();
        List<String> path = new ArrayList<>();
        List<String> orderedNodes = synchronousNodes.stream().sorted().toList();
        for (String nodeId : orderedNodes) {
            if (states.getOrDefault(nodeId, 0) != 0) {
                continue;
            }
            List<String> cycle = findAlwaysSkipCycle(
                    nodeId, parsed, adjacency, states, path);
            if (cycle == null) {
                continue;
            }
            String skippedNodeId = cycle.stream()
                    .filter(candidate -> {
                        Element element = parsed.flowNodes().get(candidate);
                        return element != null
                                && "userTask".equals(localName(element))
                                && isAlwaysSkipped(element);
                    })
                    .sorted()
                    .findFirst()
                    .orElse(cycle.get(0));
            addIssue(
                    issues,
                    "ALWAYS_SKIP_CYCLE",
                    Severity.BLOCKER,
                    skippedNodeId,
                    "userTask",
                    "始终跳过节点位于可达循环中，同步执行会无限流转",
                    "请删除回边、取消始终跳过，或在环内增加人工等待节点",
                    config.getId());
            return;
        }
    }

    /**
     * 判断是否{@code synchronous}跳过路径节点；判断结果决定调用方的后续分支。
     *
     * @param element 元素，作为 {@code localName} 的输入影响后续处理
     * @return {@code synchronous}跳过路径节点条件成立时为 true，否则为 false
     */
    private boolean isSynchronousSkipPathNode(Element element) {
        String type = localName(element);
        if ("userTask".equals(type)) {
            return isAlwaysSkipped(element);
        }
        if ("serviceTask".equals(type)) {
            return !isWaitingServiceTask(element);
        }
        if ("subProcess".equals(type)) {
            return isSynchronouslyCompletingSubProcess(element);
        }
        return SYNCHRONOUS_FLOW_NODE_TYPES.contains(type);
    }

    /**
     * 可触发、外部工作者和 CMMN case 服务任务均需等待外部完成，不能视为同步穿透。
     *
     * @param serviceTask 服务任务，作为 {@code valueOrEmpty} 的输入影响后续处理
     * @return {@code waiting}服务任务条件成立时为 true，否则为 false
     */
    private boolean isWaitingServiceTask(Element serviceTask) {
        if (Boolean.parseBoolean(valueOrEmpty(
                attributeByLocalName(serviceTask, "triggerable")).trim())) {
            return true;
        }
        String type = valueOrEmpty(
                attributeByLocalName(serviceTask, "type"))
                .trim()
                .toLowerCase(Locale.ROOT);
        return WAITING_SERVICE_TASK_TYPES.contains(type);
    }

    /**
     * 判断嵌入子流程是否能在当前命令内完成。
     *
     * <p>子流程自身不是天然等待态；若内部仅含同步任务、路由节点或始终跳过任务，
     * Flowable 会在同一 agenda 中执行到子流程出口。只要存在普通用户任务、接收任务、
     * 捕获事件、事件网关、调用活动或等待型服务任务，就保守地把整个子流程视为等待边界，
     * 避免阻断含真实人工/外部等待的合法业务回路。</p>
     *
     * @param subProcess 子级流程，供本方法判断是否{@code synchronously}{@code completing}子级流程时使用
     * @return {@code synchronously}{@code completing}子级流程条件成立时为 true，否则为 false
     */
    private boolean isSynchronouslyCompletingSubProcess(Element subProcess) {
        if (Boolean.parseBoolean(valueOrEmpty(
                attributeByLocalName(subProcess, "triggeredByEvent")).trim())) {
            return false;
        }
        NodeList descendants = subProcess.getElementsByTagName("*");
        for (int index = 0; index < descendants.getLength(); index++) {
            Element descendant = (Element) descendants.item(index);
            String type = localName(descendant);
            if (("userTask".equals(type) && !isAlwaysSkipped(descendant))
                    || "receiveTask".equals(type)
                    || "intermediateCatchEvent".equals(type)
                    || "eventBasedGateway".equals(type)
                    || "callActivity".equals(type)
                    || ("serviceTask".equals(type)
                    && isWaitingServiceTask(descendant))
                    || ("subProcess".equals(type)
                    && !isSynchronouslyCompletingSubProcess(descendant))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 以三色 DFS 定位候选子图中第一个包含始终跳过任务的环。
     *
     * @param nodeId 节点ID，后续用于查询{@code always}跳过{@code cycle}时定位或关联目标
     * @param parsed {@code parsed}，供本方法查询{@code always}跳过{@code cycle}时使用
     * @param adjacency {@code adjacency}，供本方法查询{@code always}跳过{@code cycle}时使用
     * @param states {@code states}，供本方法查询{@code always}跳过{@code cycle}时使用
     * @param path 路径，作为 {@code path.subList} 的输入影响后续处理
     * @return 流程定义{@code preflight}集合，供调用方遍历或展示
     */
    private List<String> findAlwaysSkipCycle(
            String nodeId,
            ParsedBpmn parsed,
            Map<String, List<String>> adjacency,
            Map<String, Integer> states,
            List<String> path) {
        states.put(nodeId, 1);
        path.add(nodeId);
        for (String target : adjacency.getOrDefault(nodeId, List.of())) {
            int targetState = states.getOrDefault(target, 0);
            if (targetState == 0) {
                List<String> nested = findAlwaysSkipCycle(
                        target, parsed, adjacency, states, path);
                if (nested != null) {
                    return nested;
                }
                continue;
            }
            if (targetState != 1) {
                continue;
            }
            int cycleStart = path.lastIndexOf(target);
            if (cycleStart < 0) {
                continue;
            }
            List<String> cycle = new ArrayList<>(
                    path.subList(cycleStart, path.size()));
            boolean containsAlwaysSkip = cycle.stream().anyMatch(candidate -> {
                Element element = parsed.flowNodes().get(candidate);
                return element != null
                        && "userTask".equals(localName(element))
                        && isAlwaysSkipped(element);
            });
            if (containsAlwaysSkip) {
                return cycle;
            }
        }
        path.remove(path.size() - 1);
        states.put(nodeId, 2);
        return null;
    }

    /**
     * 标明 Flowable 仅保留模型、但本平台不会自动赋予运行时读写语义的数据组件。
     *
     * <p>这些提示不阻断发布，目的是避免用户把 DataStore 图元理解为持久化设施，或把普通
     * Activity 上的数据关联理解为自动变量映射。</p>
     *
     * @param config 配置内容，决定后续数据组件{@code modeling}{@code warnings}的处理规则
     * @param parsed {@code parsed}，供本方法添加数据组件{@code modeling}{@code warnings}时使用
     * @param issues {@code issues}，作为 {@code addIssue} 的输入影响后续处理
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

    /**
     * 校验分配集合；不满足约束时阻止后续处理。
     *
     * @param config 配置内容，决定后续分配集合的处理规则
     * @param parsed {@code parsed}，供本方法校验分配集合时使用
     * @param issues {@code issues}，作为 {@code addIssue} 的输入影响后续处理
     */
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
            if (isAlwaysSkipped(entry.getValue())) {
                continue;
            }
            try {
                Element userTask = entry.getValue();
                // 扩展配置属于当前草稿的权威来源；清空或切换类型后，不能让旧表中的
                // 静态办理人记录掩盖缺失。仅未使用扩展配置的历史节点保留表记录兜底。
                if (hasXmlAssignment(userTask)
                        || (!hasAssignmentConfig(userTask) && hasStoredAssignment(nodeConfig))) {
                    continue;
                }
            } catch (IllegalArgumentException exception) {
                addIssue(issues, "USER_TASK_ASSIGNMENT_CONFIG_INVALID", Severity.BLOCKER,
                        entry.getKey(), "userTask", "办理人配置无效: " + safeMessage(exception),
                        "请重新检查并保存该节点的办理人配置", config.getId());
                continue;
            }
            addIssue(issues, "USER_TASK_ASSIGNEE_MISSING", Severity.BLOCKER,
                    entry.getKey(), "userTask", "用户任务没有配置办理人来源",
                    "请配置固定人员、用户组、角色、人员接口、节点审批人引用或有效表达式", config.getId());
        }
    }

    /**
     * 判断当前草稿 XML 是否明确配置为始终跳过。
     *
     * <p>预检必须以当前 XML 为准，不能依赖可能来自上一版草稿的 NodeConfig；
     * 条件跳过在表达式为 false 时仍会创建任务，因此必须配置办理人兜底。字面量
     * {@code ${true}} / {@code #{true}} 以及历史 {@code skipNodeEnabled} 表达式在
     * 运行时客观恒真，即使设计态错误残留了 {@code skipNode=false} 也必须按始终跳过处理。</p>
     *
     * @param userTask 用户任务，作为 {@code extensionPropertyValue} 的输入影响后续处理
     * @return {@code always}{@code skipped}条件成立时为 true，否则为 false
     */
    private boolean isAlwaysSkipped(Element userTask) {
        String skipNode = extensionPropertyValue(
                userTask, "skipNode");
        if (skipNode != null
                && Boolean.parseBoolean(skipNode.trim())) {
            return true;
        }
        String expression = attributeByLocalName(
                userTask, "skipExpression");
        if (!StringUtils.hasText(expression)) {
            NodeList descendants = userTask.getElementsByTagName("*");
            for (int index = 0; index < descendants.getLength(); index++) {
                Element descendant = (Element) descendants.item(index);
                if ("skipExpression".equals(localName(descendant))) {
                    expression = descendant.getTextContent();
                    break;
                }
            }
        }
        return StringUtils.hasText(expression)
                && expression.trim().matches(
                        "(?i)^[#$]\\{\\s*(?:true|skipNodeEnabled)\\s*}$");
    }

    /**
     * 生成扩展属性值文本，供后续匹配或展示。
     *
     * @param element 元素，供本方法处理扩展属性值时使用
     * @param propertyName 属性名称，后续用于处理扩展属性值时匹配或展示
     * @return 处理后的扩展属性值文本，供调用方比较或展示
     */
    private String extensionPropertyValue(
            Element element,
            String propertyName) {
        NodeList descendants = element.getElementsByTagName("*");
        for (int index = 0; index < descendants.getLength(); index++) {
            Element descendant = (Element) descendants.item(index);
            if (!"property".equals(localName(descendant))) {
                continue;
            }
            if (propertyName.equals(
                    attributeByLocalName(descendant, "name"))) {
                return attributeByLocalName(descendant, "value");
            }
        }
        return null;
    }

    /**
     * 生成属性本地名称文本，供后续匹配或展示。
     *
     * @param element 元素，供本方法处理属性本地名称时使用
     * @param expectedName 预期名称，后续用于处理属性本地名称时匹配或展示
     * @return 处理后的属性本地名称文本，供调用方比较或展示
     */
    private String attributeByLocalName(
            Element element,
            String expectedName) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (expectedName.equals(localName(attribute))) {
                return attribute.getNodeValue();
            }
        }
        return null;
    }

    /**
     * 生成值或空文本，供后续匹配或展示。
     *
     * @param value 待处理值或空的原始输入，结果供调用方继续使用
     * @return 处理后的值或空文本，供调用方比较或展示
     */
    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 判断是否具有已存储分配；判断结果决定调用方的后续分支。
     *
     * @param nodeConfig 节点配置内容，决定后续已存储分配的处理规则
     * @return 已存储分配条件成立时为 true，否则为 false
     */
    private boolean hasStoredAssignment(NodeConfig nodeConfig) {
        if (nodeConfig == null || !StringUtils.hasText(nodeConfig.getId())) {
            return false;
        }
        List<AssigneeConfig> assignees = assigneeConfigMapper.findByNodeConfigId(nodeConfig.getId());
        return assignees != null && assignees.stream().anyMatch(value ->
                value.getAssigneeType() != null && StringUtils.hasText(value.getAssigneeValue()));
    }

    /**
     * 按运行时使用的基础配置及历史多实例优先级识别办理人来源。
     * 这里只检查声明，不调用人员接口；目录、用途、参数和节点引用图仍由发布净化器校验。
     *
     * @param userTask 用户任务，作为 {@code LegacyMultiInstanceAssignmentParser.mergeConfigs} 的输入影响后续处理
     * @return XML分配条件成立时为 true，否则为 false
     */
    private boolean hasXmlAssignment(Element userTask) {
        Map<String, Object> assignment = LegacyMultiInstanceAssignmentParser.mergeConfigs(
                readAssignmentConfig(userTask, "assigneeConfig"),
                readAssignmentConfig(userTask, "multiInstanceConfig"));
        Element loop = null;
        for (Node child = userTask.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && "multiInstanceLoopCharacteristics".equals(localName(element))) {
                loop = element;
                break;
            }
        }
        boolean multiInstance = loop != null;
        if (multiInstance && hasEditableIndependentSource(assignment)) {
            // 前序人工改选的独立受控范围可提供会签参与人，不要求预填默认人员。
            return true;
        }
        if (LegacyMultiInstanceAssignmentParser.usesLegacyMultiInstanceAssignment(assignment, multiInstance)) {
            var legacy = LegacyMultiInstanceAssignmentParser.parse(assignment);
            return !legacy.resolver() || StringUtils.hasText(legacy.resolverCode());
        }
        if (NodeAssignmentReferenceResolver.isNodeReference(assignment)) {
            return StringUtils.hasText(NodeAssignmentReferenceResolver.referencedNodeId(assignment));
        }
        String type = String.valueOf(assignment.getOrDefault("assigneeType", ""))
                .trim().toLowerCase(Locale.ROOT);
        if (Set.of("interface", "resolver").contains(type)) {
            // 实体用户关系字段、相对组织职务在保存时同样投影为 interface + resolverCode。
            return LegacyMultiInstanceAssignmentParser.effectiveResolver(assignment, multiInstance).configured();
        }
        if (multiInstance && !type.isEmpty()) {
            boolean configured = switch (type) {
                case "user", "candidate" -> hasConfiguredValues(assignment.get("assigneeValue"))
                        || hasConfiguredValues(assignment.get("candidateUsers"))
                        || hasConfiguredValues(assignment.get("candidateGroups"));
                case "group", "role" -> hasConfiguredValues(assignment.get("assigneeValue"))
                        || hasConfiguredValues(assignment.get("candidateGroups"));
                default -> false;
            };
            if (configured || "2".equals(String.valueOf(assignment.get("assignmentConfigVersion")))
                    || !Set.of("user", "candidate", "group", "role").contains(type)) {
                return configured;
            }
            // 历史多实例可从部署 XML 的字面量用户/组恢复；v2 只认基础 JSON，
            // 不能再借用残留的旧候选人或 collection 绕过空配置检查。
        }

        String assignee = attributeByLocalName(userTask, "assignee");
        String elementVariable = loop == null ? null : attributeByLocalName(loop, "elementVariable");
        boolean iterationAssignee = StringUtils.hasText(elementVariable)
                && StringUtils.hasText(assignee)
                && (assignee.trim().equals("${" + elementVariable + "}")
                || assignee.trim().equals("#{" + elementVariable + "}"));
        if ((!iterationAssignee && StringUtils.hasText(assignee))
                || hasConfiguredValues(attributeByLocalName(userTask, "candidateUsers"))
                || hasConfiguredValues(attributeByLocalName(userTask, "candidateGroups"))) {
            return true;
        }
        // 只保留原生业务 collection 兼容；系统生成的空集合和循环元素变量不是人员来源。
        String collection = loop == null ? null : attributeByLocalName(loop, "collection");
        return StringUtils.hasText(collection)
                && !collection.contains(MultiInstanceVariableNames.COLLECTION_VARIABLE_PREFIX)
                && !collection.contains(MultiInstanceVariableNames.ENTRY_DYNAMIC_COLLECTION_LITERAL);
    }

    /**
     * 判断是否具有分配配置；判断结果决定调用方的后续分支。
     *
     * @param userTask 用户任务，作为 {@code extensionPropertyValue} 的输入影响后续处理
     * @return 分配配置条件成立时为 true，否则为 false
     */
    private boolean hasAssignmentConfig(Element userTask) {
        return extensionPropertyValue(userTask, "assigneeConfig") != null
                || extensionPropertyValue(userTask, "multiInstanceConfig") != null;
    }

    /**
     * 空属性视为未填；非法 JSON 定位为节点配置问题，避免误报整个 BPMN 无法解析。
     *
     * @param userTask 用户任务，作为 {@code extensionPropertyValue} 的输入影响后续处理
     * @param propertyName 属性名称，后续用于读取分配配置时匹配或展示
     * @return 分配配置键值结果，供调用方继续处理
     */
    private Map<String, Object> readAssignmentConfig(Element userTask, String propertyName) {
        String document = extensionPropertyValue(userTask, propertyName);
        if (!StringUtils.hasText(document)) {
            return Map.of();
        }
        try {
            Map<String, Object> config = objectMapper.readValue(document, new TypeReference<>() { });
            if (config == null) {
                throw new IllegalArgumentException(propertyName + " 必须是 JSON 对象");
            }
            return config;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException(propertyName + " 必须是合法 JSON 对象", exception);
        }
    }

    /**
     * 判断是否具有可编辑{@code independent}来源；判断结果决定调用方的后续分支。
     *
     * @param assignment 分配，供本方法判断是否具有可编辑{@code independent}来源时使用
     * @return 可编辑{@code independent}来源条件成立时为 true，否则为 false
     */
    @SuppressWarnings("unchecked")
    private boolean hasEditableIndependentSource(Map<String, Object> assignment) {
        if (!(assignment.get("nextApproverSelection") instanceof Map<?, ?> raw)) {
            return false;
        }
        var selection = NextApproverSelectionNormalizer.normalize((Map<String, Object>) raw);
        return selection.visible() && selection.editable()
                && ("SCOPE".equalsIgnoreCase(selection.sourceType())
                || "RESOLVER".equalsIgnoreCase(selection.sourceType()));
    }

    /**
     * 判断是否具有已配置值集合；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否具有已配置值集合的原始输入，结果供调用方继续使用
     * @return 已配置值集合条件成立时为 true，否则为 false
     */
    private boolean hasConfiguredValues(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream().anyMatch(this::hasConfiguredValues);
        }
        return value instanceof String text
                && java.util.Arrays.stream(text.split(",")).anyMatch(StringUtils::hasText);
    }

    /**
     * 构建差异；结果供后续流程传递或持久化。
     *
     * @param config 配置内容，决定后续差异的处理规则
     * @param current 当前，供本方法构建差异时使用
     * @param versions {@code versions}，供本方法构建差异时使用
     * @return 构建后的差异结果，供调用方继续处理
     */
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

    /**
     * 处理活动实例数量，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续活动实例数量的处理规则
     * @param issues {@code issues}，作为 {@code addIssue} 的输入影响后续处理
     * @return 处理后的活动实例数量结果，供调用方继续处理
     */
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

    /**
     * 整理{@code reachable}节点集合数据，供调用方遍历或继续处理。
     *
     * @param parsed {@code parsed}，供本方法处理{@code reachable}节点集合时使用
     * @return 流程定义{@code preflight}集合，供调用方遍历或展示
     */
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

    /**
     * 解析流程定义{@code preflight}；输出作为后续校验或处理的输入。
     *
     * @param bpmnXml BPMNXML，供本方法解析流程定义{@code preflight}时使用
     * @return 解析后的流程定义{@code preflight}结果，供调用方继续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
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

    /**
     * 判断是否具有条件；判断结果决定调用方的后续分支。
     *
     * @param sequenceFlow 序列流程，供本方法判断是否具有条件时使用
     * @return 条件条件成立时为 true，否则为 false
     */
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

    /**
     * 生成指纹文本，供后续匹配或展示。
     *
     * @param element 元素，作为 {@code appendFingerprint} 的输入影响后续处理
     * @return 处理后的指纹文本，供调用方比较或展示
     */
    private String fingerprint(Element element) {
        StringBuilder result = new StringBuilder();
        appendFingerprint(element, result);
        return sha256(result.toString());
    }

    /**
     * 追加指纹；结果供后续流程传递或持久化。
     *
     * @param element 元素，供本方法追加指纹时使用
     * @param result 结果，供本方法追加指纹时使用
     */
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

    /**
     * 整理安全{@code versions}数据，供调用方遍历或继续处理。
     *
     * @param processId 流程ID，后续用于处理安全{@code versions}时定位或关联目标
     * @return 流程版本历史集合，供调用方遍历或展示
     */
    private List<ProcessVersionHistory> safeVersions(String processId) {
        List<ProcessVersionHistory> versions = versionHistoryMapper.findByProcessConfigId(processId);
        return versions == null ? List.of() : versions;
    }

    /**
     * 生成预览令牌文本，供后续匹配或展示。
     *
     * @param processId 流程ID，后续用于处理预览令牌时定位或关联目标
     * @param revision 修订版本，供本方法处理预览令牌时使用
     * @param draftHash 草稿哈希，供本方法处理预览令牌时使用
     * @param issues {@code issues}，供本方法处理预览令牌时使用
     * @param diff 差异，供本方法处理预览令牌时使用
     * @return 处理后的预览令牌文本，供调用方比较或展示
     */
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

    /**
     * 计算输入内容的 SHA-256 摘要，供后续签名或幂等键使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    /**
     * 添加签发；结果供后续流程传递或持久化。
     *
     * @param issues {@code issues}，供本方法添加签发时使用
     * @param code 编码，后续用于添加签发时定位或关联目标
     * @param severity {@code severity}，作为 {@code issues.add} 的输入影响后续处理
     * @param elementId 元素ID，后续用于添加签发时定位或关联目标
     * @param elementType 元素类型标识，决定后续签发采用的处理分支
     * @param message 消息，作为 {@code issues.add} 的输入影响后续处理
     * @param suggestion {@code suggestion}，作为 {@code issues.add} 的输入影响后续处理
     * @param processId 流程ID，后续用于添加签发时定位或关联目标
     */
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

    /**
     * 整理{@code difference}数据，供调用方遍历或继续处理。
     *
     * @param left 左侧，供本方法处理{@code difference}时使用
     * @param right 右侧，供本方法处理{@code difference}时使用
     * @return 流程定义{@code preflight}集合，供调用方遍历或展示
     */
    private List<String> difference(Set<String> left, Set<String> right) {
        return left.stream().filter(value -> !right.contains(value)).sorted().toList();
    }

    /**
     * 处理修订版本，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续修订版本的处理规则
     * @return 处理后的修订版本结果，供调用方继续处理
     */
    private long revisionOf(ProcessDefinitionConfig config) {
        return config.getDraftRevision() == null || config.getDraftRevision() < 1
                ? 1L : config.getDraftRevision();
    }

    /**
     * 处理基础已发布版本，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续基础已发布版本的处理规则
     * @return 处理后的基础已发布版本结果，供调用方继续处理
     */
    private int basePublishedVersionOf(ProcessDefinitionConfig config) {
        if (config.getBasePublishedVersion() != null) {
            return Math.max(config.getBasePublishedVersion(), 0);
        }
        return config.getVersion() == null ? 0 : Math.max(config.getVersion(), 0);
    }

    /**
     * 生成草稿哈希文本，供后续匹配或展示。
     *
     * @param config 配置内容，决定后续草稿哈希的处理规则
     * @return 处理后的草稿哈希文本，供调用方比较或展示
     */
    private String draftHashOf(ProcessDefinitionConfig config) {
        return StringUtils.hasText(config.getDraftHash())
                ? config.getDraftHash() : ProcessDraftHashSupport.hash(config);
    }

    /**
     * 处理{@code severity}顺序，并将结果传给后续步骤。
     *
     * @param severity {@code severity}，供本方法处理{@code severity}顺序时使用
     * @return 处理后的{@code severity}顺序结果，供调用方继续处理
     */
    private int severityOrder(Severity severity) {
        return switch (severity) {
            case BLOCKER -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }

    /**
     * 生成稳定编码文本，供后续匹配或展示。
     *
     * @param exception 异常，供本方法处理稳定编码时使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的稳定编码文本，供调用方比较或展示
     */
    private String stableCode(RuntimeException exception, String fallback) {
        String message = exception.getMessage();
        if (message == null) {
            return fallback;
        }
        int separator = message.indexOf(':');
        String candidate = separator < 0 ? message : message.substring(0, separator);
        return candidate.matches("[A-Z][A-Z0-9_]{2,80}") ? candidate : fallback;
    }

    /**
     * 生成元素ID起始文本，供后续匹配或展示。
     *
     * @param exception 异常，供本方法处理元素ID起始时使用
     * @return 处理后的元素ID起始文本，供调用方比较或展示
     */
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

    /**
     * 生成安全消息文本，供后续匹配或展示。
     *
     * @param exception 异常，供本方法处理安全消息时使用
     * @return 处理后的安全消息文本，供调用方比较或展示
     */
    private String safeMessage(Throwable exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    /**
     * 判断安全相等条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，作为 {@code java.util.Objects.equals} 的输入影响后续处理
     * @param right 右侧，作为 {@code java.util.Objects.equals} 的输入影响后续处理
     * @return 安全相等条件成立时为 true，否则为 false
     */
    private boolean safeEquals(Object left, Object right) {
        return java.util.Objects.equals(left, right);
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
        String name = node.getNodeName();
        int separator = name.indexOf(':');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    /**
     * 封装流程边的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param sourceRef 来源引用，保存在对象中供后续校验、查询或展示
     * @param targetRef 目标引用，保存在对象中供后续校验、查询或展示
     * @param hasCondition {@code has}条件，保存在对象中供后续校验、查询或展示
     */
    private record FlowEdge(String id, String sourceRef, String targetRef, boolean hasCondition) {
    }

    /**
     * 封装{@code modeling}仅元素的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param type 类型标识，决定后续{@code modeling}仅元素采用的处理分支
     */
    private record ModelingOnlyElement(String id, String type) {
    }

    /**
     * 封装{@code parsed}BPMN的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param processCount 流程数量，保存在对象中供后续校验、查询或展示
     * @param flowNodes 流程节点集合，保存在对象中供后续校验、查询或展示
     * @param edges {@code edges}，保存在对象中供后续校验、查询或展示
     * @param startIds 启动ID 集合，保存在对象中供后续校验、查询或展示
     * @param endIds 结束ID 集合，保存在对象中供后续校验、查询或展示
     * @param duplicateIds {@code duplicate}ID 集合，保存在对象中供后续校验、查询或展示
     * @param fingerprints {@code fingerprints}，保存在对象中供后续校验、查询或展示
     * @param modelingOnlyElements {@code modeling}仅{@code elements}，保存在对象中供后续校验、查询或展示
     */
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

    /**
     * 设置空办理人策略BPMN校验器；后续读取或执行将使用更新后的状态。
     *
     * @param validator 校验器，供本方法设置空办理人策略BPMN校验器时使用
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setEmptyAssigneePolicyBpmnValidator(
            com.workflow.process.assignment.application.EmptyAssigneePolicyBpmnValidator validator) {
        this.emptyAssigneePolicyBpmnValidator = validator;
    }

    /**
     * 可选注入矩阵校验器，保留现有轻量单元测试的构造函数兼容性。
     *
     * @param validator 校验器，供本方法设置节点操作策略BPMN校验器时使用
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNodeOperationPolicyBpmnValidator(
            com.workflow.process.task.application.operation.NodeOperationPolicyBpmnValidator validator) {
        this.nodeOperationPolicyBpmnValidator = validator;
    }
}
