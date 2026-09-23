package com.workflow.admin.audit.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 将前后快照转换为按字段路径组织的差异集合。
 */
@Component
@RequiredArgsConstructor
public class AuditDiffCalculator {

    private final ObjectMapper objectMapper;

    /**
     * 计算审计差异{@code calculator}；结果供后续判断或展示使用。
     *
     * @param before 之前，作为 {@code objectMapper.valueToTree} 的输入影响后续处理
     * @param after 之后，作为 {@code objectMapper.valueToTree} 的输入影响后续处理
     * @return 审计差异{@code calculator}键值结果，供调用方继续处理
     */
    public Map<String, Object> calculate(Object before, Object after) {
        JsonNode beforeNode = objectMapper.valueToTree(before);
        JsonNode afterNode = objectMapper.valueToTree(after);
        Map<String, Object> changes = new LinkedHashMap<>();
        compare("", beforeNode, afterNode, changes);
        return changes;
    }

    /**
     * 比较审计差异{@code calculator}；结果供调用方的后续步骤使用。
     *
     * @param path 路径，作为 {@code changes.put} 的输入影响后续处理
     * @param before 之前，作为 {@code change.put} 的输入影响后续处理
     * @param after 之后，作为 {@code change.put} 的输入影响后续处理
     * @param changes 变更集合，供本方法比较审计差异{@code calculator}时使用
     */
    private void compare(
            String path,
            JsonNode before,
            JsonNode after,
            Map<String, Object> changes) {
        if (nodesEqual(before, after)) {
            return;
        }
        if (before instanceof ObjectNode beforeObject
                && after instanceof ObjectNode afterObject) {
            Set<String> fields = new LinkedHashSet<>();
            beforeObject.properties().forEach(entry -> fields.add(entry.getKey()));
            afterObject.properties().forEach(entry -> fields.add(entry.getKey()));
            for (String field : fields) {
                compare(
                        childPath(path, field),
                        beforeObject.get(field),
                        afterObject.get(field),
                        changes);
            }
            return;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("before", toValue(before));
        change.put("after", toValue(after));
        changes.put(path.isEmpty() ? "$" : path, change);
    }

    /**
     * 判断节点集合{@code equal}条件是否成立，供调用方选择后续分支。
     *
     * @param before 之前，供本方法处理节点集合{@code equal}时使用
     * @param after 之后，作为 {@code before.equals} 的输入影响后续处理
     * @return 节点集合{@code equal}条件成立时为 true，否则为 false
     */
    private boolean nodesEqual(JsonNode before, JsonNode after) {
        if (before == null || before.isMissingNode()) {
            return after == null || after.isMissingNode() || after.isNull();
        }
        if (after == null || after.isMissingNode()) {
            return before.isNull();
        }
        return before.equals(after);
    }

    /**
     * 转换为值；输出作为后续校验或处理的输入。
     *
     * @param node 节点，供本方法转换为值时使用
     * @return 转换为后的值结果，供调用方继续处理
     */
    private Object toValue(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
                ? null
                : objectMapper.convertValue(node, Object.class);
    }

    /**
     * 生成子级路径文本，供后续匹配或展示。
     *
     * @param parent 父级，供本方法处理子级路径时使用
     * @param field 字段，供本方法处理子级路径时使用
     * @return 处理后的子级路径文本，供调用方比较或展示
     */
    private String childPath(String parent, String field) {
        return parent.isEmpty() ? field : parent + "." + field;
    }
}
