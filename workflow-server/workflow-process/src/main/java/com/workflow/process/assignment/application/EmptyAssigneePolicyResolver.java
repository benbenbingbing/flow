package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.domain.EmptyAssigneePolicy;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 解析流程级默认策略和节点级覆盖，并执行统一参数校验。 */
@Component
@RequiredArgsConstructor
public class EmptyAssigneePolicyResolver {

    public static final String PROCESS_PROPERTY = "emptyAssigneeDefault";
    public static final String NODE_CONFIG_KEY = "emptyAssigneeStrategy";

    private final ObjectMapper objectMapper;

    /**
     * 从已部署 BPMN 和节点 assigneeConfig 解析不可变的有效策略。
     *
     * @param bpmnModel BPMN模型，供本方法解析空办理人策略解析器时使用
     * @param assigneeConfig 办理人配置内容，决定后续空办理人策略解析器的处理规则
     * @return 解析后的空办理人策略解析器结果，供调用方继续处理
     */
    public EmptyAssigneePolicy resolve(
            BpmnModel bpmnModel,
            Map<String, Object> assigneeConfig) {
        String processDocument = bpmnModel == null || bpmnModel.getMainProcess() == null
                ? null
                : ConfiguredTaskPropertyReader.read(
                        bpmnModel.getMainProcess(), PROCESS_PROPERTY);
        return resolve(processDocument, assigneeConfig);
    }

    /**
     * 从序列化流程默认值和节点配置解析策略，供预检与测试中心复用。
     *
     * @param processDefaultDocument 流程默认文档，作为 {@code readMap} 的输入影响后续处理
     * @param assigneeConfig 办理人配置内容，决定后续空办理人策略解析器的处理规则
     * @return 解析后的空办理人策略解析器结果，供调用方继续处理
     */
    public EmptyAssigneePolicy resolve(
            String processDefaultDocument,
            Map<String, Object> assigneeConfig) {
        Map<String, Object> process = readMap(processDefaultDocument, "流程级空办理人默认策略");
        Map<String, Object> node = mapValue(
                assigneeConfig == null ? null : assigneeConfig.get(NODE_CONFIG_KEY));
        Map<String, Object> effective = new LinkedHashMap<>(process);
        String nodePolicy = normalized(node.get("policy"));
        if ("INHERIT".equals(nodePolicy)) {
            // 继承表示完整采用流程版本快照，不能让编辑器携带的默认重试数字覆盖流程值。
            node = Map.of();
        }
        node.forEach((key, value) -> {
            if (value != null && (!(value instanceof String text) || StringUtils.hasText(text))) {
                effective.put(key, value);
            }
        });
        String policyName = normalized(effective.get("policy"));
        if (!StringUtils.hasText(policyName) || "INHERIT".equals(policyName)) {
            policyName = EmptyAssigneePolicy.Strategy.BLOCK_PUBLISH.name();
        }
        EmptyAssigneePolicy.Strategy strategy;
        try {
            strategy = EmptyAssigneePolicy.Strategy.valueOf(policyName);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("不支持的空办理人策略：" + policyName, error);
        }
        EmptyAssigneePolicy defaults = EmptyAssigneePolicy.secureDefault();
        EmptyAssigneePolicy result = new EmptyAssigneePolicy(
                strategy,
                text(effective.get("fallbackUser")),
                text(effective.get("fallbackGroup")),
                integer(effective.get("maxRetries"), defaults.maxRetries()),
                integer(effective.get("initialDelaySeconds"), defaults.initialDelaySeconds()),
                decimal(effective.get("backoffMultiplier"), defaults.backoffMultiplier()),
                firstText(effective.get("responsibilityOwner"), defaults.responsibilityOwner()));
        validate(result);
        return result;
    }

    /**
     * 校验五类策略的必需参数和退避边界。
     *
     * @param policy 策略内容，决定后续空办理人策略解析器的处理规则
     */
    public void validate(EmptyAssigneePolicy policy) {
        if (policy.strategy() == EmptyAssigneePolicy.Strategy.FALLBACK_USER
                && !StringUtils.hasText(policy.fallbackUser())) {
            throw new IllegalArgumentException("FALLBACK_USER 必须配置 fallbackUser");
        }
        if (policy.strategy() == EmptyAssigneePolicy.Strategy.FALLBACK_GROUP
                && !StringUtils.hasText(policy.fallbackGroup())) {
            throw new IllegalArgumentException("FALLBACK_GROUP 必须配置 fallbackGroup");
        }
        if (policy.strategy() == EmptyAssigneePolicy.Strategy.WAIT_AND_RETRY) {
            if (policy.maxRetries() < 1 || policy.maxRetries() > 20) {
                throw new IllegalArgumentException("WAIT_AND_RETRY maxRetries 必须在 1-20 之间");
            }
            if (policy.initialDelaySeconds() < 5 || policy.initialDelaySeconds() > 86400) {
                throw new IllegalArgumentException(
                        "WAIT_AND_RETRY initialDelaySeconds 必须在 5-86400 之间");
            }
            if (!Double.isFinite(policy.backoffMultiplier())
                    || policy.backoffMultiplier() < 1.0 || policy.backoffMultiplier() > 10.0) {
                throw new IllegalArgumentException(
                        "WAIT_AND_RETRY backoffMultiplier 必须在 1-10 之间");
            }
        }
        if (!StringUtils.hasText(policy.responsibilityOwner())) {
            throw new IllegalArgumentException("空办理人策略必须配置责任人");
        }
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    /**
     * 读取键值配置，供后续规则或接口处理使用。
     *
     * @param value 待读取映射的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于读取映射时匹配或展示
     * @return 映射键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value, String label) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (Exception error) {
            throw new IllegalArgumentException(label + "不是合法 JSON", error);
        }
    }

    /**
     * 生成规范化文本，供后续匹配或展示。
     *
     * @param value 待处理规范化的原始输入，结果供调用方继续使用
     * @return 处理后的规范化文本，供调用方比较或展示
     */
    private String normalized(Object value) {
        String text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 处理后的整数结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private int integer(Object value, int defaultValue) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("重试次数和延迟必须是整数", error);
        }
    }

    /**
     * 处理{@code decimal}，并将结果传给后续步骤。
     *
     * @param value 待处理{@code decimal}的原始输入，结果供调用方继续使用
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 处理后的{@code decimal}结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private double decimal(Object value, double defaultValue) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("退避倍数必须是数字", error);
        }
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param first 首个，作为 {@code text} 的输入影响后续处理
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object first, String fallback) {
        String value = text(first);
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        String result = value == null ? null : String.valueOf(value).trim();
        return StringUtils.hasText(result) ? result : null;
    }
}
