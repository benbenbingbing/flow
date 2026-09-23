package com.workflow.admin.audit.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 将审计载荷转换为脱敏、限长 JSON，防止密码、令牌和大对象进入审计表。
 */
@Component
public class AuditPayloadSanitizer {

    private static final String MASK = "******";
    private static final Set<String> SECRET_FIELDS = Set.of(
            "password", "passwd", "pwd", "token", "authorization", "cookie",
            "secret", "secretvalue", "privatekey", "accesskey", "refreshtoken",
            "clientsecret", "signaturevalue", "packagedata");
    private static final Set<String> EMAIL_FIELDS = Set.of("email", "mail");
    private static final Set<String> PHONE_FIELDS = Set.of("phone", "mobile", "telephone");
    private static final Set<String> ID_FIELDS = Set.of(
            "idcard", "identitycard", "identitynumber", "bankcard", "cardnumber");
    private static final Pattern SECRET_TEXT_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|token|authorization|cookie|secret|private[-_ ]?key|"
                    + "access[-_ ]?key|refresh[-_ ]?token|client[-_ ]?secret)"
                    + "(\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&]+)");
    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i)([A-Z0-9._%+-])[A-Z0-9._%+-]*+(@[A-Z0-9.-]+\\.[A-Z]{2,})");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern LONG_NUMBER_PATTERN =
            Pattern.compile("(?<!\\d)(\\d{2})\\d{8,15}(\\d{2})(?!\\d)");

    private final ObjectMapper objectMapper;
    private final int maxLength;

    /**
     * 初始化审计载荷{@code sanitizer}，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param maxLength 最大长度，保存在对象中供后续校验、查询或展示
     */
    public AuditPayloadSanitizer(
            ObjectMapper objectMapper,
            @Value("${workflow.audit.payload-max-length:32768}") int maxLength) {
        this.objectMapper = objectMapper;
        this.maxLength = Math.max(1024, maxLength);
    }

    /**
     * 清洗审计载荷{@code sanitizer}；结果供调用方的后续步骤使用。
     *
     * @param value 待清洗审计载荷{@code sanitizer}的原始输入，结果供调用方继续使用
     * @return 清洗后的审计载荷{@code sanitizer}结果，供调用方继续处理
     */
    public SanitizedPayload sanitize(Object value) {
        if (value == null) {
            return new SanitizedPayload(null, false);
        }
        try {
            JsonNode root = objectMapper.valueToTree(value);
            redact(root);
            String json = objectMapper.writeValueAsString(root);
            if (json.length() <= maxLength) {
                return new SanitizedPayload(json, false);
            }
            return new SanitizedPayload(json.substring(0, maxLength), true);
        } catch (Exception exception) {
            return new SanitizedPayload(
                    objectMapper.createObjectNode()
                            .put("serializationError", exception.getClass().getSimpleName())
                            .put("valueType", value.getClass().getName())
                            .toString(),
                    false);
        }
    }

    /**
     * 清洗文本；结果供调用方的后续步骤使用。
     *
     * @param value 待清洗文本的原始输入，结果供调用方继续使用
     * @param maxLength 最大长度，作为 {@code Math.max} 的输入影响后续处理
     * @return 清洗后的文本文本，供调用方比较或展示
     */
    public String sanitizeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String sanitized = SECRET_TEXT_PATTERN.matcher(value).replaceAll("$1$2" + MASK);
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("$1" + MASK);
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("$1***$2");
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("$1****$2");
        sanitized = LONG_NUMBER_PATTERN.matcher(sanitized).replaceAll("$1****$2");
        int safeMaxLength = Math.max(0, maxLength);
        return sanitized.length() <= safeMaxLength
                ? sanitized
                : sanitized.substring(0, safeMaxLength);
    }

    /**
     * 处理{@code redact}，并将结果传给后续步骤。
     *
     * @param node 节点，供本方法处理{@code redact}时使用
     */
    private void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            for (Map.Entry<String, JsonNode> field : objectNode.properties()) {
                String normalized = normalize(field.getKey());
                JsonNode value = field.getValue();
                if (isSecret(normalized)) {
                    objectNode.put(field.getKey(), MASK);
                } else if (value != null && value.isTextual() && isPersonal(normalized)) {
                    objectNode.put(field.getKey(), maskPersonal(normalized, value.asText()));
                } else {
                    redact(value);
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redact);
        }
    }

    /**
     * 判断是否密钥；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否密钥时使用
     * @return 密钥条件成立时为 true，否则为 false
     */
    private boolean isSecret(String field) {
        return SECRET_FIELDS.stream().anyMatch(field::contains);
    }

    /**
     * 判断是否{@code personal}；判断结果决定调用方的后续分支。
     *
     * @param field 字段，作为 {@code EMAIL_FIELDS.contains} 的输入影响后续处理
     * @return {@code personal}条件成立时为 true，否则为 false
     */
    private boolean isPersonal(String field) {
        return EMAIL_FIELDS.contains(field)
                || PHONE_FIELDS.contains(field)
                || ID_FIELDS.contains(field);
    }

    /**
     * 生成{@code mask}{@code personal}文本，供后续匹配或展示。
     *
     * @param field 字段，供本方法处理{@code mask}{@code personal}时使用
     * @param value 待处理{@code mask}{@code personal}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code mask}{@code personal}文本，供调用方比较或展示
     */
    private String maskPersonal(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (EMAIL_FIELDS.contains(field)) {
            int separator = value.indexOf('@');
            return separator > 1 ? value.charAt(0) + "***" + value.substring(separator) : MASK;
        }
        int length = value.length();
        if (length <= 4) {
            return MASK;
        }
        return value.substring(0, 2) + "****" + value.substring(length - 2);
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param field 字段，供本方法规范化审计载荷{@code sanitizer}时使用
     * @return 规范化后的审计载荷{@code sanitizer}文本，供调用方比较或展示
     */
    private String normalize(String field) {
        return field == null
                ? ""
                : field.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 封装{@code sanitized}载荷的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param json JSON，保存在对象中供后续校验、查询或展示
     * @param truncated {@code truncated}，保存在对象中供后续校验、查询或展示
     */
    public record SanitizedPayload(String json, boolean truncated) {
    }
}
