package com.workflow.integration.database.api;

/** 业务表的标量存储约定；只做值转换，不依赖 JDBC、ORM 或数据库连接。 */
public final class DatabaseScalarValues {
    private DatabaseScalarValues() {}

    /**
     * 七种方言的业务布尔列统一存储为 0/1；null 保留为 SQL NULL，不等同于 false。
     * 对应 DDL 的 TINYINT、SMALLINT 或 NUMBER(1)，不适用于 Flowable 等第三方自有 BOOLEAN 列。
     * 仅转换作为独立列参数的 Boolean，不递归处理 JSON 文档或业务集合。
     */
    public static Integer numericBoolean(Boolean value) {
        return value == null ? null : (value ? 1 : 0);
    }

    /**
     * 为必须非 NULL 的空文本键选择存储值；结果必须作为参数绑定，不能当作 SQL 片段展开。
     * MySQL/OB MySQL/PostgreSQL 保留空串，兼容现有键；Oracle/OB Oracle 使用调用方保留的占位值。
     * Kingbase 和达梦也使用占位值，以兼容会把空串折叠成 NULL 的配置模式。
     * 调用方须保证占位值不会与真实业务键冲突，并在读取时兼容历史空串/NULL。
     */
    public static String nonNullEmptyText(String databaseId, String placeholder) {
        if (placeholder == null || placeholder.isBlank()) throw new IllegalArgumentException("空文本占位值必须非空");
        return switch (DatabaseQueryDialects.forDatabaseId(databaseId).vendor()) {
            case MYSQL, OCEANBASE_MYSQL, POSTGRESQL -> "";
            case ORACLE, OCEANBASE_ORACLE, KINGBASE, DM -> placeholder;
        };
    }
}
