package com.workflow.integration.database.api;

import java.util.Objects;

/** 列的业务定义。refreshTimestampOnUpdate 表示数据库负责维护该审计时间列。 */
public record SchemaColumn(String name, SchemaType type, boolean nullable,
                           SchemaDefault defaultValue, String comment, boolean refreshTimestampOnUpdate) {
    public SchemaColumn {
        Objects.requireNonNull(name, "列名不能为空");
        Objects.requireNonNull(type, "列类型不能为空");
        Objects.requireNonNull(defaultValue, "默认值不能为空");
        if (refreshTimestampOnUpdate && type.kind() != SchemaType.Kind.TIMESTAMP) {
            throw new IllegalArgumentException("只有时间戳列可以自动刷新更新时间");
        }
    }
}
