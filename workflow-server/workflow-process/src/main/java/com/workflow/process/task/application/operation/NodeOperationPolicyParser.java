package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 解析并静态校验 BPMN 中冻结的节点操作策略。
 */
@Component
@RequiredArgsConstructor
public class NodeOperationPolicyParser {

    public static final String PROPERTY_NAME = "nodeOperationPolicy";
    private static final String ASSIGNEE_CONFIG_PROPERTY = "assigneeConfig";

    private final ObjectMapper objectMapper;
    private final NodeOperationConditionEvaluator conditionEvaluator;

    /**
     * 从节点扩展属性读取策略。兼容直接属性及 assigneeConfig 内嵌属性两种设计器格式。
     */
    public NodeOperationPolicy parse(BaseElement element) {
        String directJson = ConfiguredTaskPropertyReader.read(element, PROPERTY_NAME);
        if (StringUtils.hasText(directJson)) {
            return parse(directJson);
        }
        String assigneeJson = ConfiguredTaskPropertyReader.read(element, ASSIGNEE_CONFIG_PROPERTY);
        if (!StringUtils.hasText(assigneeJson)) {
            return NodeOperationPolicy.legacyCompatible();
        }
        try {
            JsonNode nested = objectMapper.readTree(assigneeJson).get(PROPERTY_NAME);
            if (nested == null || nested.isNull()) {
                return NodeOperationPolicy.legacyCompatible();
            }
            return parse(nested.isTextual() ? nested.asText() : nested.toString());
        } catch (Exception exception) {
            throw new IllegalArgumentException("节点操作矩阵所在的 assigneeConfig 不是有效 JSON", exception);
        }
    }

    /**
     * 解析设计器提交的策略 JSON，并执行版本、变量、目标范围和操作特有约束校验。
     */
    public NodeOperationPolicy parse(String json) {
        if (!StringUtils.hasText(json)) {
            return NodeOperationPolicy.legacyCompatible();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("节点操作矩阵必须是 JSON 对象");
            }
            int version = root.path("version").asInt(NodeOperationPolicy.CURRENT_VERSION);
            if (version != NodeOperationPolicy.CURRENT_VERSION) {
                throw new IllegalArgumentException("不支持的节点操作矩阵版本: " + version);
            }
            Set<String> allowedVariables = textSet(root.path("allowedVariables"));
            JsonNode operationNodes = root.path("operations");
            if (!operationNodes.isMissingNode() && !operationNodes.isObject()) {
                throw new IllegalArgumentException("operations 必须是 JSON 对象");
            }

            EnumMap<NodeOperationPolicy.Operation, NodeOperationPolicy.Rule> rules =
                    new EnumMap<>(NodeOperationPolicy.Operation.class);
            if (operationNodes.isObject()) {
                operationNodes.fields().forEachRemaining(entry -> {
                    NodeOperationPolicy.Operation operation;
                    try {
                        operation = NodeOperationPolicy.Operation.fromCode(entry.getKey());
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException("未知节点操作: " + entry.getKey(), exception);
                    }
                    if (rules.containsKey(operation)) {
                        throw new IllegalArgumentException("节点操作重复配置: " + operation.apiCode());
                    }
                    rules.put(operation, parseRule(operation, entry.getValue(), allowedVariables));
                });
            }
            return new NodeOperationPolicy(version, true, allowedVariables, rules);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("节点操作矩阵不是有效 JSON", exception);
        }
    }

    private NodeOperationPolicy.Rule parseRule(
            NodeOperationPolicy.Operation operation,
            JsonNode node,
            Set<String> allowedVariables) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("操作 " + operation.apiCode() + " 的规则必须是 JSON 对象");
        }
        String condition = text(node, "conditionExpression");
        if (!StringUtils.hasText(condition)) {
            condition = text(node, "condition");
        }
        String permissionCode = text(node, "permissionCode");
        List<String> reasonTemplates = textList(node.path("reasonTemplates"));
        boolean reasonTemplateRequired = node.path("reasonTemplateRequired").asBoolean(false);
        NodeOperationPolicy.TargetScope targetScope = parseTargetScope(text(node, "targetScope"));
        Set<String> targetIds = textSet(node.path("targetIds"));
        Set<String> allowedAddSignTypes = textSetUppercase(node.path("allowedAddSignTypes"));
        Set<String> allowedRejectTargets = textSet(node.path("allowedRejectTargets"));
        Integer withdrawWithinMinutes = node.hasNonNull("withdrawWithinMinutes")
                ? node.path("withdrawWithinMinutes").asInt()
                : null;

        if (reasonTemplateRequired && reasonTemplates.isEmpty()) {
            throw new IllegalArgumentException("操作 " + operation.apiCode() + " 要求理由模板，但未配置模板");
        }
        if (targetScope == NodeOperationPolicy.TargetScope.FIXED && targetIds.isEmpty()) {
            throw new IllegalArgumentException("操作 " + operation.apiCode() + " 的固定目标范围不能为空");
        }
        if (withdrawWithinMinutes != null && withdrawWithinMinutes <= 0) {
            throw new IllegalArgumentException("撤回时限必须大于 0 分钟");
        }
        if (!operation.isAddSign() && !allowedAddSignTypes.isEmpty()) {
            throw new IllegalArgumentException("仅加签操作可以配置 allowedAddSignTypes");
        }
        if (operation != NodeOperationPolicy.Operation.REJECT && !allowedRejectTargets.isEmpty()) {
            throw new IllegalArgumentException("仅驳回操作可以配置 allowedRejectTargets");
        }
        if (operation != NodeOperationPolicy.Operation.WITHDRAW && withdrawWithinMinutes != null) {
            throw new IllegalArgumentException("仅撤回操作可以配置 withdrawWithinMinutes");
        }

        // 编译阶段即校验语法和变量白名单，发布后不再接受任意脚本。
        conditionEvaluator.compile(condition, allowedVariables);
        return new NodeOperationPolicy.Rule(
                node.path("enabled").asBoolean(true),
                condition,
                permissionCode,
                node.path("reasonRequired").asBoolean(false),
                reasonTemplates,
                reasonTemplateRequired,
                targetScope,
                targetIds,
                allowedAddSignTypes,
                allowedRejectTargets,
                withdrawWithinMinutes);
    }

    private NodeOperationPolicy.TargetScope parseTargetScope(String value) {
        if (!StringUtils.hasText(value)) {
            return NodeOperationPolicy.TargetScope.ANY;
        }
        try {
            return NodeOperationPolicy.TargetScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的目标范围: " + value, exception);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Set<String> textSet(JsonNode node) {
        return Set.copyOf(new LinkedHashSet<>(textList(node)));
    }

    private Set<String> textSetUppercase(JsonNode node) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : textList(node)) {
            values.add(value.toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(values);
    }

    private List<String> textList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("数组配置格式无效");
        }
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || !StringUtils.hasText(item.asText())) {
                throw new IllegalArgumentException("数组配置只能包含非空字符串");
            }
            values.add(item.asText().trim());
        }
        return List.copyOf(values);
    }
}
