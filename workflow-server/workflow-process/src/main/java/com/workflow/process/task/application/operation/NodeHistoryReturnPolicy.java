package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.EventSubProcess;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** 已发布用户任务的历史回退策略。发布与运行共用节点边界，实际路径仍须由历史证实。 */
@Component
@RequiredArgsConstructor
public class NodeHistoryReturnPolicy {
    public static final String PROPERTY = "taskReturnConfig";

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
        if (raw == null) return new Configuration(Mode.OFF, Set.of(), null);
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

    /** 使用同一 BPMN 模型检查导入与设计器保存的目标，包括藏在子流程内的配置。 */
    public void validate(String xml) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            var reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                var model = new BpmnXMLConverter().convertToBpmnModel(reader);
                model.getProcesses().forEach(process -> validateContainer(process, supportsRootScope(process)));
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
                if (config.mode() != Mode.OFF) {
                    Set<String> allowed = upstream(container, (FlowNode) element);
                    if (!allowed.containsAll(config.targetNodeIds())
                            || config.resubmitNodeId() != null && (!allowed.contains(config.resubmitNodeId())
                            || !config.permits(config.resubmitNodeId()))) {
                        throw new IllegalArgumentException("节点 " + element.getId() + " 存在无效的回退目标或重提节点");
                    }
                }
            }
            if (element instanceof FlowElementsContainer nested) validateContainer(nested, false);
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
