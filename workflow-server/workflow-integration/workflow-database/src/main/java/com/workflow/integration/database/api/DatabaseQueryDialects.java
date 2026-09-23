package com.workflow.integration.database.api;

import com.workflow.integration.database.api.DatabaseQueryDialect;
import java.util.EnumMap;
import java.util.Map;
import com.workflow.integration.database.query.StandardDatabaseQueryDialect;

/**
 * 无框架依赖的查询方言入口。每次按 SqlSessionFactory 的 databaseId 选择，
 * 不保存“当前数据库”全局变量，避免多个工厂或并行测试串用方言。
 */
public final class DatabaseQueryDialects {
    private DatabaseQueryDialects() {}

    /** databaseId 必须由应用装配配置为 DatabaseVendor.name()；缺失配置直接失败。 */
    public static DatabaseQueryDialect forDatabaseId(String databaseId) {
        if (databaseId == null || databaseId.isBlank()) {
            throw new IllegalStateException("MyBatis 未配置 databaseId，请装配应用的 DatabaseIdProvider");
        }
        return forVendor(DatabaseVendor.valueOf(databaseId));
    }

    /** 按产品缓存无状态方言；选择入口与实现同属 integration，无需跨模块 SPI。 */
    public static DatabaseQueryDialect forVendor(DatabaseVendor vendor) {
        return Holder.DIALECTS.get(java.util.Objects.requireNonNull(vendor, "vendor"));
    }

    private static class Holder {
        private static final Map<DatabaseVendor, DatabaseQueryDialect> DIALECTS = load();

        private static Map<DatabaseVendor, DatabaseQueryDialect> load() {
            var result = new EnumMap<DatabaseVendor, DatabaseQueryDialect>(DatabaseVendor.class);
            for (var vendor : DatabaseVendor.values()) {
                var dialect = java.util.Objects.requireNonNull(new StandardDatabaseQueryDialect(DatabaseDialects.forVendor(vendor)), "query dialect");
                if (dialect.vendor() != vendor) throw new IllegalStateException("查询方言产品不匹配: " + vendor);
                result.put(vendor, dialect);
            }
            return Map.copyOf(result);
        }
    }
}
