package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 从用户任务的 {@code assigneeConfig} 根级读取三个简化操作开关。
 */
@Component
@RequiredArgsConstructor
public class NodeOperationConfigReader {

    public static final String ASSIGNEE_CONFIG_PROPERTY = "assigneeConfig";
    public static final String ALLOW_TRANSFER = "allowTransfer";
    public static final String ALLOW_ADD_SIGN = "allowAddSign";
    public static final String ALLOW_TERMINATE = "allowTerminate";

    private final ObjectMapper objectMapper;

    /**
     * 读取显式简化配置。任一新字段存在即视为新配置，其余字段缺省为 {@code true}。
     *
     * @return 新配置；三个字段均不存在时返回空，以便调用方继续执行旧矩阵兼容逻辑
     */
    public Optional<NodeOperationConfig> read(BaseElement element) {
        String assigneeJson = ConfiguredTaskPropertyReader.read(
                element, ASSIGNEE_CONFIG_PROPERTY);
        if (!StringUtils.hasText(assigneeJson)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(assigneeJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("assigneeConfig 必须是 JSON 对象");
            }
            boolean configured = root.has(ALLOW_TRANSFER)
                    || root.has(ALLOW_ADD_SIGN)
                    || root.has(ALLOW_TERMINATE);
            if (!configured) {
                return Optional.empty();
            }
            return Optional.of(new NodeOperationConfig(
                    booleanValue(root, ALLOW_TRANSFER),
                    booleanValue(root, ALLOW_ADD_SIGN),
                    booleanValue(root, ALLOW_TERMINATE)));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("assigneeConfig 不是有效 JSON", exception);
        }
    }

    private boolean booleanValue(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null) {
            return true;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(field + " 必须是布尔值");
        }
        return value.booleanValue();
    }
}
