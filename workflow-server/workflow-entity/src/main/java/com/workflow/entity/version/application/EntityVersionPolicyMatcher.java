package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
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
 * 当前数据版本场景的统一匹配器。
 */
@Service
@RequiredArgsConstructor
public class EntityVersionPolicyMatcher {

    private final EntityVersionConfigurationService configurationService;
    private final ObjectMapper objectMapper;

    /**
     * 匹配实体版本策略匹配器当前；判断结果决定调用方的后续分支。
     *
     * @param command 本次命令，后续经校验后用于匹配实体版本策略匹配器当前
     * @param beforeRecord 之前记录，供本方法匹配实体版本策略匹配器当前时使用
     * @param afterRecord 之后记录，供本方法匹配实体版本策略匹配器当前时使用
     * @return 匹配的实体版本策略匹配器当前；未找到时为空
     */
    public Optional<MatchedScenario> matchCurrent(
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        return configurationService
                .getCurrent(command.entityCode())
                .filter(config ->
                        Boolean.TRUE.equals(config.getEnabled()))
                .flatMap(config -> match(
                        config,
                        command,
                        beforeRecord,
                        afterRecord));
    }

    /**
     * 匹配实体版本策略匹配器；判断结果决定调用方的后续分支。
     *
     * @param configuration 配置内容，决定后续实体版本策略匹配器的处理规则
     * @param command 本次命令，后续经校验后用于匹配实体版本策略匹配器
     * @param beforeRecord 之前记录，作为 {@code matchTriggers} 的输入影响后续处理
     * @param afterRecord 之后记录，供本方法匹配实体版本策略匹配器时使用
     * @return 匹配的实体版本策略匹配器；未找到时为空
     */
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
        if (value(configuration.getSchemaVersion()) >= 2
                && configuration.getTriggers() != null
                && !configuration.getTriggers().isEmpty()) {
            return matchTriggers(
                    configuration,
                    "ROOT_MUTATION",
                    null,
                    command,
                    beforeRecord,
                    afterRecord);
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
                        configuration));
    }

    /**
     * 匹配人工；判断结果决定调用方的后续分支。
     *
     * @param configuration 配置内容，决定后续人工的处理规则
     * @param requestedTriggerCode 请求触发条件编码，后续用于匹配人工时定位或关联目标
     * @return 匹配的人工；未找到时为空
     */
    public Optional<MatchedScenario> matchManual(
            EntityVersionConfiguration configuration,
            String requestedTriggerCode) {
        if (configuration == null
                || !Boolean.TRUE.equals(configuration.getEnabled())) {
            return Optional.empty();
        }
        return configuration.getTriggers().stream()
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .filter(item -> "MANUAL".equals(item.getTriggerType()))
                .filter(item -> !StringUtils.hasText(requestedTriggerCode)
                        || item.getTriggerCode().equalsIgnoreCase(
                                requestedTriggerCode.trim()))
                .sorted((left, right) -> Integer.compare(
                        value(right.getPriority()), value(left.getPriority())))
                .findFirst()
                .map(item -> matched(configuration, item));
    }

    /**
     * 匹配关联；判断结果决定调用方的后续分支。
     *
     * @param configuration 配置内容，决定后续关联的处理规则
     * @param relationCode 关系编码，后续用于匹配关联时定位或关联目标
     * @param command 本次命令，后续经校验后用于匹配关联
     * @param beforeRecord 之前记录，作为 {@code matchTriggers} 的输入影响后续处理
     * @param afterRecord 之后记录，供本方法匹配关联时使用
     * @return 匹配的关联；未找到时为空
     */
    public Optional<MatchedScenario> matchRelated(
            EntityVersionConfiguration configuration,
            String relationCode,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        return matchTriggers(
                configuration,
                "RELATED_MUTATION",
                relationCode,
                command,
                beforeRecord,
                afterRecord);
    }

    /**
     * 匹配{@code triggers}；判断结果决定调用方的后续分支。
     *
     * @param configuration 配置内容，决定后续{@code triggers}的处理规则
     * @param triggerType 触发条件类型标识，决定后续{@code triggers}采用的处理分支
     * @param relationCode 关系编码，后续用于匹配{@code triggers}时定位或关联目标
     * @param command 本次命令，后续经校验后用于匹配{@code triggers}
     * @param beforeRecord 之前记录，供本方法匹配{@code triggers}时使用
     * @param afterRecord 之后记录，供本方法匹配{@code triggers}时使用
     * @return 匹配的{@code triggers}；未找到时为空
     */
    private Optional<MatchedScenario> matchTriggers(
            EntityVersionConfiguration configuration,
            String triggerType,
            String relationCode,
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        if (configuration == null
                || !Boolean.TRUE.equals(configuration.getEnabled())) {
            return Optional.empty();
        }
        return configuration.getTriggers().stream()
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .filter(item -> triggerType.equals(item.getTriggerType()))
                .filter(item -> relationCode == null
                        || relationCode.equals(item.getRelationCode()))
                .sorted((left, right) -> Integer.compare(
                        value(right.getPriority()), value(left.getPriority())))
                .filter(item -> matchesDimension(
                        item.getSourceTypes(),
                        command.context().sourceType().name()))
                .filter(item -> matchesDimension(
                        item.getOperationTypes(),
                        command.operationType().name()))
                .filter(item -> matchesDimension(
                        item.getBusinessIntents(),
                        command.context().businessIntentCode()))
                .filter(item -> evaluate(
                        item.getCondition(),
                        command,
                        beforeRecord,
                        afterRecord))
                .findFirst()
                .map(item -> matched(configuration, item));
    }

    /**
     * 处理{@code matched}，并将结果传给后续步骤。
     *
     * @param configuration 配置内容，决定后续{@code matched}的处理规则
     * @param trigger 触发条件，作为 {@code MatchedScenario} 的输入影响后续处理
     * @return 处理后的{@code matched}结果，供调用方继续处理
     */
    private MatchedScenario matched(
            EntityVersionConfiguration configuration,
            EntityVersionConfiguration.CaptureTrigger trigger) {
        return new MatchedScenario(
                trigger.getTriggerCode(),
                trigger.getTriggerName(),
                trigger.getVersionTitleTemplate(),
                value(trigger.getPriority()),
                configuration);
    }

    /**
     * 整理{@code simulate}数据，供调用方遍历或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param request 本次请求，后续经校验后用于处理{@code simulate}
     * @return {@code simulate}键值结果，供调用方继续处理
     */
    public Map<String, Object> simulate(
            String entityCode,
            EntityVersionSimulationRequest request) {
        EntityVersionConfiguration configuration =
                configurationService.get(entityCode);
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
        result.put("scenario", matched
                .map(value -> Map.of(
                        "code", value.scenarioCode(),
                        "name", value.scenarioName(),
                        "priority", value.priority()))
                .orElse(null));
        return result;
    }

    /**
     * 判断是否匹配{@code dimension}；判断结果决定调用方的后续分支。
     *
     * @param configured 已配置，供本方法判断是否匹配{@code dimension}时使用
     * @param actual 实际，供本方法判断是否匹配{@code dimension}时使用
     * @return {@code dimension}条件成立时为 true，否则为 false
     */
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

    /**
     * 求值实体版本策略匹配器，并将结果传给后续步骤。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param command 本次命令，后续经校验后用于求值实体版本策略匹配器
     * @param beforeRecord 之前记录，作为 {@code evaluateLeaf} 的输入影响后续处理
     * @param afterRecord 之后记录，作为 {@code evaluateLeaf} 的输入影响后续处理
     * @return 实体版本策略匹配器条件成立时为 true，否则为 false
     */
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

    /**
     * 求值{@code leaf}，并将结果传给后续步骤。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param command 本次命令，后续经校验后用于求值{@code leaf}
     * @param beforeRecord 之前记录，供本方法求值{@code leaf}时使用
     * @param afterRecord 之后记录，供本方法求值{@code leaf}时使用
     * @return {@code leaf}条件成立时为 true，否则为 false
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 解析值；输出作为后续校验或处理的输入。
     *
     * @param source 待解析值的原始输入，结果供调用方继续使用
     * @param field 字段，作为 {@code path} 的输入影响后续处理
     * @param command 本次命令，后续经校验后用于解析值
     * @param beforeRecord 之前记录，作为 {@code path} 的输入影响后续处理
     * @param afterRecord 之后记录，作为 {@code path} 的输入影响后续处理
     * @return 解析后的值结果，供调用方继续处理
     */
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

    /**
     * 处理路径，并将结果传给后续步骤。
     *
     * @param source 待处理路径的原始输入，结果供调用方继续使用
     * @param field 字段，供本方法处理路径时使用
     * @return 处理后的路径结果，供调用方继续处理
     */
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

    /**
     * 判断是否包含实体版本策略匹配器；判断结果决定调用方的后续分支。
     *
     * @param actual 实际，供本方法判断是否包含实体版本策略匹配器时使用
     * @param expected 预期，供本方法判断是否包含实体版本策略匹配器时使用
     * @return 实体版本策略匹配器条件成立时为 true，否则为 false
     */
    private boolean contains(Object actual, Object expected) {
        if (actual instanceof Collection<?> collection) {
            return collection.contains(expected);
        }
        return actual != null && expected != null
                && String.valueOf(actual)
                .contains(String.valueOf(expected));
    }

    /**
     * 比较实体版本策略匹配器；结果供调用方的后续步骤使用。
     *
     * @param actual 实际，作为 {@code BigDecimal} 的输入影响后续处理
     * @param expected 预期，供本方法比较实体版本策略匹配器时使用
     * @return 比较后的实体版本策略匹配器结果，供调用方继续处理
     */
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

    /**
     * 整理集合数据，供调用方遍历或继续处理。
     *
     * @param value 待处理集合的原始输入，结果供调用方继续使用
     * @return {@code collection<?>}集合，供调用方遍历或展示
     */
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

    /**
     * 转换为映射；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException(
                "版本场景条件节点必须是对象");
    }

    /**
     * 读取或规范化输入值，供后续计算与比较使用。
     *
     * @param value 待处理值的原始输入，结果供调用方继续使用
     * @return 处理后的值结果，供调用方继续处理
     */
    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null
                ? null : String.valueOf(value).trim();
    }

    /**
     * 生成默认文本文本，供后续匹配或展示。
     *
     * @param value 待处理默认文本的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的默认文本文本，供调用方比较或展示
     */
    private String defaultText(
            String value,
            String fallback) {
        return StringUtils.hasText(value)
                ? value.trim()
                : (StringUtils.hasText(fallback)
                        ? fallback.trim() : "UNSPECIFIED");
    }

    /**
     * 处理枚举值，并将结果传给后续步骤。
     *
     * @param type 类型标识，决定后续枚举值采用的处理分支
     * @param value 待处理枚举值的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的枚举值结果，供调用方继续处理
     */
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
     * 运行时命中的版本场景及其同一次读取所得的冻结配置。
     *
     * <p>捕获必须直接使用这里携带的配置，禁止再次查询当前配置，否则并发保存可能
     * 把旧触发器与新范围拼接成不存在的组合。</p>
     *
     * @param scenarioCode {@code scenario}编码，后续用于处理{@code matched}{@code scenario}时定位或关联目标
     * @param scenarioName {@code scenario}名称，后续用于处理{@code matched}{@code scenario}时匹配或展示
     * @param versionTitleTemplate 版本{@code title}模板，保存在对象中供后续校验、查询或展示
     * @param priority 优先级，保存在对象中供后续校验、查询或展示
     * @param configuration 配置内容，决定后续{@code matched}{@code scenario}的处理规则
     */
    public record MatchedScenario(
            String scenarioCode,
            String scenarioName,
            String versionTitleTemplate,
            int priority,
            EntityVersionConfiguration configuration) {
    }
}
