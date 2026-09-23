package com.workflow.entity.data.application;

import com.workflow.integration.database.api.SchemaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 动态实体比较值的纯类型转换，普通查询与权限规则共用，不执行数据库访问。 */
public final class EntityQueryScalarValues {
    private EntityQueryScalarValues() { }

    /**
     * 按可信发布字段的物理类型转换一个比较值，保留 NULL，不猜测字符串的用途。
     * 调用方必须先选定比较操作符；LIKE 模式不能经过数值或日期转换。
     * 非法格式、非整数、溢出及带时区时间均失败，避免依赖数据库静默转换。
     */
    public static Object scalarValue(Object value, SchemaType.Kind kind) {
        if (kind == null) throw new IllegalArgumentException("查询字段缺少存储类型");
        if (value == null) return null;
        try {
            return switch (kind) {
                case STRING, TEXT, LARGE_TEXT -> String.valueOf(value);
                case INTEGER, BYTE -> decimal(value).intValueExact();
                case LONG -> decimal(value).longValueExact();
                case DECIMAL -> decimal(value);
                case BOOLEAN -> booleanValue(value);
                case DATE -> value instanceof LocalDate day ? day
                        : value instanceof java.sql.Date day ? day.toLocalDate()
                        : LocalDate.parse(String.valueOf(value).trim());
                case TIMESTAMP -> timestampValue(value);
            };
        } catch (RuntimeException invalid) {
            // 不把原始值写入错误信息，同一转换也用于可能含敏感标识的权限规则。
            throw new IllegalArgumentException("查询比较值不符合字段类型 " + kind, invalid);
        }
    }

    /** 与转换后的 Java 值配套使用；只指定 JDBC 类型而保留 String 仍可能调用 setString。 */
    public static String jdbcType(SchemaType.Kind kind) {
        if (kind == null) throw new IllegalArgumentException("查询字段缺少存储类型");
        return switch (kind) {
            case STRING, TEXT, LARGE_TEXT -> "VARCHAR";
            case INTEGER, BYTE, BOOLEAN -> "INTEGER";
            case LONG -> "BIGINT";
            case DECIMAL -> "DECIMAL";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP";
        };
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value instanceof Boolean flag ? (flag ? "1" : "0") : String.valueOf(value).trim());
    }

    private static int booleanValue(Object value) {
        if (value instanceof Boolean flag) return flag ? 1 : 0;
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) return 1;
        if ("false".equalsIgnoreCase(text)) return 0;
        int result = decimal(value).intValueExact();
        if (result != 0 && result != 1) throw new IllegalArgumentException("布尔值必须为 0 或 1");
        return result;
    }

    private static LocalDateTime timestampValue(Object value) {
        if (value instanceof LocalDateTime instant) return instant;
        if (value instanceof java.sql.Timestamp instant) return instant.toLocalDateTime();
        if (value instanceof LocalDate day) return day.atStartOfDay();
        String text = String.valueOf(value).trim();
        return text.length() == 10 ? LocalDate.parse(text).atStartOfDay()
                : LocalDateTime.parse(text.replace(' ', 'T'));
    }
}
