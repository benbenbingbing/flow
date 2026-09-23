package com.workflow.integration.database.api.schema;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 默认值只允许标量和受控时间表达式，禁止将用户输入当作 SQL 表达式拼接。
 *
 * @param kind 类型，保存在对象中供后续校验、查询或展示
 * @param value 待处理结构默认的原始输入，结果供调用方继续使用
 */
public record SchemaDefault(Kind kind, Object value) {
    /**
     * 定义类型的可选值；调用方据此选择对应的处理分支。
     */
    public enum Kind { NULL, LITERAL, CURRENT_TIMESTAMP }
    /**
     * 初始化结构默认，保存构造参数供后续方法使用。
     *
     * @param kind 类型，保存在对象中供后续校验、查询或展示
     * @param value 待初始化结构默认的原始输入，结果供调用方继续使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
    /**
     * 处理{@code none}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code none}结果，供调用方继续处理
     */
    public static SchemaDefault none() { return new SchemaDefault(Kind.NULL, null); }
    /**
     * 处理字面值，并将结果传给后续步骤。
     *
     * @param value 待处理字面值的原始输入，结果供调用方继续使用
     * @return 处理后的字面值结果，供调用方继续处理
     */
    public static SchemaDefault literal(Object value) { return new SchemaDefault(Kind.LITERAL, value); }
    /**
     * 处理当前时间戳，并将结果传给后续步骤。
     *
     * @return 处理后的当前时间戳结果，供调用方继续处理
     */
    public static SchemaDefault currentTimestamp() { return new SchemaDefault(Kind.CURRENT_TIMESTAMP, null); }
}
