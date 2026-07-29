package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.entity.version.api.request.EntityVersionSimulationRequest;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 已发布版本场景的统一匹配器。
 */
@Service
@RequiredArgsConstructor
public class EntityVersionPolicyMatcher {

    private final EntityVersionConfigurationService configurationService;
    private final ObjectMapper objectMapper;

    public Optional<MatchedScenario> matchPublished(
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        return configurationService
                .getPublished(command.entityCode())
                .filter(config ->
                        Boolean.TRUE.equals(config.getEnabled()))
                .flatMap(config -> match(
                        config,
                        command,
                        beforeRecord,
                        afterRecord));
    }

    public Optional<MatchedScenario> match(
            EntityVersionConfiguration configuration,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        if (configuration == null
                || !Boolean.TRUE.equals(
                        configuration.getEnabled())) {
            return Optional.empty();
        }
        return configuration.getScenarios().stream()
                .filter(item ->
                        !Boolean.FALSE.equals(item.getEnabled()))
                .sorted((left, right) -> Integer.compare(
                        value(right.getPriority()),
                        value(left.getPriority())))
                .filter(item -> matchesDimension(
                        item.getSourceTypes(),
                        command.context()
                                .sourceType().name()))
                .filter(item -> matchesDimension(
                        item.getOperationTypes(),
                        command.operationType().name()))
                .filter(item -> matchesDimension(
                        item.getBusinessIntents(),
                        command.context()
                                .businessIntentCode()))
                .filter(item -> evaluate(
                        item.getCondition(),
                        command,
                        beforeRecord,
                        afterRecord))
                .findFirst()
                .map(item -> new MatchedScenario(
                        item.getScenarioCode(),
                        item.getScenarioName(),
                        item.getVersionTitleTemplate(),
                        value(item.getPriority()),
                        configuration.getActiveReleaseId(),
                        configuration.getActiveReleaseVersion()));
    }

