package com.workflow.service.listfield;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ListFieldConditionEvaluator {

    public List<EntityDataDTO> filter(
            List<EntityDataDTO> records,
            List<EntityListField> fields,
            Map<String, Object> condition) {
        if (records == null || records.isEmpty() || fields == null || fields.isEmpty()
                || condition == null || condition.isEmpty()) {
            return records;
        }

        List<EntityDataDTO> result = new ArrayList<>(records);
        for (EntityListField field : fields) {
            if (!Boolean.TRUE.equals(field.getIsQuery())) {
                continue;
            }
            String fieldCode = field.getFieldCode();
            if (fieldCode == null || fieldCode.isBlank() || !hasCondition(condition, fieldCode)) {
                continue;
            }
            result.removeIf(record -> !matches(record, field, condition));
        }
        return result;
    }

    private boolean hasCondition(Map<String, Object> condition, String fieldCode) {
        return hasValue(condition.get(fieldCode))
                || hasValue(condition.get(fieldCode + "_start"))
                || hasValue(condition.get(fieldCode + "_end"));
    }

    private boolean matches(EntityDataDTO record, EntityListField field, Map<String, Object> condition) {
        String fieldCode = field.getFieldCode();
        Object actual = getValue(record, fieldCode);
        String operator = String.valueOf(condition.getOrDefault(
                fieldCode + "_op",
                field.getQueryType() == null ? "EQ" : field.getQueryType())).toUpperCase();

        if ("BETWEEN".equals(operator)) {
            Object start = condition.get(fieldCode + "_start");
            Object end = condition.get(fieldCode + "_end");
            return (!hasValue(start) || compare(actual, start) >= 0)
                    && (!hasValue(end) || compare(actual, end) <= 0);
        }

        Object expected = condition.get(fieldCode);
        return switch (operator) {
            case "EQ" -> equalsValue(actual, expected);
            case "NE" -> !equalsValue(actual, expected);
            case "LIKE", "CONTAINS" -> contains(actual, expected);
            case "NOT_LIKE", "NOT_CONTAINS" -> !contains(actual, expected);
            case "GT" -> compare(actual, expected) > 0;
            case "GE", "GTE" -> compare(actual, expected) >= 0;
            case "LT" -> compare(actual, expected) < 0;
            case "LE", "LTE" -> compare(actual, expected) <= 0;
            case "IN" -> in(actual, expected);
            case "NOT_IN" -> !in(actual, expected);
            case "EMPTY", "IS_EMPTY" -> !hasValue(actual);
            case "NOT_EMPTY", "IS_NOT_EMPTY" -> hasValue(actual);
            default -> false;
        };
    }

    private Object getValue(EntityDataDTO record, String fieldCode) {
        if (record.getExtData() != null && record.getExtData().containsKey(fieldCode)) {
            return record.getExtData().get(fieldCode);
        }
        if (record.getData() != null && record.getData().containsKey(fieldCode)) {
            return record.getData().get(fieldCode);
        }
        return switch (fieldCode) {
            case "id" -> record.getId();
            case "dataNo" -> record.getDataNo();
            case "name" -> record.getName();
            case "title" -> record.getTitle();
            case "status" -> record.getStatus();
            case "createdBy" -> record.getCreatedBy();
            case "submitterId" -> record.getSubmitterId();
            case "submitterName" -> record.getSubmitterName();
            case "deptId" -> record.getDeptId();
            case "deptName" -> record.getDeptName();
            case "submitTime" -> record.getSubmitTime();
            case "processInstanceId" -> record.getProcessInstanceId();
            case "currentTaskId" -> record.getCurrentTaskId();
            case "currentTaskName" -> record.getCurrentTaskName();
            case "currentTaskAssignee" -> record.getCurrentTaskAssignee();
            case "processStartTime" -> record.getProcessStartTime();
            case "processEndTime" -> record.getProcessEndTime();
            case "createdAt" -> record.getCreatedAt();
            case "updatedAt" -> record.getUpdatedAt();
            default -> null;
        };
    }

    private boolean contains(Object actual, Object expected) {
        if (!hasValue(actual) || !hasValue(expected)) {
            return false;
        }
        if (actual instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> contains(item, expected));
        }
        return String.valueOf(actual).toLowerCase()
                .contains(String.valueOf(expected).toLowerCase());
    }

    private boolean in(Object actual, Object expected) {
        if (expected instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> equalsValue(actual, item));
        }
        if (expected != null && expected.getClass().isArray()) {
            Object[] values = (Object[]) expected;
            for (Object value : values) {
                if (equalsValue(actual, value)) {
                    return true;
                }
            }
            return false;
        }
        if (expected instanceof String text) {
            for (String value : text.split(",")) {
                if (equalsValue(actual, value.trim())) {
                    return true;
                }
            }
        }
        return equalsValue(actual, expected);
    }

    private int compare(Object actual, Object expected) {
        if (!hasValue(actual) || !hasValue(expected)) {
            return -1;
        }
        BigDecimal actualNumber = number(actual);
        BigDecimal expectedNumber = number(expected);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber);
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    private BigDecimal number(Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean equalsValue(Object actual, Object expected) {
        BigDecimal actualNumber = number(actual);
        BigDecimal expectedNumber = number(expected);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber) == 0;
        }
        return Objects.equals(
                actual == null ? null : String.valueOf(actual),
                expected == null ? null : String.valueOf(expected));
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.trim().isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }
}
