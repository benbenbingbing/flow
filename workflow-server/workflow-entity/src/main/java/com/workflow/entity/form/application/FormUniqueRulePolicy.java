package com.workflow.entity.form.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.form.application.model.FormUniqueCandidate;
import com.workflow.entity.form.application.model.FormUniqueRule;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表单唯一规则的解析、合法性校验、记录合并和规范化策略。
 *
 * <p>预检与事务内最终校验必须复用本组件，尤其不能分别实现条件求值或大小写处理，
 * 否则用户可能先收到“可用”提示，提交时却得到相反结果。</p>
 */
@Component
@RequiredArgsConstructor
public class FormUniqueRulePolicy {

    private static final int CONFIG_VERSION = 1;
    private static final int MAX_MESSAGE_LENGTH = 200;
    private static final int MAX_DEBOUNCE_MS = 5000;
    private static final DateTimeFormatter DATE_TIME_VALUE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern RULE_ID = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_-]{0,99}");
    private static final Set<String> UNSUPPORTED_FIELD_TYPES = Set.of(
            "FILE",
            "IMAGE",
            "SUB_FORM",
            "SUBLIST",
            "SUB_LIST",
            "REPEATER",
            "MULTI_SELECT",
            "MULTI_REFERENCE",
            "CHECKBOX",
            "RICH_TEXT",
            "SECTION");
    private static final Set<String> UNSUPPORTED_COMPONENT_TYPES = Set.of(
            "FILE",
            "IMAGE",
            "RICH_TEXT",
            "SELECT_MULTIPLE",
            "CHECKBOX",
            "MULTI_REFERENCE",
            "SUB_FORM",
            "SUB_LIST",
            "CASCADER",
            "SECTION");

    private final ObjectMapper objectMapper;
    private final PublishedFormConditionEvaluator conditionEvaluator;

    /** 解析表单发布快照中的全部已启用唯一规则。 */
    public List<FormUniqueRule> resolveRules(EntityForm form) {
        return form == null ? List.of() : resolveRules(form.getFields());
    }

    /** 解析字段列表中的全部已启用唯一规则。 */
    public List<FormUniqueRule> resolveRules(
            List<EntityFormField> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        List<FormUniqueRule> result = new ArrayList<>();
        for (EntityFormField field : fields) {
            FormUniqueRule rule = parseRule(
                    field,
                    Set.of(),
                    false);
            if (rule != null) {
                result.add(rule);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 校验草稿或发布快照中的唯一规则。
     *
     * @param fields          表单字段
     * @param validProperties 条件可以引用的实体字段；空集合时只做结构校验
     */
    public void validate(
            List<EntityFormField> fields,
            Set<String> validProperties) {
        validateInternal(fields, validProperties, false);
    }

    /**
     * 在发布边界校验唯一目标字段与条件字段都属于当前实体的持久化字段。
     *
     * <p>结构化保存阶段可能尚未加载实体元数据，因此 {@link #validate(List, Set)}
     * 保留只做结构校验的能力；发布时必须调用本方法，防止虚拟或已失效字段在
     * 运行时被拼成不存在的动态表列。</p>
     */
    public void validatePublished(
            List<EntityFormField> fields,
            Set<String> persistentProperties) {
        validateInternal(fields, persistentProperties, true);
    }

    private void validateInternal(
            List<EntityFormField> fields,
            Set<String> validProperties,
            boolean requirePersistentBinding) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        Set<String> ruleIds = new HashSet<>();
        for (EntityFormField field : fields) {
            FormUniqueRule rule = parseRule(
                    field,
                    validProperties == null
                            ? Set.of() : validProperties,
                    requirePersistentBinding);
            if (rule != null
                    && !ruleIds.add(
                            rule.ruleId().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "表单唯一规则标识重复: " + rule.ruleId());
            }
        }
    }

    /** 按稳定规则标识或字段编码选择当前表单自己的规则。 */
    public Optional<FormUniqueRule> findRule(
            EntityForm form,
            String ruleId,
            String fieldCode) {
        return resolveRules(form).stream()
                .filter(rule -> !StringUtils.hasText(ruleId)
                        || rule.ruleId().equals(ruleId))
                .filter(rule -> !StringUtils.hasText(fieldCode)
                        || rule.fieldCode().equals(fieldCode))
                .findFirst();
    }

    /**
     * 使用字段级补丁语义合并记录，并计算规则是否适用及比较值。
     */
    public FormUniqueCandidate prepare(
            FormUniqueRule rule,
            Map<String, Object> existingRecord,
            Map<String, Object> submittedData) {
        Map<String, Object> record = new LinkedHashMap<>();
        if (existingRecord != null) {
            record.putAll(flatten(existingRecord));
        }
        record.putAll(flatten(submittedData));
        boolean applicable = rule.mode() == FormUniqueRule.Mode.GLOBAL
                || conditionEvaluator.evaluateStructured(
                        rule.condition(),
                        record);
        Object rawValue = record.get(rule.fieldCode());
        boolean blank = isBlank(rawValue);
        return new FormUniqueCandidate(
                applicable,
                blank && rule.ignoreBlank(),
                blank ? "" : normalize(rule, rawValue),
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(record)));
    }

    /** 判断一条现存记录是否与候选值冲突。 */
    public boolean conflicts(
            FormUniqueRule rule,
            String normalizedValue,
            Map<String, Object> existingRecord) {
        if (rule.mode() == FormUniqueRule.Mode.CONDITIONAL
                && !conditionEvaluator.evaluateStructured(
                        rule.condition(),
                        existingRecord == null
                                ? Map.of() : existingRecord)) {
            return false;
        }
        Object existingValue = existingRecord == null
                ? null : existingRecord.get(rule.fieldCode());
        if (isBlank(existingValue)) {
            return !rule.ignoreBlank() && "".equals(normalizedValue);
        }
        return normalize(rule, existingValue).equals(normalizedValue);
    }

    /**
     * 将唯一比较值按确定性规则规范化。
     * 数字去除无意义尾零，其他值去除首尾空白并忽略大小写。
     */
    public String normalize(Object value) {
        if (value == null) {
            return null;
        }
        // 表单组件提交 Boolean，但 MySQL TINYINT(1) 常以 1/0 返回；统一到
        // 存储语义，确保提前预检与写后终检得到同一个比较值。
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        // Element Plus 以空格格式提交 DATETIME，而 JDBC/MyBatis 可能返回
        // LocalDateTime 或 Timestamp；统一到动态表 DATETIME 的秒级存储形状，
        // 否则提前预检会漏掉数据库中已存在的同一时刻。
        if (value instanceof Timestamp timestamp) {
            return DATE_TIME_VALUE.format(
                    timestamp.toLocalDateTime());
        }
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME_VALUE.format(dateTime);
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(String.valueOf(number))
                        .stripTrailingZeros()
                        .toPlainString();
            } catch (NumberFormatException ignored) {
                // 非有限浮点数继续采用稳定字符串表示。
            }
        }
        return String.valueOf(value)
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** 按实体字段类型规范化前端字符串与 JDBC 运行时值。 */
    private String normalize(
            FormUniqueRule rule,
            Object value) {
        String fieldType = rule == null
                ? "" : text(rule.fieldType()).toUpperCase(Locale.ROOT);
        if (Set.of("INTEGER", "LONG", "DECIMAL")
                .contains(fieldType)
                && value instanceof CharSequence textValue) {
            try {
                return new BigDecimal(textValue.toString().trim())
                        .stripTrailingZeros()
                        .toPlainString();
            } catch (NumberFormatException ignored) {
                // 非法数字仍按稳定字符串比较，字段类型校验会在其他层阻断写入。
            }
        }
        if ("BOOLEAN".equals(fieldType)
                && value instanceof CharSequence textValue) {
            String booleanValue = textValue.toString().trim();
            if ("true".equalsIgnoreCase(booleanValue)
                    || "1".equals(booleanValue)) {
                return "1";
            }
            if ("false".equalsIgnoreCase(booleanValue)
                    || "0".equals(booleanValue)) {
                return "0";
            }
        }
        if ("DATE".equals(fieldType)) {
            return normalizeDate(value);
        }
        if ("DATETIME".equals(fieldType)) {
            return normalizeDateTime(value);
        }
        return normalize(value);
    }

    /** 将 DATE 的表单字符串和 JDBC 类型统一为 ISO 日期。 */
    private String normalizeDate(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime()
                    .toLocalDate()
                    .toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate().toString();
        }
        String source = String.valueOf(value).trim();
        try {
            return LocalDate.parse(source).toString();
        } catch (RuntimeException ignored) {
            return normalize(value);
        }
    }

    /**
     * 将 DATETIME 的空格格式、ISO T 格式、OffsetDateTime 与 JDBC 类型
     * 统一为动态表使用的秒级本地时间形状。
     */
    private String normalizeDateTime(Object value) {
        LocalDateTime dateTime = null;
        if (value instanceof Timestamp timestamp) {
            dateTime = timestamp.toLocalDateTime();
        } else if (value instanceof LocalDateTime localDateTime) {
            dateTime = localDateTime;
        } else if (value instanceof OffsetDateTime offsetDateTime) {
            dateTime = offsetDateTime.toLocalDateTime();
        } else {
            String source = String.valueOf(value).trim();
            try {
                dateTime = LocalDateTime.parse(
                        source,
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (RuntimeException ignored) {
                try {
                    dateTime = Timestamp.valueOf(source)
                            .toLocalDateTime();
                } catch (RuntimeException ignoredAgain) {
                    try {
                        dateTime = OffsetDateTime.parse(source)
                                .toLocalDateTime();
                    } catch (RuntimeException ignoredOffset) {
                        return normalize(value);
                    }
                }
            }
        }
        return DATE_TIME_VALUE.format(dateTime);
    }

    private FormUniqueRule parseRule(
            EntityFormField field,
            Set<String> validProperties,
            boolean requirePersistentBinding) {
        if (field == null || !StringUtils.hasText(
                field.getValidationRules())) {
            return null;
        }
        Map<String, Object> validation = readObject(
                field.getValidationRules(),
                fieldLabel(field) + "校验规则");
        Object configured = validation.get("uniqueness");
        if (configured == null) {
            return null;
        }
        if (!(configured instanceof Map<?, ?> rawRule)) {
            throw invalid(field, "唯一性配置必须为对象");
        }
        Map<String, Object> source = stringMap(rawRule);
        if (source.containsKey("enabled")
                && !(source.get("enabled") instanceof Boolean)) {
            throw invalid(field, "唯一性 enabled 必须为布尔值");
        }
        boolean enabled = !source.containsKey("enabled")
                || Boolean.TRUE.equals(source.get("enabled"));
        String rawMode = text(source.get("mode"));
        if (!enabled || "NONE".equalsIgnoreCase(rawMode)) {
            return null;
        }
        if (integer(source.get("version")) != CONFIG_VERSION) {
            throw invalid(field, "唯一性配置仅支持 version=1");
        }
        FormUniqueRule.Mode mode;
        try {
            mode = FormUniqueRule.Mode.valueOf(
                    rawMode.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid(field, "唯一性模式必须为 GLOBAL 或 CONDITIONAL");
        }
        String fieldCode = text(field.getFieldCode());
        if (!StringUtils.hasText(fieldCode)) {
            throw invalid(field, "唯一性规则必须绑定实体字段");
        }
        if (requirePersistentBinding
                && !validProperties.contains(fieldCode)) {
            throw invalid(
                    field,
                    "唯一性规则必须绑定当前实体的持久化字段");
        }
        String fieldType = text(field.getFieldType())
                .toUpperCase(Locale.ROOT);
        if (UNSUPPORTED_FIELD_TYPES.contains(fieldType)) {
            throw invalid(
                    field,
                    "当前字段类型不支持唯一性校验: " + fieldType);
        }
        String componentType = text(field.getComponentType())
                .toUpperCase(Locale.ROOT);
        if (UNSUPPORTED_COMPONENT_TYPES.contains(componentType)) {
            throw invalid(
                    field,
                    "当前组件不支持唯一性校验: " + componentType);
        }
        String ruleId = text(source.get("ruleId"));
        if (!StringUtils.hasText(ruleId)) {
            ruleId = "uq_" + fieldCode;
        }
        if (!RULE_ID.matcher(ruleId).matches()) {
            throw invalid(field, "唯一规则标识不合法");
        }
        boolean ignoreBlank = booleanValue(
                source.get("ignoreBlank"),
                true,
                field,
                "ignoreBlank");
        String normalizationText = text(
                source.getOrDefault(
                        "normalization",
                        FormUniqueRule.Normalization
                                .TRIM_CASE_INSENSITIVE.name()));
        FormUniqueRule.Normalization normalization;
        try {
            normalization = FormUniqueRule.Normalization.valueOf(
                    normalizationText.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid(field, "不支持的唯一值规范化方式");
        }
        Map<String, Object> condition = mapValue(
                source.get("condition"));
        if (mode == FormUniqueRule.Mode.CONDITIONAL) {
            conditionEvaluator.validateStructured(
                    source.get("condition"),
                    validProperties,
                    fieldLabel(field) + "条件唯一：");
        }
        String message = text(source.get("message"));
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw invalid(field, "唯一性错误提示不能超过 200 个字符");
        }
        if (!StringUtils.hasText(message)) {
            message = displayLabel(field) + "已存在";
        }
        FormUniqueRule.Precheck precheck = parsePrecheck(
                source.get("precheck"),
                field);
        return new FormUniqueRule(
                CONFIG_VERSION,
                ruleId,
                fieldCode,
                displayLabel(field),
                fieldType,
                mode,
                ignoreBlank,
                normalization,
                condition,
                message,
                precheck);
    }

    private FormUniqueRule.Precheck parsePrecheck(
            Object configured,
            EntityFormField field) {
        if (configured == null) {
            return new FormUniqueRule.Precheck(
                    true,
                    FormUniqueRule.Trigger.BLUR,
                    500,
                    true);
        }
        if (!(configured instanceof Map<?, ?> rawPrecheck)) {
            throw invalid(field, "唯一性 precheck 必须为对象");
        }
        Map<String, Object> source = stringMap(rawPrecheck);
        boolean enabled = booleanValue(
                source.get("enabled"),
                true,
                field,
                "precheck.enabled");
        String triggerText = text(source.getOrDefault(
                "trigger", "BLUR"));
        FormUniqueRule.Trigger trigger;
        try {
            trigger = FormUniqueRule.Trigger.valueOf(
                    triggerText.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid(
                    field,
                    "预检触发方式必须为 CHANGE、BLUR 或 SUBMIT_ONLY");
        }
        int debounceMs = source.containsKey("debounceMs")
                ? integer(source.get("debounceMs")) : 500;
        if (debounceMs < 0 || debounceMs > MAX_DEBOUNCE_MS) {
            throw invalid(field, "预检防抖时间必须在 0 到 5000 毫秒之间");
        }
        boolean watchConditionFields = booleanValue(
                source.get("watchConditionFields"),
                true,
                field,
                "precheck.watchConditionFields");
        return new FormUniqueRule.Precheck(
                enabled,
                trigger,
                debounceMs,
                watchConditionFields);
    }

    private boolean booleanValue(
            Object value,
            boolean defaultValue,
            EntityFormField field,
            String key) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean bool)) {
            throw invalid(field, "唯一性 " + key + " 必须为布尔值");
        }
        return bool;
    }

    private Map<String, Object> flatten(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(source);
        Object nested = result.remove("data");
        if (nested instanceof Map<?, ?> map) {
            result.putAll(stringMap(map));
        }
        return result;
    }

    private boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.toString().isBlank();
        }
        if (value instanceof Collection<?> values) {
            return values.isEmpty();
        }
        if (value instanceof Map<?, ?> values) {
            return values.isEmpty();
        }
        return value.getClass().isArray()
                && Array.getLength(value) == 0;
    }

    private Map<String, Object> readObject(
            String json,
            String label) {
        try {
            Map<String, Object> result = objectMapper.readValue(
                    json,
                    new TypeReference<Map<String, Object>>() {});
            return result == null ? Map.of() : result;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    label + "不是合法 JSON 对象",
                    exception);
        }
    }

    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map
                ? Map.copyOf(stringMap(map)) : Map.of();
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(
                String.valueOf(key), value));
        return result;
    }

    private int integer(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private String displayLabel(EntityFormField field) {
        if (StringUtils.hasText(field.getFieldLabel())) {
            return field.getFieldLabel().trim();
        }
        if (StringUtils.hasText(field.getFieldName())) {
            return field.getFieldName().trim();
        }
        return field.getFieldCode();
    }

    private String fieldLabel(EntityFormField field) {
        return "字段“" + displayLabel(field) + "”";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private IllegalArgumentException invalid(
            EntityFormField field,
            String message) {
        return new IllegalArgumentException(
                fieldLabel(field) + message);
    }
}
