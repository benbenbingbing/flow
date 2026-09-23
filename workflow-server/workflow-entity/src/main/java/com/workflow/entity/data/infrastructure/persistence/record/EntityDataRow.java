package com.workflow.entity.data.infrastructure.persistence.record;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 动态业务表的 JDBC 行映射。发布的物理字段编码统一使用小写，但部分驱动返回大写列标签；
 * 仅在数据库读取边界归一化键，避免业务读取 id、审计字段和自定义字段时依赖驱动大小写。
 * 不用于用户请求 Map 或 JSON 文档，防止改变其业务键。
 */
public final class EntityDataRow extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    /** MyBatis 自动映射通过 Map.put 写入各列；值及 NULL 行为保持框架原有约定。 */
    @Override public Object put(String column, Object value) {
        return super.put(column == null ? null : column.toLowerCase(Locale.ROOT), value);
    }

    @Override public void putAll(Map<? extends String, ?> columns) {
        columns.forEach(this::put);
    }
}
