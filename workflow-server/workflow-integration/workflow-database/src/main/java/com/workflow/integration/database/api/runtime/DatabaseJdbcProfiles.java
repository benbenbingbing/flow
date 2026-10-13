package com.workflow.integration.database.api.runtime;

import com.workflow.integration.database.api.DatabaseVendor;
import java.util.Locale;
import java.util.Properties;

/** JDBC 驱动及连接初始化集中定义；兼容模式方言和驱动 URL 是两个独立概念。 */
public final class DatabaseJdbcProfiles {
    /**
     * 初始化数据库JDBC{@code profiles}，保存构造参数供后续方法使用。
     */
    private DatabaseJdbcProfiles() {}

    /**
     * 未知兼容模式的连接只启用 OceanBase 注释读取，不根据相同的 JDBC URL 猜测租户模式。
     * 需要 Oracle 主键及 CLOB 协议的调用方必须使用带 vendor 的重载。
     */
    public static Properties connectionProperties(String url) {
        return connectionProperties(null, url);
    }

    /**
     * 按明确的产品和驱动设置连接属性，不改变其他方言的预编译及游标策略。
     *
     * @param vendor 已选择的数据库产品；null 表示旧入口未提供模式，只设置通用驱动属性
     * @param url JDBC URL，决定是否使用 OceanBase 驱动专用属性
     * @return 每次创建的属性集合；显式 URL 参数仍由驱动处理
     */
    public static Properties connectionProperties(DatabaseVendor vendor, String url) {
        var properties = new Properties();
        if (url != null && url.toLowerCase(Locale.ROOT).startsWith("jdbc:oceanbase:")) {
            properties.setProperty("remarksReporting", "true");
            if (vendor == DatabaseVendor.OCEANBASE_ORACLE) {
                // Oracle 的命名生成键需要服务端预编译，Reader/InputStream 还需要分片协议。
                // 驱动会同时开启 serverPrepStmts/cursorFetch，因此必须排除 OB MySQL 租户。
                properties.setProperty("useOraclePrepareExecute", "true");
                properties.setProperty("usePieceData", "true");
            }
        }
        return properties;
    }

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
