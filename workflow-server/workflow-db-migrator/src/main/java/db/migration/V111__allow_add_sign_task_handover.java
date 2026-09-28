package db.migration;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.runtime.DatabaseRuntimeDialect;
import com.workflow.integration.database.api.schema.SchemaDdlDialect;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 同一轮加签的不同待办允许交接给同一人；任务 ID 仍然唯一，不能把不同任务合并成一条人员记录。
 * 初始加签人员去重由创建流程保障，数据库只保留人员查询索引和 generated_task_id 唯一性。
 */
public class V111__allow_add_sign_task_handover extends BaseJavaMigration {
    private static final String TABLE = "process_task_add_sign_user";
    private static final String LOOKUP_INDEX = "idx_add_sign_user";
    private static final Set<String> HANDOVER_COLUMNS = Set.of("add_sign_id", "user_id");

    private record Scope(String catalog, String schema, String table) { }
    private record Index(String name, List<String> columns, boolean unique) {
        boolean isHandoverIdentity() {
            return columns.size() == 2 && Set.copyOf(columns).equals(HANDOVER_COLUMNS);
        }
    }

    /** Oracle、MySQL 等 DDL 隐式提交；每步按结构检测，故障后重跑可补回普通索引。 */
    @Override public boolean canExecuteInTransaction() { return false; }

    /**
     * 移除人员组合唯一约束/索引后恢复普通查询索引，始终保留任务 ID 唯一性。
     *
     * @param context 当前迁移连接；只修改固定加签人员表，不扫描其他 schema
     * @throws SQLException 任务唯一性缺失、索引名称冲突或数据库 DDL 失败
     */
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        DatabaseVendor vendor = resolveVendor(connection);
        SchemaDdlDialect dialect = DatabaseDialects.forVendor(vendor);
        Scope scope = scope(connection, vendor);
        List<Index> before = indexes(connection, scope);
        requireTaskIdentity(before);
        // 在任何 DDL 前核对新索引名称，避免删除旧约束后才发现租户复用了该名称。
        for (Index index : before) {
            if (LOOKUP_INDEX.equalsIgnoreCase(index.name()) && (!index.isHandoverIdentity() || index.unique())) {
                throw new SQLException("V111: 普通加签人员索引名称已被其他索引占用");
            }
        }

        // PostgreSQL/Oracle 家族可能用 UNIQUE 约束持有索引，必须先删约束；MySQL UNIQUE KEY 直接按索引删除。
        for (String constraint : handoverConstraints(connection, scope, vendor)) {
            execute(connection, "ALTER TABLE " + dialect.quoteIdentifier(TABLE) + " DROP CONSTRAINT "
                    + quoteExisting(dialect, constraint));
        }
        for (Index index : indexes(connection, scope)) {
            if (!index.unique() || !index.isHandoverIdentity()) continue;
            String drop = "DROP INDEX " + quoteExisting(dialect, index.name());
            if (vendor == DatabaseVendor.MYSQL || vendor == DatabaseVendor.OCEANBASE_MYSQL) {
                drop += " ON " + dialect.quoteIdentifier(TABLE);
            }
            execute(connection, drop);
        }

