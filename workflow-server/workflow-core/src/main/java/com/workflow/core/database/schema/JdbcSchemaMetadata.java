package com.workflow.core.database.schema;

import com.workflow.integration.database.api.*;
import com.workflow.integration.database.api.SchemaDdlDialect;
import com.workflow.core.database.port.SchemaMetadataPort;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.*;
import java.util.*;

/**
 * 通过 JDBC 元数据读取跨数据库的表、列、主键与唯一索引；厂商查询语法从 integration 方言获取。
 * 目录/schema 和大小写规则跟随当前连接，禁止扫描其他用户的同名表。
 */
public final class JdbcSchemaMetadata implements SchemaMetadataPort {
    private final JdbcTemplate jdbc;
    private final SchemaDdlDialect dialect;
    private final DatabaseRuntimeDialect runtimeDialect;
    public JdbcSchemaMetadata(JdbcTemplate jdbc, SchemaDdlDialect dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
        this.runtimeDialect = DatabaseDialects.runtime(dialect.vendor());
    }

    @Override
    public boolean tableExists(String table) {
        dialect.quoteIdentifier(table);
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            var scope = scope(connection);
            var meta = connection.getMetaData();
            String physical = physicalName(table);
            try (var rows = meta.getTables(scope.catalog(), scope.schema(), pattern(meta, physical), new String[]{"TABLE"})) {
                while (rows.next()) if (physical.equals(rows.getString("TABLE_NAME"))) return true;
                return false;
            }
        }));
    }

    @Override
    public List<SchemaTableMetadata> tables() {
        return jdbc.execute((ConnectionCallback<List<SchemaTableMetadata>>) connection -> {
            var scope = scope(connection);
            var result = new ArrayList<SchemaTableMetadata>();
            try (var rows = connection.getMetaData().getTables(scope.catalog(), scope.schema(), "%", new String[]{"TABLE"})) {
                while (rows.next()) {
                    String name = rows.getString("TABLE_NAME");
                    // 跳过需区分大小写的外部表；应用不能可靠访问混合大小写的引号对象。
                    if (!name.equals(physicalName(name.toLowerCase(Locale.ROOT)))) continue;
                    result.add(new SchemaTableMetadata(name.toLowerCase(Locale.ROOT), rows.getString("REMARKS")));
                }
            }
            result.sort(Comparator.comparing(SchemaTableMetadata::name));
            return List.copyOf(result);
        });
    }

    @Override
    public List<SchemaColumnMetadata> columns(String table) {
        dialect.quoteIdentifier(table);
        return jdbc.execute((ConnectionCallback<List<SchemaColumnMetadata>>) connection -> {
            var scope = scope(connection);
            var meta = connection.getMetaData();
            String physical = physicalName(table);
            var primaryKeys = new HashSet<String>();
            try (var rows = meta.getPrimaryKeys(scope.catalog(), scope.schema(), physical)) {
                while (rows.next()) primaryKeys.add(rows.getString("COLUMN_NAME"));
            }
            var uniqueIndexes = new HashMap<String,List<String>>();
            try (var rows = meta.getIndexInfo(scope.catalog(), scope.schema(), physical, true, true)) {
                while (rows.next()) {
                    String column = rows.getString("COLUMN_NAME");
                    String index = rows.getString("INDEX_NAME");
                    if (index != null && column != null && !rows.getBoolean("NON_UNIQUE")) {
                        uniqueIndexes.computeIfAbsent(index, ignored -> new ArrayList<>()).add(column);
                    }
                }
            }
            // 联合唯一索引并不意味着每一列单独唯一，不能将其同步成业务字段的唯一约束。
            var singleUnique = new HashSet<String>();
            uniqueIndexes.values().stream().filter(names -> names.size() == 1).forEach(names -> singleUnique.add(names.get(0)));
            var result = new ArrayList<SchemaColumnMetadata>();
            try (var rows = meta.getColumns(scope.catalog(), scope.schema(), pattern(meta, physical), "%")) {
                while (rows.next()) {
                    if (!physical.equals(rows.getString("TABLE_NAME"))) continue;
                    String name = rows.getString("COLUMN_NAME");
                    int jdbcType = rows.getInt("DATA_TYPE");
                    Integer size = nullableInt(rows, "COLUMN_SIZE");
                    Integer scale = nullableInt(rows, "DECIMAL_DIGITS");
                    boolean character = Set.of(Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR,
                            Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB).contains(jdbcType);
                    result.add(new SchemaColumnMetadata(name.toLowerCase(Locale.ROOT), rows.getString("TYPE_NAME"), jdbcType,
                            character && size != null ? size.longValue() : null, character ? null : size, scale,
                            rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls, rows.getString("COLUMN_DEF"),
                            rows.getString("REMARKS"), primaryKeys.contains(name), singleUnique.contains(name), rows.getInt("ORDINAL_POSITION")));
                }
            }
            result.sort(Comparator.comparingInt(SchemaColumnMetadata::ordinal));
            return List.copyOf(result);
        });
    }

    @Override
    public List<SchemaIndexMetadata> indexes(String table) {
        dialect.quoteIdentifier(table);
        return jdbc.execute((ConnectionCallback<List<SchemaIndexMetadata>>) connection -> {
            var scope = scope(connection);
            var meta = connection.getMetaData();
            String physical = physicalName(table);
            var primaryNames = new HashSet<String>();
            try (var rows = meta.getPrimaryKeys(scope.catalog(), scope.schema(), physical)) {
                while (rows.next()) primaryNames.add(rows.getString("PK_NAME"));
            }
            var columns = new LinkedHashMap<String, SortedMap<Integer, String>>();
            var unique = new HashMap<String, Boolean>();
            try (var rows = meta.getIndexInfo(scope.catalog(), scope.schema(), physical, false, true)) {
                while (rows.next()) {
                    String name = rows.getString("INDEX_NAME");
                    if (name == null) continue;
                    String column = rows.getString("COLUMN_NAME");
                    // 表达式索引保留未知列哨兵，不能把仅一部分列匹配的索引误判为目标索引。
                    columns.computeIfAbsent(name, ignored -> new TreeMap<>()).put(rows.getInt("ORDINAL_POSITION"),
                            column == null ? "<expression>" : column.toLowerCase(Locale.ROOT));
                    unique.put(name, !rows.getBoolean("NON_UNIQUE"));
                }
            }
            return columns.entrySet().stream().map(entry -> new SchemaIndexMetadata(
                    entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(entry.getValue().values()),
                    unique.get(entry.getKey()), primaryNames.contains(entry.getKey()))).toList();
        });
    }

    @Override
    public long estimateRows(String table) {
        if (!tableExists(table)) return 0;
        String sql = runtimeDialect.estimatedRowsSql();
        List<Number> counts = jdbc.query(sql, (rows, index) -> (Number) rows.getObject(1), physicalName(table));
        if (!counts.isEmpty() && counts.get(0) != null && counts.get(0).longValue() >= 0) return counts.get(0).longValue();
        // 未收集统计的表不能按空表处理，否则会绕过大表发布风险评估。
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + dialect.quoteIdentifier(table), Long.class);
        return count == null ? 0 : count;
    }

    private String physicalName(String name) {
        return runtimeDialect.physicalName(name);
    }

    private Scope scope(Connection connection) throws SQLException {
        if (runtimeDialect.metadataScope() == DatabaseRuntimeDialect.MetadataScope.CATALOG_ONLY) {
            return new Scope(connection.getCatalog(), null);
        }
        String schema;
        try { schema = connection.getSchema(); }
        catch (SQLFeatureNotSupportedException exception) { schema = null; }
        if (schema == null || schema.isBlank()) {
            // Oracle/DM 的旧驱动可能没有 getSchema，默认 schema 就是当前连接用户。
            if (runtimeDialect.metadataScope() == DatabaseRuntimeDialect.MetadataScope.CURRENT_SCHEMA_OR_USER) {
                schema = connection.getMetaData().getUserName();
            } else {
                throw new SQLException("驱动未返回当前 schema，禁止跨 schema 读取实体结构");
            }
        }
        return new Scope(connection.getCatalog(), schema);
    }

    /** JDBC 的表名参数是 LIKE 模式；必须转义下划线，避免匹配到其他实体表。 */
    private String pattern(DatabaseMetaData meta, String name) throws SQLException {
        String escape = meta.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) return name; // 结果仍做精确表名匹配。
        return name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }
    private Integer nullableInt(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }
    private record Scope(String catalog, String schema) {}
}
