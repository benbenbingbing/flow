package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.DatabaseLockPlan;
import com.workflow.integration.database.api.DatabaseRuntimeDialect;
import com.workflow.integration.database.api.DatabaseVendor;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import static com.workflow.integration.database.api.DatabaseLockPlan.Invocation.*;

/** 七种产品复用语法族规则；厂商差异集中为纯数据，执行由调用方的基础设施完成。 */
public final class StandardDatabaseRuntimeDialect implements DatabaseRuntimeDialect {
    private final DatabaseVendor vendor;

    public StandardDatabaseRuntimeDialect(DatabaseVendor vendor) {
        this.vendor = Objects.requireNonNull(vendor);
    }

    @Override public DatabaseVendor vendor() { return vendor; }

    @Override public String utcTimestampExpression() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "UTC_TIMESTAMP(6)";
            // PostgreSQL CURRENT_TIMESTAMP 固定为事务开始时间，不能用来判断长事务中的租约。
            case POSTGRESQL, KINGBASE -> "(statement_timestamp() AT TIME ZONE 'UTC')";
            case ORACLE, DM, OCEANBASE_ORACLE -> "SYS_EXTRACT_UTC(SYSTIMESTAMP)";
        };
    }

    @Override public String utcAfterSeconds(String seconds) {
        return afterSeconds(utcTimestampExpression(), seconds);
    }

    @Override public String currentTimestampExpression() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "CURRENT_TIMESTAMP";
            // 保留原本的会话墙钟语义，同时避免 PostgreSQL 长事务内时间停在 BEGIN 时刻。
            case POSTGRESQL, KINGBASE -> "(statement_timestamp() AT TIME ZONE current_setting('TimeZone'))";
            case ORACLE, DM, OCEANBASE_ORACLE -> "LOCALTIMESTAMP";
        };
    }

    @Override public String currentAfterSeconds(String seconds) {
        return afterSeconds(currentTimestampExpression(), seconds);
    }

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

    @Override public String utcNowSql() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "SELECT UTC_TIMESTAMP(6)";
            case POSTGRESQL, KINGBASE -> "SELECT clock_timestamp() AT TIME ZONE 'UTC'";
            case ORACLE, DM, OCEANBASE_ORACLE -> "SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) FROM DUAL";
        };
    }

    @Override public String estimatedRowsSql() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> "SELECT TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?";
            case POSTGRESQL -> "SELECT c.reltuples FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema() AND c.relname=?";
            case KINGBASE -> "SELECT c.reltuples FROM sys_catalog.sys_class c JOIN sys_catalog.sys_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema() AND c.relname=?";
            case ORACLE, OCEANBASE_ORACLE, DM -> "SELECT NUM_ROWS FROM USER_TABLES WHERE TABLE_NAME=?";
        };
    }

    @Override public String physicalName(String name) {
        return switch (metadataScope()) {
            case CURRENT_SCHEMA_OR_USER -> name.toUpperCase(Locale.ROOT);
            default -> name;
        };
    }

    @Override public MetadataScope metadataScope() {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> MetadataScope.CATALOG_ONLY;
            case ORACLE, DM, OCEANBASE_ORACLE -> MetadataScope.CURRENT_SCHEMA_OR_USER;
            case POSTGRESQL, KINGBASE -> MetadataScope.CURRENT_SCHEMA;
        };
    }

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
