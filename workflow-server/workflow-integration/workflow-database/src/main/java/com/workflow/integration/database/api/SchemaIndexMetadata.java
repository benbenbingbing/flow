package com.workflow.integration.database.api;

import java.util.List;

/** 索引列顺序影响唯一性和访问路径，重放校验不能仅比较索引名称。 */
public record SchemaIndexMetadata(String name, List<String> columns, boolean unique, boolean primaryKey) {
    public SchemaIndexMetadata { columns = List.copyOf(columns); }
}
