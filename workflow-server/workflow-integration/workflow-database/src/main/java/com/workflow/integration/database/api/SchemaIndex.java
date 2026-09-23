package com.workflow.integration.database.api;

import java.util.List;

/** 索引只接受列名；表达式和排序片段不能从业务配置直接传入。 */
public record SchemaIndex(String name, List<String> columns, boolean unique) {
    public SchemaIndex {
        columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("索引至少需要一列");
    }
}
