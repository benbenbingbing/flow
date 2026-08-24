package com.workflow.process.task.application.operation;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 发布到 BPMN 快照中的节点操作策略。
 *
 * <p>策略只从流程实例绑定的部署版本读取，避免草稿或后续版本改变历史实例的
 * 可操作范围。未配置策略的历史流程使用 {@link #legacyCompatible()} 保持兼容。</p>
 */
public record NodeOperationPolicy(
        int version,
        boolean configured,
        Set<String> allowedVariables,
        Map<Operation, Rule> operations) {

    public static final int CURRENT_VERSION = 1;

    public NodeOperationPolicy {
        allowedVariables = allowedVariables == null
                ? Set.of()
                : Set.copyOf(allowedVariables);
        EnumMap<Operation, Rule> copiedRules = new EnumMap<>(Operation.class);
        if (operations != null) {
            copiedRules.putAll(operations);
        }
        operations = Map.copyOf(copiedRules);
    }

    /**
     * 返回某个操作的规则。显式配置时未出现的操作默认禁用，防止新增操作被旧矩阵意外放行。
     */
    public Rule rule(Operation operation) {
        if (!configured) {
            return Rule.legacyAllowed();
        }
        return operations.getOrDefault(operation, Rule.disabled());
    }

    /**
     * 为未配置矩阵的存量流程提供与改造前一致的行为。
     */
    public static NodeOperationPolicy legacyCompatible() {
        EnumMap<Operation, Rule> rules = new EnumMap<>(Operation.class);
        for (Operation operation : Operation.values()) {
            rules.put(operation, Rule.legacyAllowed());
        }
        return new NodeOperationPolicy(CURRENT_VERSION, false, Set.of(), rules);
    }

    /** 节点支持的标准操作。 */
    public enum Operation {
        APPROVE("approve"),
        REJECT("reject"),
        TRANSFER("transfer"),
        ADD_SIGN_BEFORE("addSignBefore"),
        ADD_SIGN_AFTER("addSignAfter"),
        ADD_SIGN_PARALLEL("addSignParallel"),
        MANUAL_CC("manualCc"),
        WITHDRAW("withdraw"),
        TERMINATE("terminate");

        private final String apiCode;

        Operation(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }

        /**
         * 接受枚举名、camelCase 和短横线形式，便于后端与设计器稳定交换配置。
         */
        public static Operation fromCode(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("操作类型不能为空");
            }
            String normalized = value.trim()
                    .replace('-', '_')
                    .replace(' ', '_')
                    .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                    .toUpperCase(Locale.ROOT);
            if ("ADD_SIGN".equals(normalized)) {
                normalized = "ADD_SIGN_PARALLEL";
            }
            return Operation.valueOf(normalized);
        }

        public boolean isAddSign() {
            return this == ADD_SIGN_BEFORE
                    || this == ADD_SIGN_AFTER
                    || this == ADD_SIGN_PARALLEL;
        }
    }

    /** 操作目标的可选范围。 */
    public enum TargetScope {
        ANY,
        FIXED
    }

    /**
     * 单个操作的判定规则及其操作特有约束。
     */
    public record Rule(
            boolean enabled,
            String conditionExpression,
            String permissionCode,
            boolean reasonRequired,
            List<String> reasonTemplates,
            boolean reasonTemplateRequired,
            TargetScope targetScope,
            Set<String> targetIds,
            Set<String> allowedAddSignTypes,
            Set<String> allowedRejectTargets,
            Integer withdrawWithinMinutes) {

        public Rule {
            conditionExpression = trimToNull(conditionExpression);
            permissionCode = trimToNull(permissionCode);
            reasonTemplates = reasonTemplates == null
                    ? List.of()
                    : reasonTemplates.stream()
                            .map(NodeOperationPolicy::trimToNull)
                            .filter(value -> value != null)
                            .distinct()
                            .toList();
            targetScope = targetScope == null ? TargetScope.ANY : targetScope;
            targetIds = normalizeSet(targetIds, false);
            allowedAddSignTypes = normalizeSet(allowedAddSignTypes, true);
            allowedRejectTargets = normalizeSet(allowedRejectTargets, false);
        }

        private static Rule legacyAllowed() {
            return new Rule(true, null, null, false, List.of(), false,
                    TargetScope.ANY, Set.of(), Set.of(), Set.of(), null);
        }

        private static Rule disabled() {
            return new Rule(false, null, null, false, List.of(), false,
                    TargetScope.ANY, Set.of(), Set.of(), Set.of(), null);
        }
    }

    private static Set<String> normalizeSet(Set<String> source, boolean uppercase) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : source) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                result.add(uppercase ? normalized.toUpperCase(Locale.ROOT) : normalized);
            }
        }
        return Set.copyOf(result);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
