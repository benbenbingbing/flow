package com.workflow.entity.list.experience.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.list.api.response.EntityListSchemaDTO;
import com.workflow.entity.list.application.EntityListRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从当前用户可访问的已发布列表 Schema 构造字段白名单。
 *
 * <p>索引建议必须经过该白名单，避免对已下线或当前用户不可见的字段生成结构操作。</p>
 */
@Component
@RequiredArgsConstructor
public class PublishedListFieldGuard {

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "id", "dataNo", "title", "name", "code", "status",
            "processInstanceId", "processStartTime", "processEndTime",
            "currentTaskId", "currentTaskName", "currentTaskAssignee",
            "submitterId", "submitterName", "deptId", "deptName",
            "submitTime", "createdAt", "updatedAt", "createdBy", "updatedBy");

    private final EntityListRuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    /** 解析当前用户可访问的发布字段，并返回发布版本。 */
    public AllowedFields resolve(String entityCode, String listKey) {
        EntityListSchemaDTO schema = runtimeService.schema(entityCode, listKey, "PAGE");
        Set<String> allowed = new LinkedHashSet<>(SYSTEM_FIELDS);
        Set<String> queryable = new LinkedHashSet<>();
        for (Object raw : schema.getFields() == null ? List.of() : schema.getFields()) {
            Map<String, Object> field = objectMapper.convertValue(raw, Map.class);
            String code = text(field.get("fieldCode"));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            allowed.add(code);
            if (truthy(field.get("isQuery"))) {
                queryable.add(code);
            }
        }
        return new AllowedFields(
                Set.copyOf(allowed), Set.copyOf(queryable), schema.getPublishedVersion());
    }

    /** 校验请求字段全部来自当前发布 Schema。 */
    public AllowedFields require(
            String entityCode,
            String listKey,
            Collection<String> requestedFields) {
        AllowedFields fields = resolve(entityCode, listKey);
        List<String> invalid = normalize(requestedFields).stream()
                .filter(field -> !fields.allowed().contains(field))
                .toList();
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("列表字段不存在、已下线或当前用户不可见: "
                    + String.join(", ", invalid));
        }
        return fields;
    }

    private List<String> normalize(Collection<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (StringUtils.hasText(value)) result.add(value.trim());
        }
        return result;
    }

    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || Integer.valueOf(1).equals(value)
                || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    public record AllowedFields(
            Set<String> allowed,
            Set<String> queryable,
            Integer releaseVersion) {
    }
}
