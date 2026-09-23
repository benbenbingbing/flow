package com.workflow.integration.database.api;

import java.util.Objects;

/** 逻辑存储类型，不包含任何厂商 SQL；超出目标库能力的精度由方言明确拒绝。 */
public record SchemaType(Kind kind, Integer length, Integer precision, Integer scale) {
    public enum Kind { STRING, TEXT, LARGE_TEXT, INTEGER, LONG, DECIMAL, DATE, TIMESTAMP, BOOLEAN, BYTE }

    public SchemaType {
        Objects.requireNonNull(kind, "列类型不能为空");
        if (kind == Kind.STRING && (length == null || length < 1 || length > 4096)) {
            throw new IllegalArgumentException("字段长度必须在 1 到 4096 之间");
        }
        if (kind == Kind.DECIMAL && (precision == null || precision < 1 || precision > 65
                || scale == null || scale < 0 || scale > 30 || scale > precision)) {
            throw new IllegalArgumentException("DECIMAL 精度或小数位数不合法");
        }
    }

    public static SchemaType of(Kind kind) { return new SchemaType(kind, null, null, null); }
    public static SchemaType string(int length) { return new SchemaType(Kind.STRING, length, null, null); }
    public static SchemaType decimal(int precision, int scale) {
        return new SchemaType(Kind.DECIMAL, null, precision, scale);
    }
}
