package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EventSubProcess;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/** 已发布用户任务的历史回退策略。发布与运行共用节点边界，实际路径仍须由历史证实。 */
@Component
@RequiredArgsConstructor
public class NodeHistoryReturnPolicy {
    public static final String PROPERTY = "taskReturnConfig";
    public static final String DEFAULT_PROPERTY = "taskReturnDefault";
    public static final String SNAPSHOT_PROPERTY = "publishedTaskReturnTargets";
    private static final int SNAPSHOT_VERSION = 1;
    private static final Configuration DISABLED = new Configuration(Mode.OFF, Set.of(), null);

    private final ObjectMapper json;

    public record Configuration(Mode mode, Set<String> targetNodeIds, String resubmitNodeId) {
        public boolean permits(String nodeId) {
            return mode == Mode.HISTORY || mode == Mode.FIXED && targetNodeIds.contains(nodeId);
        }
    }

    public enum Mode { OFF, HISTORY, FIXED }

    /** 旧部署没有扩展属性时默认关闭，不沿用旧驳回目标白名单。 */
    public Configuration read(FlowElement element) {
        String raw = ConfiguredTaskPropertyReader.read(element, PROPERTY);
        if (raw == null) return DISABLED;
        try {
            JsonNode value = json.readTree(raw);
            if (value == null || !value.isObject()) throw new IllegalArgumentException("回退配置必须是对象");
            Mode mode = Mode.valueOf(value.path("mode").asText());
            Set<String> targets = new LinkedHashSet<>();
            JsonNode configuredTargets = value.path("targetNodeIds");
            if (!configuredTargets.isMissingNode() && !configuredTargets.isArray()) {
                throw new IllegalArgumentException("指定回退目标必须是节点列表");
            }
            for (JsonNode target : configuredTargets) {
                if (!target.isTextual() || target.asText().isBlank()) throw new IllegalArgumentException("回退目标无效");
                targets.add(target.asText());
            }
            JsonNode resubmitValue = value.path("resubmitNodeId");
            if (!resubmitValue.isMissingNode() && !resubmitValue.isNull() && !resubmitValue.isTextual()) {
                throw new IllegalArgumentException("重提节点必须是节点 ID");
            }
            String resubmit = resubmitValue.asText("");
            if (mode == Mode.FIXED && targets.isEmpty()) throw new IllegalArgumentException("请至少指定一个回退目标");
            return new Configuration(mode, Set.copyOf(targets), resubmit.isBlank() ? null : resubmit);
        } catch (JsonProcessingException | IllegalArgumentException error) {
            throw new IllegalArgumentException("节点 " + element.getId() + " 的选择节点回退配置无效: " + error.getMessage(), error);
        }
    }

    /**
     * 读取实例所属部署的候选。旧部署没有清单时沿用节点策略及重提行为。
     * 发布清单还须用实际办理历史过滤，不能直接作为执行目标。
     */
    public Configuration readPublished(Process process, FlowElement node) {
        String raw = ConfiguredTaskPropertyReader.read(process, SNAPSHOT_PROPERTY);
        if (raw == null) return read(node);
        JsonNode targets = parseSnapshot(raw).path("targets").path(node.getId());
        if (targets.isMissingNode() || targets.isArray() && targets.isEmpty()) return DISABLED;
        if (!targets.isArray()) throw new IllegalStateException("发布版回退候选无效");
        Set<String> ids = new HashSet<>();
        for (JsonNode target : targets) {
            if (!target.isTextual() || target.asText().isBlank()) throw new IllegalStateException("发布版回退目标无效");
            ids.add(target.asText());
        }
        return new Configuration(Mode.FIXED, Set.copyOf(ids), null);
    }

