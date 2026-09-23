package com.workflow.integration.database.api;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.SchemaDdlDialect;
import com.workflow.integration.database.dialect.*;
import java.util.Locale;

/** 方言选择的单一入口；不能识别的产品或兼容模式在启动时失败，禁止退回 MySQL。 */
public final class DatabaseDialects {
    private DatabaseDialects() {}

    /** 批量写入方言只描述单条 SQL；执行、绑定与事务边界由调用方负责。 */
    public static DatabaseMutationDialect mutations(DatabaseVendor vendor) {
        return new StandardDatabaseMutationDialect(DatabaseQueryDialects.forVendor(vendor));
    }

    /** MyBatis Provider 使用当前工厂的产品标识，缺失或未知配置直接失败。 */
    public static DatabaseMutationDialect mutationsForDatabaseId(String databaseId) {
        return mutations(DatabaseQueryDialects.forDatabaseId(databaseId).vendor());
    }

    /** 厂商错误码分类不解析异常文本；JDBC 异常遍历及应用异常翻译由外层负责。 */
    public static DatabaseErrorDialect errors(DatabaseVendor vendor) {
        return new StandardDatabaseErrorDialect(vendor);
    }

    /** 获取只描述 SQL/调用约定的运行时方言，不会建立数据库连接。 */
    public static DatabaseRuntimeDialect runtime(DatabaseVendor vendor) {
        return new StandardDatabaseRuntimeDialect(vendor);
    }

    /** 单行插入及错误分类同样只返回语法/规则；事务恢复由执行方负责。 */
    public static DatabaseInsertDialect insert(DatabaseVendor vendor) {
        return new StandardDatabaseInsertDialect(forVendor(vendor));
    }

    public static SchemaDdlDialect forVendor(DatabaseVendor vendor) {
        return switch (vendor) {
            case MYSQL -> new MySqlSchemaDdlDialect();
            case ORACLE -> new OracleSchemaDdlDialect();
            case POSTGRESQL -> new PostgresSchemaDdlDialect();
            case KINGBASE -> new KingbaseSchemaDdlDialect();
            case DM -> new DmSchemaDdlDialect();
            case OCEANBASE_MYSQL -> new OceanBaseMySqlSchemaDdlDialect();
            case OCEANBASE_ORACLE -> new OceanBaseOracleSchemaDdlDialect();
        };
    }

    /** OceanBase URL 不包含租户模式，必须配置 oceanbase-mysql 或 oceanbase-oracle。 */
    public static DatabaseVendor resolve(String configured, String jdbcUrl) {
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured)) {
            return switch (configured.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
                case "mysql" -> DatabaseVendor.MYSQL;
                case "oracle" -> DatabaseVendor.ORACLE;
                case "postgresql", "postgres", "pgsql" -> DatabaseVendor.POSTGRESQL;
                case "kingbase", "kingbasees" -> DatabaseVendor.KINGBASE;
                case "dm", "dameng" -> DatabaseVendor.DM;
                case "oceanbase-mysql", "ob-mysql" -> DatabaseVendor.OCEANBASE_MYSQL;
                case "oceanbase-oracle", "ob-oracle" -> DatabaseVendor.OCEANBASE_ORACLE;
                default -> throw new IllegalArgumentException("不支持的 workflow.database.vendor: " + configured);
            };
        }
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:mysql:")) return DatabaseVendor.MYSQL;
        if (url.startsWith("jdbc:oracle:")) return DatabaseVendor.ORACLE;
        if (url.startsWith("jdbc:postgresql:")) return DatabaseVendor.POSTGRESQL;
        if (url.startsWith("jdbc:kingbase8:")) return DatabaseVendor.KINGBASE;
        if (url.startsWith("jdbc:dm:")) return DatabaseVendor.DM;
        // H2 只用于已有的 MySQL 兼容测试，不作为对外支持的数据库产品。
        if (url.startsWith("jdbc:h2:") && url.contains("mode=mysql")) return DatabaseVendor.MYSQL;
        throw new IllegalArgumentException("无法自动确定数据库方言，请显式配置 workflow.database.vendor（OceanBase 需指定租户模式）");
    }
}
