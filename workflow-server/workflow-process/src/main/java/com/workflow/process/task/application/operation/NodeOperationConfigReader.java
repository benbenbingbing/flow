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
 * 从用户任务的 {@code assigneeConfig} 根级读取四个简化操作开关。
 */
@Component
@RequiredArgsConstructor
public class NodeOperationConfigReader {

    public static final String ASSIGNEE_CONFIG_PROPERTY = "assigneeConfig";
    public static final String ALLOW_TRANSFER = "allowTransfer";
    public static final String ALLOW_ADD_SIGN = "allowAddSign";
    public static final String ALLOW_TERMINATE = "allowTerminate";

    public static final String ALLOW_WITHDRAW = "allowWithdraw";

    private final ObjectMapper objectMapper;

    /**
     * 读取冻结的节点开关；新增节点由设计器显式写入四个值。
     * 旧部署缺少 allowWithdraw 时沿用原终止门禁，避免升级代码改变存量实例权限；
     * 一旦配置 allowWithdraw，撤回便独立于终止。旧三字段缺省继续保持历史值。
     *
     * @param element 元素，供本方法读取节点操作配置{@code reader}时使用
     * @return 新配置；四个字段均不存在时返回空，以便调用方继续执行旧矩阵兼容逻辑
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
                    || root.has(ALLOW_TERMINATE)
                    || root.has(ALLOW_WITHDRAW);
            if (!configured) {
                return Optional.empty();
            }
            return Optional.of(new NodeOperationConfig(
                    booleanValue(root, ALLOW_TRANSFER),
                    booleanValue(root, ALLOW_ADD_SIGN),
                    booleanValue(root, ALLOW_TERMINATE),
                    root.has(ALLOW_WITHDRAW) ? booleanValue(root, ALLOW_WITHDRAW)
                            : booleanValue(root, ALLOW_TERMINATE)));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("assigneeConfig 不是有效 JSON", exception);
        }
    }

    /**
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param root 根，供本方法处理布尔值值时使用
     * @param field 字段，作为 {@code root.get} 的输入影响后续处理
     * @return 布尔值值条件成立时为 true，否则为 false
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
