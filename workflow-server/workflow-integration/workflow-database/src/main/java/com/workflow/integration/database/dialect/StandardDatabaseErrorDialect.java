package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.error.DatabaseErrorDialect;
import com.workflow.integration.database.api.error.DatabaseErrorKind;
import com.workflow.integration.database.api.DatabaseVendor;
import java.util.Objects;
import static com.workflow.integration.database.api.error.DatabaseErrorKind.*;

/** 厂商错误码按实际产品隔离；OceanBase 同时接受兼容协议码和已确认的内部码。 */
public final class StandardDatabaseErrorDialect implements DatabaseErrorDialect {
    private final DatabaseVendor vendor;

    /**
     * 初始化标准数据库错误方言，保存构造参数供后续方法使用。
     *
     * @param vendor 供应商，保存在对象中供后续校验、查询或展示
     */
    public StandardDatabaseErrorDialect(DatabaseVendor vendor) { this.vendor = Objects.requireNonNull(vendor); }

    /**
     * 处理{@code classify}，并将结果传给后续步骤。
     *
     * @param sqlState 数据库 SQLState，后续用于识别唯一约束冲突
     * @param vendorCode 数据库供应商错误码，与 SQLState 一起判断唯一约束冲突
     * @return 处理后的{@code classify}结果，供调用方继续处理
     */
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

    /**
     * 处理{@code mysql}，并将结果传给后续步骤。
     *
     * @param code 编码，后续用于处理{@code mysql}时定位或关联目标
     * @return 处理后的{@code mysql}结果，供调用方继续处理
     */
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

    /**
     * 处理{@code oracle}，并将结果传给后续步骤。
     *
     * @param code 编码，后续用于处理{@code oracle}时定位或关联目标
     * @return 处理后的{@code oracle}结果，供调用方继续处理
     */
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

    /**
     * 处理状态，并将结果传给后续步骤。
     *
     * @param state 状态标识，决定后续状态采用的处理分支
     * @return 处理后的状态结果，供调用方继续处理
     */
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

    /**
     * 处理{@code dm}，并将结果传给后续步骤。
     *
     * @param code 编码，后续用于处理{@code dm}时定位或关联目标
     * @return 处理后的{@code dm}结果，供调用方继续处理
     */
    private static DatabaseErrorKind dm(int code) {
        // 只列已确认的产品码，未知完整性错误不借用 Oracle/MySQL 的数字或英文文本。
        return switch (code) {
            case -6602 -> UNIQUE;
            case -6607 -> FOREIGN_KEY;
            case -6169, -6109 -> VALUE_TOO_LONG;
            default -> UNKNOWN;
        };
    }

    /**
     * 处理{@code oceanbase}，并将结果传给后续步骤。
     *
     * @param code 编码，后续用于处理{@code oceanbase}时定位或关联目标
     * @param oracleMode {@code oracle}模式标识，决定后续{@code oceanbase}采用的处理分支
     * @return 处理后的{@code oceanbase}结果，供调用方继续处理
     */
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