    /**
     * 从最终部署模型编译候选，优先使用节点显式设置。若影响候选的拓扑和设置相同，
     * 原样复用上一部署的清单，避免只改布局、名称或表单时重新查找回退路径。
     */
    public String compile(String xml, String processKey, Process previous) {
        Process process = parseModel(xml).getProcesses().stream().filter(item -> processKey.equals(item.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("找不到待发布的流程 " + processKey));
        boolean supportsRoot = supportsRootScope(process);
        validateContainer(process, supportsRoot);
        boolean autoDefault = "HISTORY".equals(ConfiguredTaskPropertyReader.read(process, DEFAULT_PROPERTY));

        // 只记录与候选有关的拓扑和策略。排序使 XML 格式及元素顺序不影响复用。
        var signature = json.createObjectNode().put("enabled", supportsRoot).put("auto", autoDefault);
        var nodes = signature.putArray("nodes");
        var supportedNodes = process.getFlowElements().stream().filter(NodeHistoryReturnPolicy::supports)
                .sorted(Comparator.comparing(FlowElement::getId)).toList();
        supportedNodes.forEach(node -> nodes.addArray().add(node.getId()).add(node instanceof UserTask));
        Set<String> supportedIds = new HashSet<>();
        supportedNodes.forEach(node -> supportedIds.add(node.getId()));
        var edges = signature.putArray("edges");
        process.getFlowElements().stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                .filter(flow -> supportedIds.contains(flow.getSourceRef()) && supportedIds.contains(flow.getTargetRef()))
                .sorted(Comparator.comparing(SequenceFlow::getSourceRef).thenComparing(SequenceFlow::getTargetRef))
                .forEach(flow -> edges.addArray().add(flow.getSourceRef()).add(flow.getTargetRef()));
        var settings = signature.putObject("settings");
        var sourceTasks = supportedNodes.stream().filter(UserTask.class::isInstance).map(UserTask.class::cast).toList();
        var enabled = new LinkedHashMap<UserTask, Configuration>();
        for (UserTask task : sourceTasks) {
            Configuration config = ConfiguredTaskPropertyReader.read(task, PROPERTY) == null
                    ? (autoDefault ? new Configuration(Mode.HISTORY, Set.of(), null) : DISABLED)
                    : read(task);
            if (config.mode() == Mode.OFF) continue;
            enabled.put(task, config);
            var setting = settings.putObject(task.getId()).put("mode", config.mode().name());
            if (config.mode() == Mode.FIXED) {
                var targets = setting.putArray("targets");
                config.targetNodeIds().stream().sorted().forEach(targets::add);
            }
        }
        String fingerprint = digest(signature.toString());
        String previousSnapshot = ConfiguredTaskPropertyReader.read(previous, SNAPSHOT_PROPERTY);
        if (previousSnapshot != null) {
            JsonNode snapshot = parseSnapshot(previousSnapshot);
            if (fingerprint.equals(snapshot.path("fingerprint").asText())) return previousSnapshot;
        }

        var snapshot = json.createObjectNode().put("version", SNAPSHOT_VERSION).put("fingerprint", fingerprint);
        var candidates = snapshot.putObject("targets");
        if (!supportsRoot) return snapshot.toString();
        enabled.forEach((task, config) -> {
            // 指定模式在上面已验证可达，原样写入人工名单。
            Set<String> allowed = config.mode() == Mode.FIXED ? config.targetNodeIds() : upstream(process, task);
            if (allowed.isEmpty()) return;
            var targets = candidates.putArray(task.getId());
            allowed.stream().sorted().forEach(targets::add);
        });
        return snapshot.toString();
    }

    /** 使用同一 BPMN 模型检查显式回退设置，包括子流程内不受支持的配置。 */
    public void validate(String xml) {
        parseModel(xml).getProcesses().forEach(process -> validateContainer(process, supportsRootScope(process)));
    }

    private BpmnModel parseModel(String xml) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            var reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                return new BpmnXMLConverter().convertToBpmnModel(reader);
            } finally {
                reader.close();
            }
        } catch (javax.xml.stream.XMLStreamException error) {
            throw new IllegalArgumentException("无法读取节点回退配置", error);
        }
    }

    /** 根作用域的事件子流程可能在回退期间改变执行树，即使目前没有第二条活动执行。 */
    public static boolean supportsRootScope(FlowElementsContainer process) {
        return process != null && process.getFlowElements().stream().noneMatch(EventSubProcess.class::isInstance);
    }

    private void validateContainer(FlowElementsContainer container, boolean root) {
        for (FlowElement element : container.getFlowElements()) {
            if (ConfiguredTaskPropertyReader.read(element, PROPERTY) != null) {
                Configuration config = read(element);
                if (!(element instanceof UserTask) || config.mode() != Mode.OFF
                        && (!root || !supports(element))) {
                    throw new IllegalArgumentException("节点 " + element.getId() + " 不支持配置选择节点回退");
                }
                if (config.mode() == Mode.FIXED) {
                    Set<String> allowed = upstream(container, (FlowNode) element);
                    if (!allowed.containsAll(config.targetNodeIds())) {
                        throw new IllegalArgumentException("节点 " + element.getId() + " 存在无效的回退目标");
                    }
                }
            }
            if (element instanceof FlowElementsContainer nested) validateContainer(nested, false);
        }
    }

    private JsonNode parseSnapshot(String raw) {
        try {
            JsonNode snapshot = json.readTree(raw);
            if (snapshot != null && snapshot.path("version").asInt() == SNAPSHOT_VERSION
                    && snapshot.path("targets").isObject()) return snapshot;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法读取发布版回退候选", error);
        }
        throw new IllegalStateException("发布版回退候选版本或格式无效");
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    /** 只沿用户任务及排他网关反向搜索，结构上存在的路径用于设计校验。 */
    private Set<String> upstream(FlowElementsContainer container, FlowNode source) {
        Set<String> seen = new HashSet<>();
        Set<String> result = new HashSet<>();
        var pending = new ArrayDeque<FlowNode>();
        pending.add(source);
        seen.add(source.getId());
        while (!pending.isEmpty()) {
            for (var flow : pending.removeFirst().getIncomingFlows()) {
                FlowElement predecessor = container.getFlowElement(flow.getSourceRef());
                if (!supports(predecessor) || !seen.add(predecessor.getId())) continue;
                if (predecessor instanceof UserTask) result.add(predecessor.getId());
                pending.add((FlowNode) predecessor);
            }
        }
        return result;
    }

    /** 多实例、边界订阅、异步作业及隐式分流需要独立的执行生命周期处理。 */
    public static boolean supports(FlowElement element) {
        if (element instanceof ExclusiveGateway gateway) return !gateway.isAsynchronous() && !gateway.isAsynchronousLeave();
        if (!(element instanceof UserTask task)) return false;
        return !task.hasMultiInstanceLoopCharacteristics() && !task.isAsynchronous()
                && !task.isAsynchronousLeave() && task.getOutgoingFlows().size() <= 1
                && !task.isForCompensation() && task.getBoundaryEvents().isEmpty();
    }
}