    public Map<String, Object> simulate(
            String entityCode,
            EntityVersionSimulationRequest request) {
        EntityVersionConfiguration configuration =
                configurationService.getDraft(entityCode);
        EntityMutationContext context =
                EntityMutationContext.builder(
                                enumValue(
                                        EntityMutationSourceType.class,
                                        request.getSourceType(),
                                        EntityMutationSourceType.SYSTEM_TASK),
                                defaultText(
                                        request.getBusinessIntentCode(),
                                        "UNSPECIFIED"),
                                defaultText(
                                        request.getBusinessIntentName(),
                                        request.getBusinessIntentCode()))
                        .sourceId(request.getSourceId())
                        .extraParams(request.getExtraParams())
                        .build();
        EntityMutationCommand command =
                new EntityMutationCommand(
                        "simulation",
                        entityCode,
                        defaultText(request.getRecordId(),
                                "simulation-record"),
                        enumValue(
                                EntityMutationOperationType.class,
                                request.getOperationType(),
                                EntityMutationOperationType.UPDATE),
                        request.getAfterRecord(),
                        context);
        Optional<MatchedScenario> matched = match(
                configuration,
                command,
                request.getBeforeRecord(),
                request.getAfterRecord());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matched.isPresent());
        result.put("configurationEnabled",
                Boolean.TRUE.equals(configuration.getEnabled()));
        result.put("configurationStatus",
                configuration.getStatus());
        result.put("scenario", matched
                .map(value -> Map.of(
                        "code", value.scenarioCode(),
                        "name", value.scenarioName(),
                        "priority", value.priority()))
                .orElse(null));
        return result;
    }

    public boolean evaluateCondition(
            Map<String, Object> condition,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        return evaluate(
                condition,
                command,
                beforeRecord,
                afterRecord);
    }

    private boolean matchesDimension(
            List<String> configured,
            String actual) {
        if (configured == null || configured.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(actual)) {
            return false;
        }
        return configured.stream()
                .filter(StringUtils::hasText)
                .anyMatch(item -> "*".equals(item)
                        || item.equalsIgnoreCase(actual));
    }

    @SuppressWarnings("unchecked")
    private boolean evaluate(
            Map<String, Object> condition,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        Object all = condition.get("all");
        if (all instanceof Collection<?> values) {
            return values.stream().allMatch(value ->
                    evaluate(asMap(value), command,
                            beforeRecord, afterRecord));
        }
        Object any = condition.get("any");
        if (any instanceof Collection<?> values) {
            return values.stream().anyMatch(value ->
                    evaluate(asMap(value), command,
                            beforeRecord, afterRecord));
        }
        Object not = condition.get("not");
        if (not instanceof Map<?, ?>) {
            return !evaluate(asMap(not), command,
                    beforeRecord, afterRecord);
        }
        if (condition.containsKey("field")) {
            return evaluateLeaf(condition, command,
                    beforeRecord, afterRecord);
        }

        Map<String, Object> effective =
                afterRecord == null ? Map.of() : afterRecord;
        return condition.entrySet().stream()
                .allMatch(entry -> Objects.equals(
                        path(effective, entry.getKey()),
                        entry.getValue()));
    }

    private boolean evaluateLeaf(
            Map<String, Object> condition,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        String field = text(condition.get("field"));
        String operator = defaultText(
                text(condition.get("operator")),
                "EQ").toUpperCase(Locale.ROOT);
        if ("CHANGED".equals(operator)) {
            return !Objects.equals(
                    path(beforeRecord, field),
                    path(afterRecord, field));
        }
        Object actual = resolveValue(
                defaultText(
                        text(condition.get("source")),
                        "AFTER"),
                field,
                command,
                beforeRecord,
                afterRecord);
        Object expected = condition.get("value");
        return switch (operator) {
            case "EQ" -> Objects.equals(actual, expected);
            case "NE" -> !Objects.equals(actual, expected);
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "IN" -> collection(expected).contains(actual);
            case "NOT_IN" -> !collection(expected).contains(actual);
            case "CONTAINS" -> contains(actual, expected);
            case "GT" -> actual != null
                    && expected != null
                    && compare(actual, expected) > 0;
            case "GTE" -> actual != null
                    && expected != null
                    && compare(actual, expected) >= 0;
            case "LT" -> actual != null
                    && expected != null
                    && compare(actual, expected) < 0;
            case "LTE" -> actual != null
                    && expected != null
                    && compare(actual, expected) <= 0;
            default -> throw new IllegalArgumentException(
                    "不支持的版本场景条件操作符: " + operator);
        };
    }

    private Object resolveValue(
            String source,
            String field,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "BEFORE" -> path(beforeRecord, field);
            case "PAYLOAD" -> path(command.payload(), field);
            case "CONTEXT" -> path(
                    objectMapper.convertValue(
                            command.context(),
                            Map.class),
                    field);
            case "EXTRA", "EXTRA_PARAMS" -> path(
                    command.context().extraParams(),
                    field);
            default -> path(afterRecord, field);
        };
    }

    private Object path(
            Map<String, Object> source,
            String field) {
        if (source == null || !StringUtils.hasText(field)) {
            return null;
        }
        Object current = source;
        for (String part : field.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private boolean contains(Object actual, Object expected) {
        if (actual instanceof Collection<?> collection) {
            return collection.contains(expected);
        }
        return actual != null && expected != null
                && String.valueOf(actual)
                .contains(String.valueOf(expected));
    }

    private int compare(Object actual, Object expected) {
        try {
            return new BigDecimal(String.valueOf(actual))
                    .compareTo(new BigDecimal(
                            String.valueOf(expected)));
        } catch (NumberFormatException ignored) {
            return String.valueOf(actual)
                    .compareTo(String.valueOf(expected));
        }
    }

    private Collection<?> collection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        List<Object> result = new ArrayList<>();
        if (value != null) {
            result.add(value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException(
                "版本场景条件节点必须是对象");
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String text(Object value) {
        return value == null
                ? null : String.valueOf(value).trim();
    }

    private String defaultText(
            String value,
            String fallback) {
        return StringUtils.hasText(value)
                ? value.trim()
                : (StringUtils.hasText(fallback)
                        ? fallback.trim() : "UNSPECIFIED");
    }

    private <T extends Enum<T>> T enumValue(
            Class<T> type,
            String value,
            T fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Enum.valueOf(
                    type,
                    value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    /**
     * 运行时命中的已发布版本场景。
     */
    public record MatchedScenario(
            String scenarioCode,
            String scenarioName,
            String versionTitleTemplate,
            int priority,
            String releaseId,
            Integer releaseVersion) {
    }
}
