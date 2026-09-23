package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.DatabaseErrorDialect;
import com.workflow.integration.database.api.DatabaseErrorKind;
import com.workflow.integration.database.api.DatabaseVendor;
import java.util.Objects;
import static com.workflow.integration.database.api.DatabaseErrorKind.*;

/** 厂商错误码按实际产品隔离；OceanBase 同时接受兼容协议码和已确认的内部码。 */
public final class StandardDatabaseErrorDialect implements DatabaseErrorDialect {
    private final DatabaseVendor vendor;

    public StandardDatabaseErrorDialect(DatabaseVendor vendor) { this.vendor = Objects.requireNonNull(vendor); }

    @Override
    public DatabaseErrorKind classify(String sqlState, int vendorCode) {
        // 连接或事务中止状态优先，不能把不一致的驱动错误信息当成可恢复的唯一冲突。
        if (sqlState != null && sqlState.startsWith("08")) return CONNECTION;
        DatabaseErrorKind specific = switch (vendor) {
            case MYSQL -> mysql(vendorCode);
            case ORACLE -> oracle(vendorCode);
            case POSTGRESQL, KINGBASE -> state(sqlState);
            case DM -> dm(vendorCode);
            case OCEANBASE_MYSQL -> oceanbase(vendorCode, false);
            case OCEANBASE_ORACLE -> oceanbase(vendorCode, true);
        };
        if (sqlState != null && sqlState.startsWith("40")) {
            // MySQL Connector/J 也会把 1205 锁等待超时映射为 40001，保留已确认的具体类型。
            return specific == DEADLOCK || specific == LOCK_TIMEOUT ? specific : TRANSACTION_ROLLBACK;
        }
        return specific;
    }

    private static DatabaseErrorKind mysql(int code) {
        return switch (code) {
            case 1062 -> UNIQUE;
            case 1048 -> NOT_NULL;
            case 1364 -> MISSING_DEFAULT;
            case 1406 -> VALUE_TOO_LONG;
            case 1451, 1452, 1216, 1217 -> FOREIGN_KEY;
            case 3819 -> CHECK;
            case 1264 -> NUMERIC_RANGE;
            case 1213 -> DEADLOCK;
            case 1205, 3572 -> LOCK_TIMEOUT;
            default -> UNKNOWN;
        };
    }

    private static DatabaseErrorKind oracle(int code) {
        return switch (code) {
            case 1 -> UNIQUE;
            case 1400, 1407 -> NOT_NULL;
            case 12899, 1401 -> VALUE_TOO_LONG;
            case 2291, 2292 -> FOREIGN_KEY;
            case 2290 -> CHECK;
            case 1438, 1426 -> NUMERIC_RANGE;
            case 60 -> DEADLOCK;
            case 54, 2049, 30006 -> LOCK_TIMEOUT;
            case 8177, 24761 -> TRANSACTION_ROLLBACK;
            default -> UNKNOWN;
        };
    }

    private static DatabaseErrorKind state(String state) {
        if (state == null) return UNKNOWN;
        return switch (state) {
            case "23505" -> UNIQUE;
            case "23502" -> NOT_NULL;
            case "23503" -> FOREIGN_KEY;
            case "23514" -> CHECK;
            case "22001" -> VALUE_TOO_LONG;
            case "22003" -> NUMERIC_RANGE;
            case "40P01" -> DEADLOCK;
            case "55P03" -> LOCK_TIMEOUT;
            default -> UNKNOWN;
        };
    }

    private static DatabaseErrorKind dm(int code) {
        // 只列已确认的产品码，未知完整性错误不借用 Oracle/MySQL 的数字或英文文本。
        return switch (code) {
            case -6602 -> UNIQUE;
            case -6607 -> FOREIGN_KEY;
            case -6169, -6109 -> VALUE_TOO_LONG;
            default -> UNKNOWN;
        };
    }

    private static DatabaseErrorKind oceanbase(int code, boolean oracleMode) {
        DatabaseErrorKind compatible = oracleMode ? oracle(code) : mysql(code);
        if (compatible != UNKNOWN) return compatible;
        // 官方内部码的符号用于服务端实现，客户端错误包中使用正值。
        return switch (code) {
            case 5024 -> UNIQUE;
            case 5334, 5562, 5595 -> oracleMode ? UNIQUE : UNKNOWN;
            case 4235 -> NOT_NULL;
            case 4227 -> MISSING_DEFAULT;
            case 5167 -> VALUE_TOO_LONG;
            case 5314, 5315 -> FOREIGN_KEY;
            case 5693 -> CHECK;
            case 4101 -> DEADLOCK;
            case 6003, 6004, 6006 -> LOCK_TIMEOUT;
            case 6002 -> TRANSACTION_ROLLBACK;
            default -> UNKNOWN;
        };
    }
}
