package com.workflow.migration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;

/**
 * SLA 文档中 userId/userIds 的共用遍历器，就地改写文档。
 * 导出与导入仍各自负责可移植标识转换、目标身份校验及异常策略。
 */
final class SlaUserReferenceRewriter {
    private SlaUserReferenceRewriter() {}

    /**
     * 递归改写文本 userId 与 userIds，保留其他字段和原有数组顺序。
     * converter 的身份校验异常直接传播，让外层导入事务中止，不留下静默丢失的审批人。
     */
    static void rewrite(
            JsonNode node,
            ObjectMapper objectMapper,
            java.util.function.UnaryOperator<String> converter) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            List<String> names = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode value = objectNode.get(name);
                if ("userId".equals(name) && value.isTextual()) {
                    objectNode.put(
                            name,
                            converter.apply(value.asText()));
                    continue;
                }
                if ("userIds".equals(name)
                        && value instanceof ArrayNode values) {
                    ArrayNode converted = objectMapper.createArrayNode();
                    values.forEach(item -> converted.add(
                            item.isTextual()
                                    ? converter.apply(item.asText())
                                    : item.asText()));
                    objectNode.set(name, converted);
                    continue;
                }
                rewrite(value, objectMapper, converter);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> rewrite(value, objectMapper, converter));
        }
    }
}
