package com.workflow.integration.database.api.schema;

import java.util.Objects;

/**
 * 列的业务定义。refreshTimestampOnUpdate 表示数据库负责维护该审计时间列。
 *
 * @param name 展示名称，供界面或日志识别
 * @param type 类型标识，决定后续结构列采用的处理分支
 * @param nullable 可空，保存在对象中供后续校验、查询或展示
 * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
 * @param comment 注释，保存在对象中供后续校验、查询或展示
 * @param refreshTimestampOnUpdate 刷新时间戳更新，后续用于判断有效期或展示该事件的发生时间
 */
public record SchemaColumn(String name, SchemaType type, boolean nullable,
                           SchemaDefault defaultValue, String comment, boolean refreshTimestampOnUpdate) {
    /**
     * 初始化结构列，保存构造参数供后续方法使用。
     *
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续结构列采用的处理分支
     * @param nullable 可空，保存在对象中供后续校验、查询或展示
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @param comment 注释，保存在对象中供后续校验、查询或展示
     * @param refreshTimestampOnUpdate 刷新时间戳更新，后续用于判断有效期或展示该事件的发生时间
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public SchemaColumn {
        Objects.requireNonNull(name, "列名不能为空");
        Objects.requireNonNull(type, "列类型不能为空");
        Objects.requireNonNull(defaultValue, "默认值不能为空");
        if (refreshTimestampOnUpdate && type.kind() != SchemaType.Kind.TIMESTAMP) {
            throw new IllegalArgumentException("只有时间戳列可以自动刷新更新时间");
        }
    }
}
