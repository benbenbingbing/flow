package com.workflow.process.assignment.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 在单个已部署 BPMN 模型内递归解析“使用其他节点审批人”。
 *
 * <p>{@code referencedNodeId} 是唯一权威引用；节点名称仅用于设计器回显。
 * 解析过程不访问当前草稿或节点快照，确保旧实例始终使用它绑定的部署版本。</p>
 */
@Component
@RequiredArgsConstructor
public class NodeAssignmentReferenceResolver {

    public static final int MAX_REFERENCE_DEPTH = 16;

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    /**
     * 从当前节点开始解析到真正提供办理人规则的 UserTask。
     *
     * @param model 同一流程定义的已部署模型
     * @param currentTask 当前被分配的节点
     * @param currentConfig 已从当前节点读取并合并历史字段的配置
     * @return 终端规则节点、终端配置及完整节点链
     * @throws IllegalArgumentException 引用缺失、越界、成环或配置损坏时抛出
     */
    public ResolvedAssignment resolve(
            BpmnModel model,
            UserTask currentTask,
            Map<String, Object> currentConfig) {
        if (model == null || model.getMainProcess() == null) {
            throw new IllegalArgumentException("无法读取引用节点所在的部署模型");
        }
        if (currentTask == null || !StringUtils.hasText(currentTask.getId())) {
            throw new IllegalArgumentException("当前审批节点缺少稳定 nodeId");
        }

        UserTask task = currentTask;
        Map<String, Object> config = immutableCopy(currentConfig);
        Set<String> visited = new LinkedHashSet<>();
        List<String> chain = new ArrayList<>();
        for (int depth = 0; depth <= MAX_REFERENCE_DEPTH; depth++) {
            String nodeId = task.getId();
            if (!visited.add(nodeId)) {
                chain.add(nodeId);
                throw invalid(currentTask,
                        "审批人节点引用形成环: " + String.join(" -> ", chain));
            }
            chain.add(nodeId);
            if (!isNodeReference(config)) {
                return new ResolvedAssignment(
                        task,
                        config,
                        List.copyOf(chain));
            }
            if (depth == MAX_REFERENCE_DEPTH) {
                throw invalid(currentTask,
                        "审批人节点引用超过最大深度 " + MAX_REFERENCE_DEPTH);
            }

            String referencedNodeId = referencedNodeId(config);
            if (!StringUtils.hasText(referencedNodeId)) {
                throw invalid(task, "node_reference 缺少 referencedNodeId");
            }
            if (containsExpression(referencedNodeId)) {
                throw invalid(task,
                        "referencedNodeId 必须是字面量节点 ID，不能使用表达式");
            }
            FlowElement element = model.getMainProcess().getFlowElement(
                    referencedNodeId, true);
            if (!(element instanceof UserTask referencedTask)) {
                throw invalid(task,
                        "referencedNodeId 不存在或不是 UserTask: "
                                + referencedNodeId);
            }
            task = referencedTask;
            config = readAssigneeConfig(referencedTask);
        }
        throw invalid(currentTask,
                "审批人节点引用超过最大深度 " + MAX_REFERENCE_DEPTH);
    }

    /** 读取 assigneeConfig，并与同一部署中的历史多实例配置保序合并。 */
    public Map<String, Object> readAssigneeConfig(UserTask userTask) {
        Map<String, Object> assigneeConfig = readConfigProperty(
                userTask, "assigneeConfig");
        Map<String, Object> multiInstanceConfig = readConfigProperty(
                userTask, "multiInstanceConfig");
        return immutableCopy(
                LegacyMultiInstanceAssignmentParser.mergeConfigs(
                        assigneeConfig, multiInstanceConfig));
    }

    /** 兼容大小写及历史驼峰写法，但新部署统一保存 node_reference。 */
    public static boolean isNodeReference(Map<String, Object> config) {
        String type = text(config == null
                ? null : config.get("assigneeType"));
        if (!StringUtils.hasText(type)) {
            return false;
        }
        String normalized = type.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        return "nodereference".equals(normalized);
    }

