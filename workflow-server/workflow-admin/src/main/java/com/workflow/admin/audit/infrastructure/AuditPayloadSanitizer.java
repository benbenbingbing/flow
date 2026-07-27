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
            Pattern.compile("(?i)([A-Z0-9._%+-])[A-Z0-9._%+-]*(@[A-Z0-9.-]+\\.[A-Z]{2,})");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern LONG_NUMBER_PATTERN =
            Pattern.compile("(?<!\\d)(\\d{2})\\d{8,15}(\\d{2})(?!\\d)");

    private final ObjectMapper objectMapper;
    private final int maxLength;

    public AuditPayloadSanitizer(
            ObjectMapper objectMapper,
            @Value("${workflow.audit.payload-max-length:32768}") int maxLength) {
        this.objectMapper = objectMapper;
        this.maxLength = Math.max(1024, maxLength);
    }

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

    private boolean isSecret(String field) {
        return SECRET_FIELDS.stream().anyMatch(field::contains);
    }

    private boolean isPersonal(String field) {
        return EMAIL_FIELDS.contains(field)
                || PHONE_FIELDS.contains(field)
                || ID_FIELDS.contains(field);
    }

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

    private String normalize(String field) {
        return field == null
                ? ""
                : field.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    public record SanitizedPayload(String json, boolean truncated) {
    }
}
