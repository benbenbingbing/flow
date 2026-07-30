package com.workflow.entity.version.application;

import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationStepResult;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 实体变更管道内置校验规则。
 */
@Service
@RequiredArgsConstructor
public class EntityMutationBuiltInRuleExecutor {

    private final EntityRecordVersionService versionService;
    private final EntityVersionPolicyMatcher policyMatcher;
    private final EntityDataDynamicMapper dynamicMapper;
    private final DynamicTableService dynamicTableService;

    public EntityMutationStepResult execute(
            EntityVersionConfiguration.Step step,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        String rule = firstText(
                step.getProviderCode(),
                step.getConfig().get("rule"));
        if (!StringUtils.hasText(rule)) {
            throw new IllegalArgumentException(
                    "内置规则未配置规则编码: "
                            + step.getStepName());
        }
        return switch (rule.toUpperCase(Locale.ROOT)) {
            case "REQUIRED_FIELDS" ->
                    requiredFields(
                            step.getConfig(),
                            beforeRecord,
                            workingPayload);
            case "EXPECTED_VERSION", "CURRENT_VERSION" ->
                    expectedVersion(
                            command,
                            step.getConfig());
            case "ALLOWED_STATUS" ->
                    allowedStatus(
                            step.getConfig(),
                            beforeRecord);
            case "DATA_RANGE" ->
                    dataRange(
                            step.getConfig(),
                            command,
                            beforeRecord,
                            workingPayload);
            case "UNIQUE" ->
                    unique(
                            step.getConfig(),
                            command,
                            beforeRecord,
                            workingPayload);
            default -> throw new IllegalArgumentException(
                    "不支持的内置实体变更规则: " + rule);
        };
    }

    private EntityMutationStepResult requiredFields(
            Map<String, Object> config,
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        for (String field : strings(config.get("fields"))) {
            Object value = effectiveValue(
                    field,
                    beforeRecord,
                    workingPayload);
            if (blank(value)) {
                return EntityMutationStepResult.block(
                        firstText(
                                config.get("message"),
                                "字段不能为空: " + field));
            }
        }
        return EntityMutationStepResult.allow();
    }

    private EntityMutationStepResult expectedVersion(
            EntityMutationCommand command,
            Map<String, Object> config) {
        Integer expected = integer(first(
                command.context().extraParams()
                        .get("baselineVersionNo"),
                config.get("expectedVersion")));
        if (expected == null) {
            return EntityMutationStepResult.allow();
        }
        int current = versionService.currentVersionNo(
                command.entityCode(),
                command.recordId());
        return current == expected
                ? EntityMutationStepResult.allow()
                : EntityMutationStepResult.block(
                        firstText(
                                config.get("message"),
                                "基线版本冲突：申请基于 V"
                                        + expected
                                        + "，当前已是 V"
                                        + current));
    }

    private EntityMutationStepResult allowedStatus(
            Map<String, Object> config,
            Map<String, Object> beforeRecord) {
        List<String> statuses = strings(
                config.get("statuses"));
        if (statuses.isEmpty()) {
            return EntityMutationStepResult.allow();
        }
        String current = firstText(
                path(beforeRecord, "status").value(),
                path(beforeRecord, "data.status").value());
        return statuses.stream()
                .anyMatch(item ->
                        item.equalsIgnoreCase(current))
                ? EntityMutationStepResult.allow()
                : EntityMutationStepResult.block(
                        firstText(
                                config.get("message"),
                                "当前状态不允许执行本次变更: "
                                        + current));
    }

    private EntityMutationStepResult dataRange(
            Map<String, Object> config,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        Map<String, Object> condition =
                condition(config);
        if (condition.isEmpty()) {
            return EntityMutationStepResult.allow();
        }
        Map<String, Object> effective =
                merged(beforeRecord, workingPayload);
        boolean allowed = policyMatcher.evaluateCondition(
                condition,
                command,
                beforeRecord,
                effective);
        return allowed
                ? EntityMutationStepResult.allow()
                : EntityMutationStepResult.block(
                        firstText(
                                config.get("message"),
                                "当前数据不在允许的操作范围内"));
    }