    /** canonical 字段优先，历史 sourceNodeId 仅作读取兼容。 */
    public static String referencedNodeId(Map<String, Object> config) {
        return firstText(
                config == null ? null : config.get("referencedNodeId"),
                config == null ? null : config.get("sourceNodeId"));
    }

    /**
     * 统一计算引用者的实际输出模式。当前节点是否多实例最优先；普通任务
     * 继承源规则的 direct/candidate 语义，但绝不继承源节点的循环属性。
     */
    public static String assignmentMode(
            UserTask currentTask,
            UserTask sourceTask,
            Map<String, Object> sourceConfig) {
        if (currentTask.hasMultiInstanceLoopCharacteristics()) {
            return "MULTI_INSTANCE";
        }
        if (literalAssignee(sourceTask.getAssignee())) {
            return "DIRECT";
        }
        String configured = firstText(
                sourceConfig == null
                        ? null : sourceConfig.get("assignmentMode"),
                sourceConfig == null ? null : sourceConfig.get("mode"));
        if (StringUtils.hasText(configured)) {
            String normalized = configured.trim()
                    .toUpperCase(Locale.ROOT);
            if ("DIRECT".equals(normalized)
                    || "CANDIDATE".equals(normalized)) {
                return normalized;
            }
            if (!"MULTI_INSTANCE".equals(normalized)) {
                throw new IllegalArgumentException(
                        "不支持的审批分配模式: " + configured);
            }
        }
        if (hasValues(sourceTask.getCandidateUsers())
                || hasValues(sourceTask.getCandidateGroups())) {
            return "CANDIDATE";
        }
        String assigneeType = text(sourceConfig == null
                ? null : sourceConfig.get("assigneeType"));
        if (StringUtils.hasText(assigneeType)) {
            String normalizedType = assigneeType.trim()
                    .toLowerCase(Locale.ROOT);
            if ("candidate".equals(normalizedType)
                    || "role".equals(normalizedType)
                    || "group".equals(normalizedType)) {
                return "CANDIDATE";
            }
        }
        LegacyMultiInstanceAssignmentParser.LegacyAssignment legacy =
                LegacyMultiInstanceAssignmentParser.parse(
                        sourceConfig == null ? Map.of() : sourceConfig);
        if (legacy.effective()
                && legacy.userKeys().isEmpty()
                && (!legacy.groupKeys().isEmpty()
                || !legacy.roleKeys().isEmpty())) {
            return "CANDIDATE";
        }
        return "DIRECT";
    }

    private Map<String, Object> readConfigProperty(
            UserTask userTask,
            String propertyName) {
        String json = ConfiguredTaskPropertyReader.read(
                userTask, propertyName);
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw invalid(userTask,
                    propertyName + " 不是合法 JSON", exception);
        }
    }

    private Map<String, Object> immutableCopy(
            Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private boolean containsExpression(String value) {
        return value.contains("${") || value.contains("#{");
    }

    private IllegalArgumentException invalid(
            UserTask task,
            String message) {
        return invalid(task, message, null);
    }

    private IllegalArgumentException invalid(
            UserTask task,
            String message,
            Throwable cause) {
        String nodeId = task == null ? null : task.getId();
        String detail = "节点 " + (nodeId == null ? "<unknown>" : nodeId)
                + " 的审批人引用无效: " + message;
        return cause == null
                ? new IllegalArgumentException(detail)
                : new IllegalArgumentException(detail, cause);
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String result = text(value);
            if (StringUtils.hasText(result)) {
                return result.trim();
            }
        }
        return null;
    }

    private static boolean literalAssignee(String value) {
        return StringUtils.hasText(value)
                && !value.contains("${")
                && !value.contains("#{");
    }

    private static boolean hasValues(List<String> values) {
        return values != null
                && values.stream().anyMatch(StringUtils::hasText);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 引用解析结果；chainNodeIds 从当前节点起并包含终端规则节点。 */
    public record ResolvedAssignment(
            UserTask sourceTask,
            Map<String, Object> assigneeConfig,
            List<String> chainNodeIds) {

        public boolean referenced() {
            return chainNodeIds != null && chainNodeIds.size() > 1;
        }
    }
}