        // 先删除旧唯一索引再建普通索引，兼容不允许同列重复建索引的数据库；中断后可凭结构补建。
        List<Index> remaining = indexes(connection, scope);
        if (remaining.stream().noneMatch(index -> !index.unique() && index.isHandoverIdentity())) {
            execute(connection, "CREATE INDEX " + dialect.quoteIdentifier(LOOKUP_INDEX) + " ON "
                    + dialect.quoteIdentifier(TABLE) + " (" + dialect.quoteIdentifier("add_sign_id")
                    + ", " + dialect.quoteIdentifier("user_id") + ")");
        }
        List<Index> after = indexes(connection, scope);
        requireTaskIdentity(after);
        if (after.stream().anyMatch(index -> index.unique() && index.isHandoverIdentity())) {
            throw new SQLException("V111: 加签人员组合唯一性仍然存在，迁移未完成");
        }
    }

    /** 沿用运行时方言入口；OceanBase 必须通过同一环境变量明确租户模式。 */
    protected DatabaseVendor resolveVendor(Connection connection) throws SQLException {
        return DatabaseDialects.resolve(System.getenv("WORKFLOW_DATABASE_VENDOR"), connection.getMetaData().getURL());
    }

    /** JDBC 元数据返回真实索引名称，兼容跨库基线中的哈希缩短名称，不能硬编码旧 MySQL 索引名。 */
    private List<Index> indexes(Connection connection, Scope scope) throws SQLException {
        Map<String, TreeMap<Integer, String>> columns = new LinkedHashMap<>();
        Map<String, Boolean> unique = new LinkedHashMap<>();
        try (var rows = connection.getMetaData().getIndexInfo(scope.catalog(), scope.schema(), scope.table(), false, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                if (name == null) continue;
                String column = rows.getString("COLUMN_NAME");
                // 表达式列使用哨兵，禁止把部分匹配的复杂索引误判为简单人员唯一性。
                columns.computeIfAbsent(name, ignored -> new TreeMap<>()).put(rows.getInt("ORDINAL_POSITION"),
                        column == null ? "<expression>" : column.toLowerCase(Locale.ROOT));
                unique.put(name, !rows.getBoolean("NON_UNIQUE"));
            }
        }
        return columns.entrySet().stream().map(entry ->
                new Index(entry.getKey(), List.copyOf(entry.getValue().values()), unique.get(entry.getKey()))).toList();
    }

    /** 只删除恰好两列的人员 UNIQUE 约束，不触碰主键、任务唯一约束或其他业务组合约束。 */
    private List<String> handoverConstraints(Connection connection, Scope scope, DatabaseVendor vendor) throws SQLException {
        if (vendor == DatabaseVendor.MYSQL || vendor == DatabaseVendor.OCEANBASE_MYSQL) return List.of();
        String sql = switch (vendor) {
            case POSTGRESQL, KINGBASE -> """
                    SELECT c.constraint_name, k.column_name, k.ordinal_position
                    FROM information_schema.table_constraints c
                    JOIN information_schema.key_column_usage k
                      ON k.constraint_catalog = c.constraint_catalog AND k.constraint_schema = c.constraint_schema
                      AND k.constraint_name = c.constraint_name
                    WHERE c.table_schema = ? AND c.table_name = ? AND c.constraint_type = 'UNIQUE'
                    ORDER BY c.constraint_name, k.ordinal_position
                    """;
            case ORACLE, DM, OCEANBASE_ORACLE -> """
                    SELECT c.constraint_name, k.column_name, k.position
                    FROM all_constraints c JOIN all_cons_columns k
                      ON k.owner = c.owner AND k.constraint_name = c.constraint_name AND k.table_name = c.table_name
                    WHERE c.owner = ? AND c.table_name = ? AND c.constraint_type = 'U'
                    ORDER BY c.constraint_name, k.position
                    """;
            default -> throw new SQLException("V111: 不支持的约束目录方言");
        };
        Map<String, List<String>> columns = new LinkedHashMap<>();
        try (var query = connection.prepareStatement(sql)) {
            query.setString(1, scope.schema());
            query.setString(2, scope.table());
            try (var rows = query.executeQuery()) {
                while (rows.next()) columns.computeIfAbsent(rows.getString(1), ignored -> new ArrayList<>())
                        .add(rows.getString(2).toLowerCase(Locale.ROOT));
            }
        }
        return columns.entrySet().stream().filter(entry -> entry.getValue().size() == 2
                && Set.copyOf(entry.getValue()).equals(HANDOVER_COLUMNS)).map(Map.Entry::getKey).toList();
    }

    /** 任务 ID 唯一性是加签完成回调定位人员记录的基础；异常环境必须在改表前停止。 */
    private void requireTaskIdentity(List<Index> indexes) throws SQLException {
        if (indexes.stream().noneMatch(index -> index.unique() && index.columns().equals(List.of("generated_task_id")))) {
            throw new SQLException("V111: generated_task_id 唯一索引缺失，请先核实加签表结构");
        }
    }

    private Scope scope(Connection connection, DatabaseVendor vendor) throws SQLException {
        var runtime = DatabaseDialects.runtime(vendor);
        if (runtime.metadataScope() == DatabaseRuntimeDialect.MetadataScope.CATALOG_ONLY) {
            return new Scope(connection.getCatalog(), null, runtime.physicalName(TABLE));
        }
        String schema;
        try { schema = connection.getSchema(); }
        catch (SQLFeatureNotSupportedException ignored) { schema = null; }
        if (schema == null || schema.isBlank()) {
            if (runtime.metadataScope() == DatabaseRuntimeDialect.MetadataScope.CURRENT_SCHEMA_OR_USER) {
                schema = connection.getMetaData().getUserName();
            } else throw new SQLException("V111: 当前 schema 未知，禁止跨 schema 修改加签表");
        }
        return new Scope(connection.getCatalog(), schema, runtime.physicalName(TABLE));
    }

    /** 仅接受项目约定的普通标识符；真实混合大小写对象不能靠大小写折叠猜测，遇到时停止迁移。 */
    private String quoteExisting(SchemaDdlDialect dialect, String name) throws SQLException {
        String normalized = name.toLowerCase(Locale.ROOT);
        String expected = DatabaseDialects.runtime(dialect.vendor()).physicalName(normalized);
        if (!name.equals(expected)) throw new SQLException("V111: 不支持需要保留混合大小写的索引或约束名称: " + name);
        return dialect.quoteIdentifier(normalized);
    }

    private void execute(Connection connection, String ddl) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(ddl); }
    }
}