    private EntityMutationStepResult unique(
            Map<String, Object> config,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        if (command.operationType()
                == EntityMutationOperationType.DELETE) {
            return EntityMutationStepResult.allow();
        }
        List<String> fields = strings(first(
                config.get("fields"),
                config.get("field")));
        if (fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "唯一性校验未配置字段");
        }
        boolean ignoreBlank = booleanValue(
                config.get("ignoreBlank"),
                true);
        Map<String, Object> condition =
                new LinkedHashMap<>();
        for (String field : fields) {
            Object value = effectiveValue(
                    field,
                    beforeRecord,
                    workingPayload);
            if (blank(value)) {
                if (ignoreBlank) {
                    return EntityMutationStepResult.allow();
                }
                return EntityMutationStepResult.block(
                        firstText(
                                config.get("message"),
                                "唯一性校验字段不能为空: "
                                        + field));
            }
            String column = physicalField(field);
            condition.put(column, value);
            condition.put(column + "_op", "EQ");
        }
        if (command.operationType()
                        != EntityMutationOperationType.CREATE
                && StringUtils.hasText(command.recordId())) {
            condition.put("id", command.recordId());
            condition.put("id_op", "NE");
        }
        long matches = dynamicMapper.countByCondition(
                dynamicTableService.getTableName(
                        command.entityCode()),
                condition);
        return matches == 0
                ? EntityMutationStepResult.allow()
                : EntityMutationStepResult.block(
                        firstText(
                                config.get("message"),
                                "字段值已存在: "
                                        + String.join(", ", fields)));
    }

    private Map<String, Object> condition(
            Map<String, Object> config) {
        Map<String, Object> configured =
                map(config.get("condition"));
        if (!configured.isEmpty()) {
            return configured;
        }
        if (!config.containsKey("field")
                && !config.containsKey("all")
                && !config.containsKey("any")
                && !config.containsKey("not")) {
            return Map.of();
        }
        Map<String, Object> result =
                new LinkedHashMap<>(config);
        result.remove("rule");
        result.remove("message");
        return result;
    }

    private Object effectiveValue(
            String field,
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        Lookup workingValue = effectiveValue(
                field,
                workingPayload);
        if (workingValue.found()) {
            return workingValue.value();
        }
        return effectiveValue(field, beforeRecord).value();
    }

    private Lookup effectiveValue(
            String field,
            Map<String, Object> source) {
        Lookup direct = path(source, field);
        if (direct.found()) {
            return direct;
        }
        if (!field.startsWith("data.")) {
            return path(source, "data." + field);
        }
        return Lookup.missing();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> merged(
            Map<String, Object> beforeRecord,
            Map<String, Object> workingPayload) {
        Map<String, Object> result =
                new LinkedHashMap<>();
        if (beforeRecord != null) {
            beforeRecord.forEach((key, value) ->
                    result.put(key, copy(value)));
        }
        if (workingPayload != null) {
            workingPayload.forEach((key, value) -> {
                Object current = result.get(key);
                if (current instanceof Map<?, ?>
                        && value instanceof Map<?, ?>) {
                    result.put(
                            key,
                            merged(
                                    (Map<String, Object>) current,
                                    (Map<String, Object>) value));
                } else {
                    result.put(key, copy(value));
                }
            });
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object copy(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result =
                    new LinkedHashMap<>();
            ((Map<String, Object>) source).forEach(
                    (key, nested) ->
                            result.put(key, copy(nested)));
            return result;
        }
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .map(this::copy)
                    .toList();
        }
        return value;
    }

    private Lookup path(
            Map<String, Object> source,
            String field) {
        if (source == null
                || !StringUtils.hasText(field)) {
            return Lookup.missing();
        }
        Object current = source;
        for (String part : field.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)
                    || !map.containsKey(part)) {
                return Lookup.missing();
            }
            current = map.get(part);
        }
        return new Lookup(true, current);
    }

    private String physicalField(String field) {
        return field.startsWith("data.")
                ? field.substring("data.".length())
                : field;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            return new LinkedHashMap<>(
                    (Map<String, Object>) source);
        }
        return new LinkedHashMap<>();
    }

    private List<String> strings(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return value == null
                || !StringUtils.hasText(
                        String.valueOf(value))
                ? List.of()
                : List.of(String.valueOf(value));
    }

    private boolean blank(Object value) {
        return value == null
                || value instanceof String text
                && text.isBlank()
                || value instanceof Collection<?> values
                && values.isEmpty();
    }

    private boolean booleanValue(
            Object value,
            boolean fallback) {
        return value == null
                ? fallback
                : Boolean.parseBoolean(String.valueOf(value));
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null
                || !StringUtils.hasText(
                        String.valueOf(value))) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Object first(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(
                            String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private record Lookup(
            boolean found,
            Object value) {

        private static Lookup missing() {
            return new Lookup(false, null);
        }
    }
}
