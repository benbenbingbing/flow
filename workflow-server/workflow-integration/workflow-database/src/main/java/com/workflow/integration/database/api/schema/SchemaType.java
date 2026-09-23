package com.workflow.integration.database.api.schema;

import java.util.Objects;

/**
 * 逻辑存储类型，不包含任何厂商 SQL；超出目标库能力的精度由方言明确拒绝。
 *
 * @param kind 类型，保存在对象中供后续校验、查询或展示
 * @param length 长度，保存在对象中供后续校验、查询或展示
 * @param precision {@code precision}，保存在对象中供后续校验、查询或展示
 * @param scale {@code scale}，保存在对象中供后续校验、查询或展示
 */
public record SchemaType(Kind kind, Integer length, Integer precision, Integer scale) {
    /**
     * 定义类型的可选值；调用方据此选择对应的处理分支。
     */
    public enum Kind { STRING, TEXT, LARGE_TEXT, INTEGER, LONG, DECIMAL, DATE, TIMESTAMP, BOOLEAN, BYTE }

    /**
     * 初始化结构类型，保存构造参数供后续方法使用。
     *
     * @param kind 类型，保存在对象中供后续校验、查询或展示
     * @param length 长度，保存在对象中供后续校验、查询或展示
     * @param precision {@code precision}，保存在对象中供后续校验、查询或展示
     * @param scale {@code scale}，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理of，并将结果传给后续步骤。
     *
     * @param kind 类型，作为 {@code SchemaType} 的输入影响后续处理
     * @return 处理后的of结果，供调用方继续处理
     */
    public static SchemaType of(Kind kind) { return new SchemaType(kind, null, null, null); }
    /**
     * 处理字符串，并将结果传给后续步骤。
     *
     * @param length 长度，作为 {@code SchemaType} 的输入影响后续处理
     * @return 处理后的字符串结果，供调用方继续处理
     */
    public static SchemaType string(int length) { return new SchemaType(Kind.STRING, length, null, null); }
    /**
     * 处理{@code decimal}，并将结果传给后续步骤。
     *
     * @param precision {@code precision}，作为 {@code SchemaType} 的输入影响后续处理
     * @param scale {@code scale}，作为 {@code SchemaType} 的输入影响后续处理
     * @return 处理后的{@code decimal}结果，供调用方继续处理
     */
    public static SchemaType decimal(int precision, int scale) {
        return new SchemaType(Kind.DECIMAL, null, precision, scale);
    }
}
