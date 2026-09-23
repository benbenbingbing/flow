package com.workflow.integration.database.api;

import java.util.List;
import java.util.HashSet;

/** 不可变的目标表结构，供发布预览和实际执行共享。 */
public record SchemaTable(String name, List<SchemaColumn> columns, List<String> primaryKey,
                          List<SchemaIndex> indexes, String comment, boolean ifNotExists) {
    public SchemaTable {
        columns = List.copyOf(columns);
        primaryKey = List.copyOf(primaryKey);
        indexes = List.copyOf(indexes);
        var names = new HashSet<String>();
        for (var column : columns) {
            if (!names.add(column.name())) throw new IllegalArgumentException("重复列: " + column.name());
        }
        if (columns.isEmpty() || !names.containsAll(primaryKey)) {
            throw new IllegalArgumentException("表缺少列或主键引用了未知列");
        }
        var indexNames = new HashSet<String>();
        for (var index : indexes) {
            if (!names.containsAll(index.columns()) || !indexNames.add(index.name())) {
                throw new IllegalArgumentException("索引引用未知列或重名: " + index.name());
            }
        }
    }
}
