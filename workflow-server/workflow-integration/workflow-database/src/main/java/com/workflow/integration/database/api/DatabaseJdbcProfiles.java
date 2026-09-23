package com.workflow.integration.database.api;

import com.workflow.integration.database.api.DatabaseVendor;
import java.util.Locale;

/** JDBC 驱动及连接初始化集中定义；兼容模式方言和驱动 URL 是两个独立概念。 */
public final class DatabaseJdbcProfiles {
    private DatabaseJdbcProfiles() {}

    public static String driver(String url, String configured) {
        if (configured != null && !configured.isBlank()) return configured;
        String normalized = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
        if (normalized.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (normalized.startsWith("jdbc:oracle:")) return "oracle.jdbc.OracleDriver";
        if (normalized.startsWith("jdbc:kingbase8:")) return "com.kingbase8.Driver";
        if (normalized.startsWith("jdbc:dm:")) return "dm.jdbc.driver.DmDriver";
        if (normalized.startsWith("jdbc:oceanbase:")) return "com.oceanbase.jdbc.Driver";
        if (normalized.startsWith("jdbc:h2:")) return "org.h2.Driver";
        throw new IllegalArgumentException("无法确定 JDBC 驱动，请配置 spring.datasource.driver-class-name");
    }

    public static String connectionInitSql(DatabaseVendor vendor) {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci";
            default -> null;
        };
    }

    /** 防止主库选择一个语法族，却把专用 DDL 连接配置成另一种数据库。 */
    public static void requireCompatibleUrl(DatabaseVendor vendor, String url) {
        String value = url == null ? "" : url.toLowerCase(Locale.ROOT);
        boolean compatible = switch (vendor) {
            case MYSQL -> value.startsWith("jdbc:mysql:") || (value.startsWith("jdbc:h2:") && value.contains("mode=mysql"));
            case POSTGRESQL -> value.startsWith("jdbc:postgresql:");
            case KINGBASE -> value.startsWith("jdbc:kingbase8:");
            case ORACLE -> value.startsWith("jdbc:oracle:");
            case DM -> value.startsWith("jdbc:dm:");
            case OCEANBASE_MYSQL -> value.startsWith("jdbc:mysql:") || value.startsWith("jdbc:oceanbase:");
            case OCEANBASE_ORACLE -> value.startsWith("jdbc:oracle:") || value.startsWith("jdbc:oceanbase:");
        };
        if (!compatible) throw new IllegalArgumentException("JDBC URL 与选定数据库方言不匹配: " + vendor);
    }

    /** Flowable 使用语法族名，不能直接使用 Kingbase、DM、OceanBase 的产品名称。 */
    public static String flowableType(DatabaseVendor vendor) {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "mysql";
            case POSTGRESQL, KINGBASE -> "postgres";
            case ORACLE, DM, OCEANBASE_ORACLE -> "oracle";
        };
    }
}
