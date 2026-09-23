package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.query.DatabaseSort;
import com.workflow.integration.database.api.write.DatabaseMutationDialect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** MySQL 使用原生有序限量 DML；其余产品在单条 DML 内选取有限主键集合。 */
public final class StandardDatabaseMutationDialect implements DatabaseMutationDialect {
    private final DatabaseQueryDialect query;

    /**
     * 初始化标准数据库变更方言，保存构造参数供后续方法使用。
     *
     * @param query 查询，保存在对象中供后续校验、查询或展示
     */
    public StandardDatabaseMutationDialect(DatabaseQueryDialect query) { this.query = java.util.Objects.requireNonNull(query); }
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return query.vendor(); }

    /**
     * 更新受限；后续读取或执行将使用更新后的状态。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param assignments 分配集合，作为 {@code requireFragment} 的输入影响后续处理
     * @param predicate 判断条件，供本方法更新受限时使用
     * @param orderBy 顺序，供本方法更新受限时使用
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 更新后的受限文本，供调用方比较或展示
     */
    @Override public String updateLimited(String table, String assignments, String predicate,
            List<DatabaseSort> orderBy, List<String> primaryKey, String limit) {
        requireFragment(assignments);
        return "UPDATE " + query.quoteIdentifier(table) + " SET " + assignments
                + restriction(table, predicate, orderBy, primaryKey, limit);
    }

    /**
     * 删除受限；后续读取或执行将使用更新后的状态。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param predicate 判断条件，供本方法删除受限时使用
     * @param orderBy 顺序，供本方法删除受限时使用
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的受限文本，供调用方比较或展示
     */
    @Override public String deleteLimited(String table, String predicate, List<DatabaseSort> orderBy,
            List<String> primaryKey, String limit) {
        return "DELETE FROM " + query.quoteIdentifier(table)
                + restriction(table, predicate, orderBy, primaryKey, limit);
    }

    /**
     * 生成{@code restriction}文本，供后续匹配或展示。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param predicate 判断条件，作为 {@code requireFragment} 的输入影响后续处理
     * @param orderBy 顺序，供本方法处理{@code restriction}时使用
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code restriction}文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 生成主要键更新{@code hint}文本，供后续匹配或展示。
     *
     * @return 处理后的主要键更新{@code hint}文本，供调用方比较或展示
     */
    @Override public String primaryKeyUpdateHint() {
        return switch (vendor()) {
            case MYSQL, OCEANBASE_MYSQL -> " FORCE INDEX (PRIMARY)";
            default -> "";
        };
    }

    /**
     * 本接口接收受信源码片段，不是 SQL 解析器；禁止多语句和无法重复绑定的匿名参数。
     *
     * @param fragment {@code fragment}，供本方法校验并获取{@code fragment}时使用
     */
    private void requireFragment(String fragment) {
        if (fragment == null || fragment.isBlank() || fragment.contains("?") || fragment.contains(";")) {
            throw new IllegalArgumentException("批量写入必须提供固定单语句片段并使用命名绑定");
        }
    }
}
