package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;

/** 为乱序来源组织保存父级外部引用，并将已有组织视为父链已完整，兼容升级前的数据。 */
public class V129__allow_out_of_order_organization_provisioning extends BaseJavaMigration {

    /** DDL 在部分数据库隐式提交，每步检查结构以支持中断后的继续执行。 */
    @Override
    public boolean canExecuteInTransaction() { return false; }

    /**
     * 增加父级定位及父链状态，按实际父子关系回填旧来源映射。
     * 使用标准 JDBC 和基础 DDL，避免 UPDATE JOIN 对目标数据库语法的依赖。
     *
     * @param context Flyway 提供的当前数据库连接
     * @throws SQLException 结构变更或历史关系回填失败时中止迁移
     */
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        addColumn(connection, "sys_organization", "hierarchy_ready", "SMALLINT DEFAULT 1 NOT NULL");
        addColumn(connection, "sys_identity_source_binding", "parent_external_id", "VARCHAR(255) NULL");
        addColumn(connection, "sys_identity_source_binding", "parent_source_key", "VARCHAR(64) NULL");
        // 哈希引用保持来源和外部 ID 的精确大小写语义，并支持父节点到达后的定点子级查询。
        String table;
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM sys_identity_source_binding WHERE 1 = 0")) {
            table = rows.getMetaData().getTableName(1);
        }
        boolean indexed = false;
        try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), connection.getSchema(), table, false, false)) {
            while (indexes.next()) {
                if ("idx_identity_parent".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) indexed = true;
            }
        }
        if (!indexed) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE INDEX idx_identity_parent ON sys_identity_source_binding (parent_source_key)");
            }
        }

        // 只回填同来源的现有父级，保留根节点的 NULL，重复执行不会覆盖已登记的待关联引用。
        try (var query = connection.createStatement();
             var rows = query.executeQuery("""
                     SELECT child.source_key, parent.source_key, parent.external_id,
                            child.source_code, parent.source_code
                     FROM sys_identity_source_binding child
                     JOIN sys_organization org ON org.id = child.local_id
                     JOIN sys_identity_source_binding parent ON parent.local_id = org.parent_id
                     WHERE child.object_type = 'ORGANIZATION' AND parent.object_type = 'ORGANIZATION'
                     """);
             var update = connection.prepareStatement("""
                     UPDATE sys_identity_source_binding SET parent_external_id = ?, parent_source_key = ?
                     WHERE source_key = ? AND parent_external_id IS NULL AND parent_source_key IS NULL
                     """)) {
            while (rows.next()) {
                if (!rows.getString(4).equals(rows.getString(5))) continue;
                update.setString(1, rows.getString(3));
                update.setString(2, rows.getString(2));
                update.setString(3, rows.getString(1));
                update.executeUpdate();
            }
        }
    }

    /** 固定表名和列名来自迁移代码，查询当前表元数据后逐项补齐，避免重复执行已提交的 DDL。 */
    private void addColumn(Connection connection, String table, String column, String definition) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            var metadata = rows.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                if (column.equalsIgnoreCase(metadata.getColumnName(index))) return;
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD " + column + " " + definition);
        }
    }
}
