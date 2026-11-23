package com.workflow.process.condition.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.port.process.model.ProcessValues;

import java.util.Map;

/** 顺序流扩展配置与受控表达式约定；前端和发布器均只生成固定入口。 */
public record SequenceFlowConditionConfig(String providerCode, Map<String, Object> extraParams) {
    public static final String PROPERTY = "sequenceFlowCondition";
    private static final String FLOW_ID_PATTERN = "[A-Za-z_][A-Za-z0-9_.-]*";

    /** 解析已保存的配置；拒绝数组、字符串等非对象参数，防止草稿看似有条件却执行无条件流。 */
    public static SequenceFlowConditionConfig parse(String json, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject() || !root.path("providerCode").isTextual()
                    || root.path("providerCode").asText().isBlank()
                    || root.has("extraParams") && !root.get("extraParams").isObject()) {
                throw new IllegalArgumentException("业务条件接口需要 providerCode 和对象类型的 extraParams");
            }
            Map<String, Object> params = root.has("extraParams")
                    ? mapper.convertValue(root.get("extraParams"), new TypeReference<Map<String, Object>>() {})
                    : Map.of();
            return new SequenceFlowConditionConfig(root.get("providerCode").asText().trim(), ProcessValues.snapshot(params));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("业务条件接口配置不是合法 JSON", exception);
        }
    }

    /** 只允许引用当前连线；业务参数存扩展属性，不拼接到可执行表达式中。 */
    public static String expression(String flowId) {
        if (flowId == null || !flowId.matches(FLOW_ID_PATTERN)) {
            throw new IllegalArgumentException("业务条件连线 ID 不合法: " + flowId);
        }
        return "${sequenceFlowConditionEvaluator.evaluate(execution, '" + flowId + "')}";
    }
}
