package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.runtime.DatabaseLockPlan;
import com.workflow.integration.database.api.runtime.DatabaseRuntimeDialect;
import com.workflow.integration.database.api.DatabaseVendor;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import static com.workflow.integration.database.api.runtime.DatabaseLockPlan.Invocation.*;

/** 七种产品复用语法族规则；厂商差异集中为纯数据，执行由调用方的基础设施完成。 */
public final class StandardDatabaseRuntimeDialect implements DatabaseRuntimeDialect {
    private final DatabaseVendor vendor;

    /**
     * 初始化标准数据库运行时方言，保存构造参数供后续方法使用。
     *
     * @param vendor 供应商，保存在对象中供后续校验、查询或展示
     */
    public StandardDatabaseRuntimeDialect(DatabaseVendor vendor) {
        this.vendor = Objects.requireNonNull(vendor);
    }

    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return vendor; }

    /**
     * 生成UTC时间戳表达式文本，供后续匹配或展示。
     *
     * @return 处理后的UTC时间戳表达式文本，供调用方比较或展示
     */
    @Override public String utcTimestampExpression() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "UTC_TIMESTAMP(6)";
            // PostgreSQL CURRENT_TIMESTAMP 固定为事务开始时间，不能用来判断长事务中的租约。
            case POSTGRESQL, KINGBASE -> "(statement_timestamp() AT TIME ZONE 'UTC')";
            case ORACLE, DM, OCEANBASE_ORACLE -> "SYS_EXTRACT_UTC(SYSTIMESTAMP)";
        };
    }

    /**
     * 生成UTC之后秒数文本，供后续匹配或展示。
     *
     * @param seconds 秒数，供本方法处理UTC之后秒数时使用
     * @return 处理后的UTC之后秒数文本，供调用方比较或展示
     */
    @Override public String utcAfterSeconds(String seconds) {
        return afterSeconds(utcTimestampExpression(), seconds);
    }

    /**
     * 生成当前时间戳表达式文本，供后续匹配或展示。
     *
     * @return 处理后的当前时间戳表达式文本，供调用方比较或展示
     */
    @Override public String currentTimestampExpression() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "CURRENT_TIMESTAMP";
            // 保留原本的会话墙钟语义，同时避免 PostgreSQL 长事务内时间停在 BEGIN 时刻。
            case POSTGRESQL, KINGBASE -> "(statement_timestamp() AT TIME ZONE current_setting('TimeZone'))";
            case ORACLE, DM, OCEANBASE_ORACLE -> "LOCALTIMESTAMP";
        };
    }

    /**
     * 生成当前之后秒数文本，供后续匹配或展示。
     *
     * @param seconds 秒数，供本方法处理当前之后秒数时使用
     * @return 处理后的当前之后秒数文本，供调用方比较或展示
     */
    @Override public String currentAfterSeconds(String seconds) {
        return afterSeconds(currentTimestampExpression(), seconds);
    }

    /**
     * 生成之后秒数文本，供后续匹配或展示。
     *
     * @param now 当前时间，作为 {@code TIMESTAMPADD} 的输入影响后续处理
     * @param seconds 秒数，作为 {@code TIMESTAMPADD} 的输入影响后续处理
     * @return 处理后的之后秒数文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String afterSeconds(String now, String seconds) {
        // 本表达式只使用一次 seconds，不重排参数，因此 JDBC 单问号也可安全绑定。
        if (seconds == null || !seconds.matches("(?:\\?|-?[0-9]+|:[A-Za-z_][A-Za-z0-9_]*|#\\{[A-Za-z_][A-Za-z0-9_.]*})")) {
            throw new IllegalArgumentException("时间偏移只接受固定整数或绑定参数");
        }
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "TIMESTAMPADD(SECOND, " + seconds + ", " + now + ")";
            case POSTGRESQL, KINGBASE -> "(" + now + " + (" + seconds + " * INTERVAL '1 second'))";
            case ORACLE, DM, OCEANBASE_ORACLE -> "(" + now + " + NUMTODSINTERVAL(" + seconds + ", 'SECOND'))";
        };
    }

    /**
     * 生成UTC当前时间SQL文本，供后续匹配或展示。
     *
     * @return 处理后的UTC当前时间SQL文本，供调用方比较或展示
     */
    @Override public String utcNowSql() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "SELECT UTC_TIMESTAMP(6)";
            case POSTGRESQL, KINGBASE -> "SELECT clock_timestamp() AT TIME ZONE 'UTC'";
            case ORACLE, DM, OCEANBASE_ORACLE -> "SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) FROM DUAL";
        };
    }

    /**
     * 生成{@code estimated}行SQL文本，供后续匹配或展示。
     *
     * @return 处理后的{@code estimated}行SQL文本，供调用方比较或展示
     */
    @Override public String estimatedRowsSql() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "SELECT TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?";
            case POSTGRESQL -> "SELECT c.reltuples FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema() AND c.relname=?";
            case KINGBASE -> "SELECT c.reltuples FROM sys_catalog.sys_class c JOIN sys_catalog.sys_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema() AND c.relname=?";
            case ORACLE, OCEANBASE_ORACLE, DM -> "SELECT NUM_ROWS FROM USER_TABLES WHERE TABLE_NAME=?";
        };
    }

    /**
     * 生成物理名称文本，供后续匹配或展示。
     *
     * @param name 名称，后续用于处理物理名称时匹配或展示
     * @return 处理后的物理名称文本，供调用方比较或展示
     */
    @Override public String physicalName(String name) {
        return switch (metadataScope()) {
            case CURRENT_SCHEMA_OR_USER -> name.toUpperCase(Locale.ROOT);
            default -> name;
        };
    }

    /**
     * 处理元数据作用域，并将结果传给后续步骤。
     *
     * @return 处理后的元数据作用域结果，供调用方继续处理
     */
    @Override public MetadataScope metadataScope() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> MetadataScope.CATALOG_ONLY;
            case ORACLE, DM, OCEANBASE_ORACLE -> MetadataScope.CURRENT_SCHEMA_OR_USER;
            case POSTGRESQL, KINGBASE -> MetadataScope.CURRENT_SCHEMA;
        };
    }

    /**
     * 锁定方案；避免后续并发处理覆盖状态。
     *
     * @param namespace 命名空间，作为 {@code digest.digest} 的输入影响后续处理
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 锁定后的方案结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override public DatabaseLockPlan lockPlan(String namespace, String key) {
        if (namespace == null || !namespace.matches("[a-z][a-z0-9:_-]{0,19}") || key == null || key.isBlank()) {
            throw new IllegalArgumentException("锁命名空间或业务键不合法");
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            // 保持原 MySQL SQL SHA2 的键，滚动升级期间新旧发布器仍争用同一把锁。
            String name = namespace + ":" + HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8))).substring(0, 40);
            byte[] hash = digest.digest((namespace + ":" + key).getBytes(StandardCharsets.UTF_8));
            long longId = ByteBuffer.wrap(hash).getLong();
            int integerId = ByteBuffer.wrap(hash).getInt() & 0x3fffffff;
            return switch (vendor) {
                case MYSQL, OCEANBASE_MYSQL -> new DatabaseLockPlan("SELECT GET_LOCK(?, 0)",
                        "SELECT RELEASE_LOCK(?)", name, TEXT_QUERY, 1, 0, 1, false);
                case POSTGRESQL -> new DatabaseLockPlan("SELECT pg_try_advisory_lock(?)",
                        "SELECT pg_advisory_unlock(?)", longId, BOOLEAN_QUERY, 1, 0, 1, false);
                case KINGBASE -> new DatabaseLockPlan("SELECT sys_try_advisory_lock(?)",
                        "SELECT sys_advisory_unlock(?)", longId, BOOLEAN_QUERY, 1, 0, 1, false);
                case ORACLE, DM -> new DatabaseLockPlan("BEGIN ? := DBMS_LOCK.REQUEST(?, 6, 0, FALSE); END;",
                        "BEGIN ? := DBMS_LOCK.RELEASE(?); END;", integerId, INTEGER_CALL, 0, 1, 0, false);
                // 该产品需要独立持锁事务，执行器不能把它混入业务连接或 DDL 连接。
                case OCEANBASE_ORACLE -> new DatabaseLockPlan("BEGIN ? := DBMS_LOCK.REQUEST(?, 6, 0, TRUE); END;",
                        null, integerId, INTEGER_CALL, 0, 1, 0, true);
            };
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
