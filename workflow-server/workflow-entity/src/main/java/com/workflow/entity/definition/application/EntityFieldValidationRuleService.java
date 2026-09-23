package com.workflow.entity.definition.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 实体字段内置验证规则的定义校验、标准化和运行时执行。
 */
@Service
@RequiredArgsConstructor
public class EntityFieldValidationRuleService {

    private static final Set<EntityField.FieldType> TEXT_TYPES = Set.of(
            EntityField.FieldType.STRING,
            EntityField.FieldType.TEXT);
    private static final Set<EntityField.FieldType> NUMBER_TYPES = Set.of(
            EntityField.FieldType.INTEGER,
            EntityField.FieldType.LONG,
            EntityField.FieldType.DECIMAL);
    private static final Set<String> ALL_KEYS = Set.of(
            "minLength", "maxLength", "min", "max", "format");
    private static final Set<String> TEXT_KEYS = Set.of(
            "minLength", "maxLength", "format");
    private static final Set<String> NUMBER_KEYS = Set.of("min", "max");
    private static final Set<String> FORMATS = Set.of(
            "EMAIL", "PHONE", "URL");
    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern URL = Pattern.compile(
            "^https?://\\S+$",
            Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    /**
     * 校验并标准化一组字段定义中的验证规则。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     */
    public void validateAndNormalizeAll(List<EntityFieldDTO> fields) {
        if (fields == null) {
            return;
        }
        fields.stream()
                .filter(java.util.Objects::nonNull)
                .forEach(field -> field.setValidateRules(validateAndNormalize(
                        field.getFieldType(),
                        field.getValidateRules(),
                        field.getFieldName())));
    }

    /**
     * 校验字段类型支持的规则并返回标准 JSON；空对象标准化为空值。
     *
     * @param fieldType 字段类型标识，决定后续与规范化采用的处理分支
     * @param rawRules 原始规则集合，作为 {@code parseRules} 的输入影响后续处理
     * @param fieldName 字段名称，后续用于校验与规范化时匹配或展示
     * @return 校验后的与规范化文本，供调用方比较或展示
     */
    public String validateAndNormalize(
            EntityField.FieldType fieldType,
            String rawRules,
            String fieldName) {
        if (!StringUtils.hasText(rawRules)) {
            return null;
        }
        Map<String, Object> rules = parseRules(
                fieldType,
                rawRules,
                fieldName);
        if (rules.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException exception) {
            throw invalid(fieldName, "无法序列化验证规则", exception);
        }
    }

    /**
     * 按发布快照中的实体字段规则校验一次实际写入值。
     *
     * @param field 字段，作为 {@code parseRules} 的输入影响后续处理
     * @param value 待校验值的原始输入，结果供调用方继续使用
     */
    public void validateValue(EntityField field, Object value) {
        if (field == null
                || isBlank(value)
                || !StringUtils.hasText(field.getValidateRules())) {
            return;
        }
        Map<String, Object> rules = parseRules(
                field.getFieldType(),
                field.getValidateRules(),
                field.getFieldName());
        if (TEXT_TYPES.contains(field.getFieldType())) {
            validateText(field.getFieldName(), String.valueOf(value), rules);
        } else if (NUMBER_TYPES.contains(field.getFieldType())) {
            validateNumber(field.getFieldName(), value, rules);
        }
    }

    /**
     * 解析规则集合；输出作为后续校验或处理的输入。
     *
     * @param fieldType 字段类型标识，决定后续规则集合采用的处理分支
     * @param rawRules 原始规则集合，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @param fieldName 字段名称，后续用于解析规则集合时匹配或展示
     * @return 规则集合键值结果，供调用方继续处理
     */
    private Map<String, Object> parseRules(
            EntityField.FieldType fieldType,
            String rawRules,
            String fieldName) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawRules);
        } catch (JsonProcessingException exception) {
            throw invalid(fieldName, "必须是合法的 JSON 对象", exception);
        }
        if (root == null || !root.isObject()) {
            throw invalid(fieldName, "必须是 JSON 对象", null);
        }

        Set<String> allowedKeys = TEXT_TYPES.contains(fieldType)
                ? TEXT_KEYS
                : NUMBER_TYPES.contains(fieldType)
                ? NUMBER_KEYS
                : Set.of();
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!ALL_KEYS.contains(key)) {
                throw invalid(fieldName, "不支持规则 " + key, null);
            }
            if (!allowedKeys.contains(key)) {
                throw invalid(
                        fieldName,
                        "字段类型 " + fieldType
                                + " 不支持规则 " + key,
                        null);
            }
        });

        if (TEXT_TYPES.contains(fieldType)) {
            putLength(root, normalized, "minLength", fieldName);
            putLength(root, normalized, "maxLength", fieldName);
            putFormat(root, normalized, fieldName);
            Integer minLength = (Integer) normalized.get("minLength");
            Integer maxLength = (Integer) normalized.get("maxLength");
            if (minLength != null
                    && maxLength != null
                    && minLength > maxLength) {
                throw invalid(
                        fieldName,
                        "minLength 不能大于 maxLength",
                        null);
            }
        } else if (NUMBER_TYPES.contains(fieldType)) {
            putNumber(root, normalized, "min", fieldName);
            putNumber(root, normalized, "max", fieldName);
            BigDecimal min = (BigDecimal) normalized.get("min");
            BigDecimal max = (BigDecimal) normalized.get("max");
            if (min != null
                    && max != null
                    && min.compareTo(max) > 0) {
                throw invalid(fieldName, "min 不能大于 max", null);
            }
        }
        return normalized;
    }

    /**
     * 写入长度；后续读取或执行将使用更新后的状态。
     *
     * @param root 根，供本方法写入长度时使用
     * @param normalized 规范化，供本方法写入长度时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param fieldName 字段名称，后续用于写入长度时匹配或展示
     */
    private void putLength(
            JsonNode root,
            Map<String, Object> normalized,
            String key,
            String fieldName) {
        JsonNode value = root.get(key);
        if (value == null) {
            return;
        }
        if (!value.isIntegralNumber()
                || !value.canConvertToInt()
                || value.intValue() < 0
                || value.intValue() > 20_000) {
            throw invalid(
                    fieldName,
                    key + " 必须是 0 到 20000 之间的整数",
                    null);
        }
        normalized.put(key, value.intValue());
    }

    /**
     * 写入数值；后续读取或执行将使用更新后的状态。
     *
     * @param root 根，供本方法写入数值时使用
     * @param normalized 规范化，供本方法写入数值时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param fieldName 字段名称，后续用于写入数值时匹配或展示
     */
    private void putNumber(
            JsonNode root,
            Map<String, Object> normalized,
            String key,
            String fieldName) {
        JsonNode value = root.get(key);
        if (value == null) {
            return;
        }
        if (!value.isNumber()) {
            throw invalid(fieldName, key + " 必须是数字", null);
        }
        normalized.put(key, value.decimalValue());
    }

    /**
     * 写入{@code format}；后续读取或执行将使用更新后的状态。
     *
     * @param root 根，供本方法写入{@code format}时使用
     * @param normalized 规范化，供本方法写入{@code format}时使用
     * @param fieldName 字段名称，后续用于写入{@code format}时匹配或展示
     */
    private void putFormat(
            JsonNode root,
            Map<String, Object> normalized,
            String fieldName) {
        JsonNode value = root.get("format");
        if (value == null) {
            return;
        }
        if (!value.isTextual()) {
            throw invalid(fieldName, "format 必须是字符串", null);
        }
        String format = value.textValue().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(format)) {
            throw invalid(
                    fieldName,
                    "format 仅支持 EMAIL、PHONE 或 URL",
                    null);
        }
        normalized.put("format", format);
    }

    /**
     * 校验文本；不满足约束时阻止后续处理。
     *
     * @param fieldName 字段名称，后续用于校验文本时匹配或展示
     * @param value 待校验文本的原始输入，结果供调用方继续使用
     * @param rules 规则集合，供本方法校验文本时使用
     */
    private void validateText(
            String fieldName,
            String value,
            Map<String, Object> rules) {
        Integer minLength = (Integer) rules.get("minLength");
        Integer maxLength = (Integer) rules.get("maxLength");
        if (minLength != null && value.length() < minLength) {
            throw valueInvalid(
                    fieldName,
                    "长度不能小于 " + minLength);
        }
        if (maxLength != null && value.length() > maxLength) {
            throw valueInvalid(
                    fieldName,
                    "长度不能大于 " + maxLength);
        }
        String format = (String) rules.get("format");
        if (format == null) {
            return;
        }
        Pattern pattern = switch (format) {
            case "EMAIL" -> EMAIL;
            case "PHONE" -> PHONE;
            case "URL" -> URL;
            default -> null;
        };
        if (pattern != null && !pattern.matcher(value).matches()) {
            String label = switch (format) {
                case "EMAIL" -> "邮箱";
                case "PHONE" -> "手机号";
                case "URL" -> "URL";
                default -> format;
            };
            throw valueInvalid(
                    fieldName,
                    "不是合法的" + label);
        }
    }

    /**
     * 校验数值；不满足约束时阻止后续处理。
     *
     * @param fieldName 字段名称，后续用于校验数值时匹配或展示
     * @param value 待校验数值的原始输入，结果供调用方继续使用
     * @param rules 规则集合，供本方法校验数值时使用
     */
    private void validateNumber(
            String fieldName,
            Object value,
            Map<String, Object> rules) {
        BigDecimal number;
        try {
            number = new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw valueInvalid(fieldName, "必须为数字");
        }
        BigDecimal min = (BigDecimal) rules.get("min");
        BigDecimal max = (BigDecimal) rules.get("max");
        if (min != null && number.compareTo(min) < 0) {
            throw valueInvalid(
                    fieldName,
                    "不能小于 " + display(min));
        }
        if (max != null && number.compareTo(max) > 0) {
            throw valueInvalid(
                    fieldName,
                    "不能大于 " + display(max));
        }
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param fieldName 字段名称，后续用于处理无效时匹配或展示
     * @param detail 详情，供本方法处理无效时使用
     * @param cause 原因，供本方法处理无效时使用
     * @return 处理后的无效结果，供调用方继续处理
     */
    private RuntimeException invalid(
            String fieldName,
            String detail,
            Exception cause) {
        String message = "字段[" + displayName(fieldName)
                + "]验证规则" + detail;
        return cause == null
                ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }

    /**
     * 构造值无效异常，供调用方区分失败原因。
     *
     * @param fieldName 字段名称，后续用于处理值无效时匹配或展示
     * @param detail 详情，供本方法处理值无效时使用
     * @return 处理后的值无效结果，供调用方继续处理
     */
    private RuntimeException valueInvalid(
            String fieldName,
            String detail) {
        return new IllegalArgumentException(
                displayName(fieldName) + detail);
    }

    /**
     * 生成展示名称文本，供后续匹配或展示。
     *
     * @param fieldName 字段名称，后续用于处理展示名称时匹配或展示
     * @return 处理后的展示名称文本，供调用方比较或展示
     */
    private String displayName(String fieldName) {
        return StringUtils.hasText(fieldName)
                ? fieldName
                : "未命名字段";
    }

    /**
     * 生成展示文本，供后续匹配或展示。
     *
     * @param value 待处理展示的原始输入，结果供调用方继续使用
     * @return 处理后的展示文本，供调用方比较或展示
     */
    private String display(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 判断是否空白；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否空白的原始输入，结果供调用方继续使用
     * @return 空白条件成立时为 true，否则为 false
     */
    private boolean isBlank(Object value) {
        return value == null
                || value instanceof String text && text.isBlank();
    }
}
