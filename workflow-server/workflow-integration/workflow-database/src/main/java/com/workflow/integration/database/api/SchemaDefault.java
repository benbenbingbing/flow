package com.workflow.integration.database.api;

import java.math.BigDecimal;
import java.util.Objects;

/** 默认值只允许标量和受控时间表达式，禁止将用户输入当作 SQL 表达式拼接。 */
public record SchemaDefault(Kind kind, Object value) {
    public enum Kind { NULL, LITERAL, CURRENT_TIMESTAMP }
    public SchemaDefault {
        Objects.requireNonNull(kind, "默认值类型不能为空");
        if (kind == Kind.LITERAL && !(value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long || value instanceof BigDecimal)) {
            throw new IllegalArgumentException("默认值必须是文本、布尔值或数值");
        }
        if (value instanceof String text && (text.length() > 4096 || text.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("默认值过长或包含 NUL 字符");
        }
        if (kind != Kind.LITERAL && value != null) {
            throw new IllegalArgumentException("非字面量默认值不能携带文本");
        }
    }
    public static SchemaDefault none() { return new SchemaDefault(Kind.NULL, null); }
    public static SchemaDefault literal(Object value) { return new SchemaDefault(Kind.LITERAL, value); }
    public static SchemaDefault currentTimestamp() { return new SchemaDefault(Kind.CURRENT_TIMESTAMP, null); }
}
