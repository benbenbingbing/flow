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

    /** 从已部署 BPMN 和节点 assigneeConfig 解析不可变的有效策略。 */
    public EmptyAssigneePolicy resolve(
            BpmnModel bpmnModel,
            Map<String, Object> assigneeConfig) {
        String processDocument = bpmnModel == null || bpmnModel.getMainProcess() == null
                ? null
                : ConfiguredTaskPropertyReader.read(
                        bpmnModel.getMainProcess(), PROCESS_PROPERTY);
        return resolve(processDocument, assigneeConfig);
    }

    /** 从序列化流程默认值和节点配置解析策略，供预检与测试中心复用。 */
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

    /** 校验五类策略的必需参数和退避边界。 */
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

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

    private String normalized(Object value) {
        String text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

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

    private String firstText(Object first, String fallback) {
        String value = text(first);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String text(Object value) {
        String result = value == null ? null : String.valueOf(value).trim();
        return StringUtils.hasText(result) ? result : null;
    }
}
