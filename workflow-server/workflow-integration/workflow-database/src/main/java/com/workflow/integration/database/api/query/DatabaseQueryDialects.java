package com.workflow.integration.database.api.query;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;

import java.util.EnumMap;
import java.util.Map;
import com.workflow.integration.database.dialect.StandardDatabaseQueryDialect;

/**
 * 无框架依赖的查询方言入口。每次按 SqlSessionFactory 的 databaseId 选择，
 * 不保存“当前数据库”全局变量，避免多个工厂或并行测试串用方言。
 */
public final class DatabaseQueryDialects {
    /**
     * 初始化数据库查询{@code dialects}，保存构造参数供后续方法使用。
     */
    private DatabaseQueryDialects() {}

    /**
     * databaseId 必须由应用装配配置为 DatabaseVendor.name()；缺失配置直接失败。
     *
     * @param databaseId 数据库ID，后续用于处理数据库ID时定位或关联目标
     * @return 处理后的数据库ID结果，供调用方继续处理
     */
    public static DatabaseQueryDialect forDatabaseId(String databaseId) {
        if (databaseId == null || databaseId.isBlank()) {
            throw new IllegalStateException("MyBatis 未配置 databaseId，请装配应用的 DatabaseIdProvider");
        }
        return forVendor(DatabaseVendor.valueOf(databaseId));
    }

    /**
     * 按产品缓存无状态方言；选择入口与实现同属 integration，无需跨模块 SPI。
     *
     * @param vendor 供应商，作为 {@code Holder.DIALECTS.get} 的输入影响后续处理
     * @return 处理后的供应商结果，供调用方继续处理
     */
    public static DatabaseQueryDialect forVendor(DatabaseVendor vendor) {
        return Holder.DIALECTS.get(java.util.Objects.requireNonNull(vendor, "vendor"));
    }

    /**
     * 封装持有者相关能力和状态；供同一业务流程的后续处理使用。
     */
    private static class Holder {
        private static final Map<DatabaseVendor, DatabaseQueryDialect> DIALECTS = load();

        /**
         * 加载{@code map<database}{@code vendor,}数据库查询{@code dialect>}；结果供调用方展示或继续处理。
         *
         * @return 持有者键值结果，供调用方继续处理
         * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
         */
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
