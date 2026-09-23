package com.workflow.integration.database.api.runtime;

import com.workflow.integration.database.api.DatabaseVendor;
import java.util.Locale;

/** JDBC 驱动及连接初始化集中定义；兼容模式方言和驱动 URL 是两个独立概念。 */
public final class DatabaseJdbcProfiles {
    /**
     * 初始化数据库JDBC{@code profiles}，保存构造参数供后续方法使用。
     */
    private DatabaseJdbcProfiles() {}

    /**
     * 生成{@code driver}文本，供后续匹配或展示。
     *
     * @param url URL，供本方法处理{@code driver}时使用
     * @param configured 已配置，供本方法处理{@code driver}时使用
     * @return 处理后的{@code driver}文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 生成连接{@code init}SQL文本，供后续匹配或展示。
     *
     * @param vendor 供应商，供本方法处理连接{@code init}SQL时使用
     * @return 处理后的连接{@code init}SQL文本，供调用方比较或展示
     */
    public static String connectionInitSql(DatabaseVendor vendor) {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci";
            default -> null;
        };
    }

    /**
     * 防止主库选择一个语法族，却把专用 DDL 连接配置成另一种数据库。
     *
     * @param vendor 供应商，供本方法校验并获取兼容URL时使用
     * @param url URL，供本方法校验并获取兼容URL时使用
     */
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

    /**
     * Flowable 使用语法族名，不能直接使用 Kingbase、DM、OceanBase 的产品名称。
     *
     * @param vendor 供应商，供本方法处理Flowable类型时使用
     * @return 处理后的Flowable类型文本，供调用方比较或展示
     */
    public static String flowableType(DatabaseVendor vendor) {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "mysql";
            case POSTGRESQL, KINGBASE -> "postgres";
            case ORACLE, DM, OCEANBASE_ORACLE -> "oracle";
        };
    }
}
