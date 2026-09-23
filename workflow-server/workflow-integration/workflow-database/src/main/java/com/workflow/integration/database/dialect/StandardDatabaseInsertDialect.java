package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.error.DatabaseErrorKind;
import com.workflow.integration.database.api.schema.SchemaDdlDialect;
import com.workflow.integration.database.api.sql.BoundSqlStatement;
import com.workflow.integration.database.api.write.DatabaseInsertDialect;
import com.workflow.integration.database.api.write.DatabaseRowLockPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Objects;

/** 生成单行 INSERT 和事务锁行计划；封装厂商语法和错误码，不依赖 upsert 的影响行数约定。 */
public final class StandardDatabaseInsertDialect implements DatabaseInsertDialect {
    private final SchemaDdlDialect schema;

    /**
     * 初始化标准数据库{@code insert}方言，保存构造参数供后续方法使用。
     *
     * @param schema 结构，保存在对象中供后续校验、查询或展示
     */
    public StandardDatabaseInsertDialect(SchemaDdlDialect schema) {
        this.schema = Objects.requireNonNull(schema);
    }

    /**
     * 插入标准数据库{@code insert}方言；后续读取或执行将使用更新后的状态。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 插入后的标准数据库{@code insert}方言结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    public BoundSqlStatement insert(String table, Map<String, ?> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("单行插入必须提供列和值");
        var columns = new ArrayList<String>();
        var parameters = new ArrayList<Object>();
        var canonicalNames = new HashSet<String>();
        for (var entry : values.entrySet()) {
            columns.add(schema.quoteIdentifier(entry.getKey()));
            if (!canonicalNames.add(entry.getKey().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("单行插入不能重复指定同一物理列");
            }
            parameters.add(entry.getValue());
        }
        return new BoundSqlStatement("INSERT INTO " + schema.quoteIdentifier(table) + " ("
                + String.join(", ", columns) + ") VALUES ("
                + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")", parameters);
    }

    /**
     * 处理行锁定，并将结果传给后续步骤。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param initialValues 缺失行首次创建时的列值；已有行的业务值不会被覆盖
     * @param keyColumns 对应主键或唯一约束的列，后续用于锁定目标行
     * @return 处理后的行锁定结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    public DatabaseRowLockPlan rowLock(String table, Map<String, ?> initialValues, List<String> keyColumns) {
        var insert = insert(table, initialValues);
        if (keyColumns == null || keyColumns.isEmpty()) throw new IllegalArgumentException("事务锁行必须指定唯一键");
        var keys = new ArrayList<String>();
        var boundKeys = new ArrayList<Object>();
        var seen = new HashSet<String>();
        for (String key : keyColumns) {
            String quoted = schema.quoteIdentifier(key);
            if (!seen.add(key.toUpperCase(Locale.ROOT)) || !initialValues.containsKey(key) || initialValues.get(key) == null) {
                throw new IllegalArgumentException("事务锁行唯一键必须完整、非空且不重复");
            }
            keys.add(quoted);
            boundKeys.add(initialValues.get(key));
        }
        String quotedTable = schema.quoteIdentifier(table);
        String lock = "SELECT 1 FROM " + quotedTable + " WHERE "
                + String.join(" AND ", keys.stream().map(key -> key + " = ?").toList()) + " FOR UPDATE";
        String first = keys.get(0);
        String initialize = switch (schema.vendor()) {
            // no-op UPDATE 在重复键处直接取得写锁，避免普通重复 INSERT 的共享锁升级死锁。
            case MYSQL, OCEANBASE_MYSQL -> insert.sql() + " ON DUPLICATE KEY UPDATE " + first + " = " + first;
            case POSTGRESQL, KINGBASE -> insert.sql() + " ON CONFLICT (" + String.join(", ", keys)
                    + ") DO UPDATE SET " + first + " = " + quotedTable + "." + first;
            // MERGE 不更新匹配行，之后显式 FOR UPDATE；不能更新 ON 子句引用的主键列。
            case ORACLE, DM, OCEANBASE_ORACLE -> {
                var columns = initialValues.keySet().stream().map(schema::quoteIdentifier).toList();
                yield "MERGE INTO " + quotedTable + " t USING (SELECT "
                        + String.join(", ", columns.stream().map(column -> "? AS " + column).toList()) + " FROM DUAL) s ON ("
                        + String.join(" AND ", keys.stream().map(key -> "t." + key + " = s." + key).toList())
                        + ") WHEN NOT MATCHED THEN INSERT (" + String.join(", ", columns) + ") VALUES ("
                        + String.join(", ", columns.stream().map(column -> "s." + column).toList()) + ")";
            }
        };
        boolean recover = switch (schema.vendor()) {
            case ORACLE, DM, OCEANBASE_ORACLE -> true;
            default -> false;
        };
        return new DatabaseRowLockPlan(new BoundSqlStatement(initialize, insert.parameters()),
                new BoundSqlStatement(lock, boundKeys), recover);
    }

    /**
     * 判断是否唯一冲突；判断结果决定调用方的后续分支。
     *
     * @param sqlState 数据库 SQLState，后续用于识别唯一约束冲突
     * @param vendorCode 数据库供应商错误码，与 SQLState 一起判断唯一约束冲突
     * @return 唯一冲突条件成立时为 true，否则为 false
     */
    @Override
    public boolean isUniqueViolation(String sqlState, int vendorCode) {
        return DatabaseDialects.errors(schema.vendor()).classify(sqlState, vendorCode) == DatabaseErrorKind.UNIQUE;
    }
}
