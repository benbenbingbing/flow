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

/**
 * 列表字段条件求值器
 * 
 * 用于在内存中对实体数据列表进行二次过滤：依据列表字段配置中标记为可查询的字段，
 * 结合前端传入的查询条件（含 _op/_start/_end 后缀约定的操作符），逐条匹配并筛选记录。
 * 支持 EQ、NE、LIKE、GT/GE/LT/LE、BETWEEN、IN、EMPTY 等多种操作符。
 */
@Component
public class ListFieldConditionEvaluator {

    /**
     * 按可查询字段及其查询条件对记录进行内存过滤。
     *
     * @param records   待过滤的实体数据记录列表
     * @param fields    列表字段配置列表（仅处理 isQuery 为 true 的字段）
     * @param condition 查询条件，key 可为字段编码、字段编码_op、字段编码_start/_end
     * @return 过滤后的记录列表（无字段或无条件时原样返回）
     */
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
            // 跳过未开启查询功能的字段
            if (!Boolean.TRUE.equals(field.getIsQuery())) {
                continue;
            }
            String fieldCode = field.getFieldCode();
            if (fieldCode == null || fieldCode.isBlank() || !hasCondition(condition, fieldCode)) {
                continue;
            }
            // 移除不匹配当前字段条件的记录
            result.removeIf(record -> !matches(record, field, condition));
        }
        return result;
    }

    /**
     * 判断指定字段在条件中是否存在有效值（普通值或 _start/_end 范围值）。
     */
    private boolean hasCondition(Map<String, Object> condition, String fieldCode) {
        return hasValue(condition.get(fieldCode))
                || hasValue(condition.get(fieldCode + "_start"))
                || hasValue(condition.get(fieldCode + "_end"));
    }

    /**
     * 判断单条记录是否匹配指定字段的查询条件。
     * 操作符优先取条件中的 {fieldCode}_op，否则使用字段配置的 queryType，默认 EQ。
     */
    private boolean matches(EntityDataDTO record, EntityListField field, Map<String, Object> condition) {
        String fieldCode = field.getFieldCode();
        Object actual = getValue(record, fieldCode);
        String operator = String.valueOf(condition.getOrDefault(
                fieldCode + "_op",
                field.getQueryType() == null ? "EQ" : field.getQueryType())).toUpperCase();

        // BETWEEN 操作符单独处理，使用 _start/_end 范围值
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

    /**
     * 按优先级从记录中取字段值：扩展数据 > 业务数据 > 系统基础字段。
     */
    private Object getValue(EntityDataDTO record, String fieldCode) {
        if (record.getExtData() != null && record.getExtData().containsKey(fieldCode)) {
            return record.getExtData().get(fieldCode);
        }
        if (record.getData() != null && record.getData().containsKey(fieldCode)) {
            return record.getData().get(fieldCode);
        }
        // 系统基础字段映射
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

    /**
     * 判断 actual 是否包含 expected（支持集合递归匹配，比较时忽略大小写）。
     */
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

    /**
     * 判断 actual 是否在 expected 集合/数组/逗号分隔字符串中。
     */
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

    /**
     * 比较 actual 与 expected 的大小。数值按 BigDecimal 比较，其余按字符串比较；任一为空返回 -1。
     */
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

    /**
     * 将值转换为 BigDecimal，转换失败返回 null。
     */
    private BigDecimal number(Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 判断两个值是否相等，数值按 BigDecimal 比较，其余按字符串比较。
     */
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

    /**
     * 判断值是否有效（非 null、非空白字符串、非空集合）。
     */
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
