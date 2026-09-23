package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.*;
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

    public StandardDatabaseInsertDialect(SchemaDdlDialect schema) {
        this.schema = Objects.requireNonNull(schema);
    }

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

    @Override
    public boolean isUniqueViolation(String sqlState, int vendorCode) {
        return DatabaseDialects.errors(schema.vendor()).classify(sqlState, vendorCode) == DatabaseErrorKind.UNIQUE;
    }
}
