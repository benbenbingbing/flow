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
 *
 * @param version 版本，保存在对象中供后续校验、查询或展示
 * @param configured 已配置，保存在对象中供后续校验、查询或展示
 * @param allowedVariables 允许流程变量，保存在对象中供后续校验、查询或展示
 * @param operations 操作集合，保存在对象中供后续校验、查询或展示
 */
public record NodeOperationPolicy(
        int version,
        boolean configured,
        Set<String> allowedVariables,
        Map<Operation, Rule> operations) {

    public static final int CURRENT_VERSION = 1;

    /**
     * 初始化节点操作策略，保存构造参数供后续方法使用。
     *
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param configured 已配置，保存在对象中供后续校验、查询或展示
     * @param allowedVariables 允许流程变量，保存在对象中供后续校验、查询或展示
     * @param operations 操作集合，保存在对象中供后续校验、查询或展示
     */
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
     *
     * @param operation 操作标识，决定后续规则采用的处理分支
     * @return 处理后的规则结果，供调用方继续处理
     */
    public Rule rule(Operation operation) {
        if (!configured) {
            return Rule.legacyAllowed();
        }
        return operations.getOrDefault(operation, Rule.disabled());
    }

    /**
     * 为未配置矩阵的存量流程提供与改造前一致的行为。
     *
     * @return 处理后的旧版兼容结果，供调用方继续处理
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

        /**
         * 初始化操作，保存构造参数供后续方法使用。
         *
         * @param apiCode API编码依赖，保存到当前对象供后续业务方法调用
         */
        Operation(String apiCode) {
            this.apiCode = apiCode;
        }

        /**
         * 生成API编码文本，供后续匹配或展示。
         *
         * @return 处理后的API编码文本，供调用方比较或展示
         */
        public String apiCode() {
            return apiCode;
        }

        /**
         * 接受枚举名、camelCase 和短横线形式，便于后端与设计器稳定交换配置。
         *
         * @param value 待处理起始编码的原始输入，结果供调用方继续使用
         * @return 处理后的起始编码结果，供调用方继续处理
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

        /**
         * 判断是否添加签名；判断结果决定调用方的后续分支。
         *
         * @return 添加签名条件成立时为 true，否则为 false
         */
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
     *
     * @param enabled 启用，保存在对象中供后续校验、查询或展示
     * @param conditionExpression 条件表达式，保存在对象中供后续校验、查询或展示
     * @param permissionCode 权限编码，后续用于处理规则时定位或关联目标
     * @param reasonRequired 原因必填，保存在对象中供后续校验、查询或展示
     * @param reasonTemplates 原因{@code templates}，保存在对象中供后续校验、查询或展示
     * @param reasonTemplateRequired 原因模板必填，保存在对象中供后续校验、查询或展示
     * @param targetScope 目标作用域，保存在对象中供后续校验、查询或展示
     * @param targetIds 目标ID 集合，保存在对象中供后续校验、查询或展示
     * @param allowedAddSignTypes 允许添加签名类型集合，保存在对象中供后续校验、查询或展示
     * @param allowedRejectTargets 允许驳回目标集合，保存在对象中供后续校验、查询或展示
     * @param withdrawWithinMinutes {@code withdraw}{@code within}{@code minutes}，保存在对象中供后续校验、查询或展示
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

        /**
         * 初始化规则，保存构造参数供后续方法使用。
         *
         * @param enabled 启用，保存在对象中供后续校验、查询或展示
         * @param conditionExpression 条件表达式，保存在对象中供后续校验、查询或展示
         * @param permissionCode 权限编码，后续用于初始化规则时定位或关联目标
         * @param reasonRequired 原因必填，保存在对象中供后续校验、查询或展示
         * @param reasonTemplates 原因{@code templates}，保存在对象中供后续校验、查询或展示
         * @param reasonTemplateRequired 原因模板必填，保存在对象中供后续校验、查询或展示
         * @param targetScope 目标作用域，保存在对象中供后续校验、查询或展示
         * @param targetIds 目标ID 集合，保存在对象中供后续校验、查询或展示
         * @param allowedAddSignTypes 允许添加签名类型集合，保存在对象中供后续校验、查询或展示
         * @param allowedRejectTargets 允许驳回目标集合，保存在对象中供后续校验、查询或展示
         * @param withdrawWithinMinutes {@code withdraw}{@code within}{@code minutes}，保存在对象中供后续校验、查询或展示
         */
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

        /**
         * 处理旧版允许，并将结果传给后续步骤。
         *
         * @return 处理后的旧版允许结果，供调用方继续处理
         */
        private static Rule legacyAllowed() {
            return new Rule(true, null, null, false, List.of(), false,
                    TargetScope.ANY, Set.of(), Set.of(), Set.of(), null);
        }

        /**
         * 处理{@code disabled}，并将结果传给后续步骤。
         *
         * @return 处理后的{@code disabled}结果，供调用方继续处理
         */
        private static Rule disabled() {
            return new Rule(false, null, null, false, List.of(), false,
                    TargetScope.ANY, Set.of(), Set.of(), Set.of(), null);
        }
    }

    /**
     * 规范化设置；输出作为后续校验或处理的输入。
     *
     * @param source 待规范化设置的原始输入，结果供调用方继续使用
     * @param uppercase {@code uppercase}，作为 {@code result.add} 的输入影响后续处理
     * @return 节点操作策略集合，供调用方遍历或展示
     */
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

    /**
     * 去除文本首尾空白，并将空白结果转为 null 供后续缺失值判断。
     *
     * @param value 待清理截止空值的原始输入，结果供调用方继续使用
     * @return 清理后的截止空值文本，供调用方比较或展示
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
