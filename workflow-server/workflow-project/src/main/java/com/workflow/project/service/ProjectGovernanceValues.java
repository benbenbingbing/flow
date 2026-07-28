package com.workflow.project.service;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Normalizes dynamic project fields used by governance rules.
 */
final class ProjectGovernanceValues {

    private ProjectGovernanceValues() {
    }

    static Map<String, Object> update(
            String status,
            Map<String, Object> customData) {
        Map<String, Object> update = new LinkedHashMap<>();
        if (StringUtils.hasText(status)) {
            update.put("status", status);
        }
        update.put("data", new LinkedHashMap<>(customData));
        return update;
    }

    static Map<String, Object> data(EntityDataDTO dto) {
        return dto.getData() == null
                ? Map.of()
                : dto.getData();
    }

    static void requireEntity(
            EntityDataDTO dto,
            String entityCode) {
        if (dto == null
                || !entityCode.equals(dto.getEntityCode())) {
            throw new BusinessConflictException(
                    "PROJECT_ENTITY_CONTEXT_INVALID",
                    "流程动作未获得正确的业务实体上下文");
        }
    }

    static String requireText(
            Map<String, Object> source,
            String fieldCode,
            String message) {
        String value = text(read(source, fieldCode));
        if (!StringUtils.hasText(value)) {
            conflict("PROJECT_REQUIRED_FIELD_MISSING", message);
        }
        return value;
    }

    static void validateDateRange(
            Object startValue,
            Object endValue,
            String label) {
        LocalDate start = date(startValue);
        LocalDate end = date(endValue);
        if (start == null
                || end == null
                || end.isBefore(start)) {
            conflict(
                    "PROJECT_DATE_RANGE_INVALID",
                    label + "结束日期不得早于开始日期");
        }
    }

    static void copy(
            Map<String, Object> source,
            Map<String, Object> target,
            String sourceKey,
            String targetKey) {
        Object value = read(source, sourceKey);
        if (value != null
                && (!(value instanceof String text)
                        || !text.isBlank())) {
            target.put(targetKey, value);
        }
    }

    static Object read(
            Map<String, Object> source,
            String snakeCaseKey) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        if (source.containsKey(snakeCaseKey)) {
            return source.get(snakeCaseKey);
        }
        StringBuilder camelCaseKey = new StringBuilder();
        boolean capitalizeNext = false;
        for (char character : snakeCaseKey.toCharArray()) {
            if (character == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                camelCaseKey.append(
                        Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                camelCaseKey.append(character);
            }
        }
        return source.get(camelCaseKey.toString());
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    static BigDecimal decimal(Object value) {
        if (value == null
                || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(String.valueOf(value));
    }

    static LocalDate date(Object value) {
        if (value == null
                || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        String text = String.valueOf(value);
        return LocalDate.parse(
                text.length() >= 10
                        ? text.substring(0, 10)
                        : text);
    }

    static boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return "true".equalsIgnoreCase(
                String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    static String text(Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }

    static String upper(Object value) {
        String text = text(value);
        return text == null
                ? null
                : text.toUpperCase(Locale.ROOT);
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    static <T> T firstNonNull(T first, T fallback) {
        return first != null ? first : fallback;
    }

    static void conflict(String errorCode, String message) {
        throw new BusinessConflictException(
                errorCode,
                message);
    }
}
