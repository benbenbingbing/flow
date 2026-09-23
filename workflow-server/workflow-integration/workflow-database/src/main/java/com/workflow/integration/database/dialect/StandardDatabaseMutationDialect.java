package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** MySQL 使用原生有序限量 DML；其余产品在单条 DML 内选取有限主键集合。 */
public final class StandardDatabaseMutationDialect implements DatabaseMutationDialect {
    private final DatabaseQueryDialect query;

    public StandardDatabaseMutationDialect(DatabaseQueryDialect query) { this.query = java.util.Objects.requireNonNull(query); }
    @Override public DatabaseVendor vendor() { return query.vendor(); }

    @Override public String updateLimited(String table, String assignments, String predicate,
            List<DatabaseSort> orderBy, List<String> primaryKey, String limit) {
        requireFragment(assignments);
        return "UPDATE " + query.quoteIdentifier(table) + " SET " + assignments
                + restriction(table, predicate, orderBy, primaryKey, limit);
    }

    @Override public String deleteLimited(String table, String predicate, List<DatabaseSort> orderBy,
            List<String> primaryKey, String limit) {
        return "DELETE FROM " + query.quoteIdentifier(table)
                + restriction(table, predicate, orderBy, primaryKey, limit);
    }

    private String restriction(String table, String predicate, List<DatabaseSort> orderBy,
            List<String> primaryKey, String limit) {
        requireFragment(predicate);
        // 复用查询方言对数量占位符的校验，禁止匿名问号或调用方传入 SQL 表达式。
        query.paginationClause("0", limit);
        if (primaryKey == null || primaryKey.isEmpty() || orderBy == null || orderBy.isEmpty()) {
            throw new IllegalArgumentException("批量写入必须提供完整主键和稳定排序");
        }
        var orderedColumns = new HashSet<String>();
        var order = new ArrayList<String>();
        for (var item : orderBy) {
            if (!orderedColumns.add(item.column().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("排序列不能重复");
            order.add(query.quoteIdentifier(item.column()) + (item.descending() ? " DESC" : " ASC"));
        }
        var keys = new ArrayList<String>();
        var uniqueKeys = new HashSet<String>();
        for (String column : primaryKey) {
            String canonical = column.toUpperCase(Locale.ROOT);
            if (!uniqueKeys.add(canonical) || !orderedColumns.contains(canonical)) {
                throw new IllegalArgumentException("主键列不能重复且必须全部包含在排序中");
            }
            keys.add(query.quoteIdentifier(column));
        }
        String where = " WHERE (" + predicate + ")";
        String ordering = " ORDER BY " + String.join(", ", order);
        if (vendor() == DatabaseVendor.MYSQL || vendor() == DatabaseVendor.OCEANBASE_MYSQL) {
            // 保留 MySQL 原有锁定扫描行为，避免改成同表子查询引发 1093 或扩大竞争窗口。
            return where + ordering + " LIMIT " + limit;
        }
        String keyList = String.join(", ", keys);
        String keyExpression = keys.size() == 1 ? keyList : "(" + keyList + ")";
        String candidates = query.paginate("SELECT " + keyList + " FROM " + query.quoteIdentifier(table)
                + where + ordering, "0", limit);
        // 候选集可能来自等待锁之前的快照，必须在外层重新检查状态，不能覆盖已领取或续租的行。
        return where + " AND " + keyExpression + " IN (" + candidates + ")";
    }

    @Override public String primaryKeyUpdateHint() {
        return switch (vendor()) {
            case MYSQL, OCEANBASE_MYSQL -> " FORCE INDEX (PRIMARY)";
            default -> "";
        };
    }

    /** 本接口接收受信源码片段，不是 SQL 解析器；禁止多语句和无法重复绑定的匿名参数。 */
    private void requireFragment(String fragment) {
        if (fragment == null || fragment.isBlank() || fragment.contains("?") || fragment.contains(";")) {
            throw new IllegalArgumentException("批量写入必须提供固定单语句片段并使用命名绑定");
        }
    }
}
