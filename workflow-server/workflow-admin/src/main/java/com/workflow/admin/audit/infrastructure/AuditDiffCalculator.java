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

    public Map<String, Object> calculate(Object before, Object after) {
        JsonNode beforeNode = objectMapper.valueToTree(before);
        JsonNode afterNode = objectMapper.valueToTree(after);
        Map<String, Object> changes = new LinkedHashMap<>();
        compare("", beforeNode, afterNode, changes);
        return changes;
    }

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

    private boolean nodesEqual(JsonNode before, JsonNode after) {
        if (before == null || before.isMissingNode()) {
            return after == null || after.isMissingNode() || after.isNull();
        }
        if (after == null || after.isMissingNode()) {
            return before.isNull();
        }
        return before.equals(after);
    }

    private Object toValue(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
                ? null
                : objectMapper.convertValue(node, Object.class);
    }

    private String childPath(String parent, String field) {
        return parent.isEmpty() ? field : parent + "." + field;
    }
}
