package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** 将带时间轴的任职事实收敛为当前关系，退役历史、预约、主职及任命批次。 */
public class V130__simplify_position_membership extends BaseJavaMigration {
    private static final String TABLE = "sys_position_assignment";
    private static final String STAGING = "sys_position_member_stage";
    private static final java.util.Map<String, String> COMMENTS = java.util.Map.of(
            "id", "当前组织职务人员关系，移除时直接删除，不保存任职履历。",
            "position_id", "关联全局职务，供流程按稳定职务编码选人。",
            "organization_unit_id", "职务作用的组织或部门，区分不同部门的同名职务。",
            "user_id", "当前职务人员，供流程生成办理人。",
            "revision", "并发控制版本，不保存历史。",
            "created_by", "创建人。", "updated_by", "修改人。",
            "create_time", "当前关系创建时间，不参与生效判断。", "update_time", "更新时间，由应用维护。");
    private static final String COLUMNS = "id, position_id, organization_unit_id, user_id, revision, "
            + "created_by, updated_by, create_time, update_time";

    /** 部分数据库的 DDL 隐式提交，通过结构检测支持中断后继续，不能承诺整体回滚。 */
    @Override
    public boolean canExecuteInTransaction() { return false; }

    /**
     * 只复制切换时刻生效且未撤销的关系；未来预约不会被提前激活。
     * 先成功复制并检查约束，再删除旧表；正式表已完成切换时跳过复制。
     * 历史 Flyway 文件保持不可变，本迁移不执行 repair 或 baseline。
     */
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        if (hasColumn(connection, TABLE, "effective_from")) {
            if (!hasTable(connection, STAGING)) createCurrentTable(connection);
            execute(connection, "DELETE FROM " + STAGING);
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            Timestamp cutoff = Timestamp.from(Instant.now());
            String current = " revoked_at IS NULL AND effective_from <= ? "
                    + "AND (effective_to IS NULL OR effective_to > ?)";
            // 不猜测脏数据中的负责人；约束失败时保留旧表供排查，避免静默丢失当前人员。
            try (var check = connection.prepareStatement("SELECT a.organization_unit_id FROM " + TABLE
                    + " a JOIN sys_position p ON p.id = a.position_id WHERE p.holder_mode = 'SINGLE' AND "
                    + current + " GROUP BY a.position_id, a.organization_unit_id HAVING COUNT(*) > 1")) {
                check.setTimestamp(1, cutoff, utc);
                check.setTimestamp(2, cutoff, utc);
                try (var rows = check.executeQuery()) {
                    if (rows.next()) throw new SQLException("单人职务存在多名当前人员，必须先修正后再升级");
                }
            }
            try (var copy = connection.prepareStatement("INSERT INTO " + STAGING + " (" + COLUMNS
                    + ") SELECT " + COLUMNS + " FROM " + TABLE + " WHERE " + current)) {
                copy.setTimestamp(1, cutoff, utc);
                copy.setTimestamp(2, cutoff, utc);
                copy.executeUpdate();
            }
            execute(connection, "DROP TABLE " + TABLE);
        }
        // 若进程在 DROP 和 RENAME 之间中断，保留的 staging 表可继续完成切换。
        if (!hasTable(connection, TABLE) && hasTable(connection, STAGING)) {
            execute(connection, "ALTER TABLE " + STAGING + " RENAME TO " + TABLE);
        }
        if (!hasTable(connection, TABLE)) throw new SQLException("当前职务关系表缺失，迁移未完成");
        if (hasTable(connection, "sys_position_assignment_batch")) {
            execute(connection, "DROP TABLE sys_position_assignment_batch");
        }
        ensureIndex(connection, "idx_pos_member_user", "user_id");
        ensureIndex(connection, "idx_pos_member_unit", "organization_unit_id, position_id");
        if (!isMysql(connection)) {
            execute(connection, "COMMENT ON TABLE " + TABLE + " IS '当前组织职务人员关系'");
            for (var comment : COMMENTS.entrySet()) {
                execute(connection, "COMMENT ON COLUMN " + TABLE + "." + comment.getKey() + " IS '" + comment.getValue() + "'");
            }
        }
        refreshLeaders(connection);
    }

    /** 使用新约束名称，避免复制阶段与尚未删除的旧表发生数据库级名称冲突。 */
    private void createCurrentTable(Connection connection) throws SQLException {
        boolean mysql = isMysql(connection);
        String timestamp = mysql ? "DATETIME(6)" : "TIMESTAMP(6)";
        String tableOptions = "";
        if (mysql) {
            // 继承被引用 ID 的排序规则，避免存量库 unicode_ci 与新表 0900_ai_ci 的外键不兼容。
            try (var query = connection.createStatement();
                 var rows = query.executeQuery("SELECT CHARACTER_SET_NAME, COLLATION_NAME FROM information_schema.COLUMNS "
                         + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_position' AND COLUMN_NAME = 'id'")) {
                if (!rows.next()) throw new SQLException("无法读取职务 ID 的字符集");
                String charset = rows.getString(1), collation = rows.getString(2);
                if (!charset.matches("[A-Za-z0-9_]+") || !collation.matches("[A-Za-z0-9_]+")) {
                    throw new SQLException("非法的数据库字符集元数据");
                }
                tableOptions = " ENGINE=InnoDB DEFAULT CHARSET=" + charset + " COLLATE=" + collation;
            }
        }
        execute(connection, "CREATE TABLE " + STAGING + " ("
                + column(mysql, "id", "VARCHAR(64) NOT NULL PRIMARY KEY")
                + column(mysql, "position_id", "VARCHAR(64) NOT NULL")
                + column(mysql, "organization_unit_id", "VARCHAR(64) NOT NULL")
                + column(mysql, "user_id", "VARCHAR(64) NOT NULL")
                + column(mysql, "revision", "INTEGER DEFAULT 1 NOT NULL")
                + column(mysql, "created_by", "VARCHAR(64)")
                + column(mysql, "updated_by", "VARCHAR(64)")
                + column(mysql, "create_time", timestamp + " DEFAULT CURRENT_TIMESTAMP(6) NOT NULL")
                + column(mysql, "update_time", timestamp + " DEFAULT CURRENT_TIMESTAMP(6) NOT NULL")
                + "CONSTRAINT uk_pos_member_current UNIQUE (position_id, organization_unit_id, user_id), "
                + "CONSTRAINT fk_pos_member_position FOREIGN KEY (position_id) REFERENCES sys_position(id), "
                + "CONSTRAINT fk_pos_member_unit FOREIGN KEY (organization_unit_id) REFERENCES sys_organization(id), "
                + "CONSTRAINT fk_pos_member_user FOREIGN KEY (user_id) REFERENCES sys_user(id), "
                + "CONSTRAINT chk_pos_member_revision CHECK (revision >= 1))"
                + tableOptions + (mysql ? " COMMENT='当前组织职务人员关系'" : ""));

    }

    /** 仅使用迁移内固定标识符和注释；MySQL 字段注释随建表写入，其余方言用 COMMENT ON。 */
    private String column(boolean mysql, String name, String definition) {
        return name + " " + definition + (mysql ? " COMMENT '" + COMMENTS.get(name) + "'" : "") + ", ";
    }

    private boolean isMysql(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        String url = connection.getMetaData().getURL();
        if (product.contains("oceanbase") || url.toLowerCase(Locale.ROOT).startsWith("jdbc:oceanbase:")) {
            // OceanBase 共用产品名和驱动，必须沿用平台明确配置的租户模式，不能据产品名猜测。
            var vendor = com.workflow.integration.database.api.DatabaseDialects.resolve(
                    System.getenv("WORKFLOW_DATABASE_VENDOR"), url);
            return vendor == com.workflow.integration.database.api.DatabaseVendor.MYSQL
                    || vendor == com.workflow.integration.database.api.DatabaseVendor.OCEANBASE_MYSQL;
        }
        return product.contains("mysql") || product.contains("mariadb");
    }

    /** 负责人兼容字段反映当前设置；停用状态仍由运行时目录过滤，不再依赖定时任务。 */
    private void refreshLeaders(Connection connection) throws SQLException {
        execute(connection, "UPDATE sys_organization SET leader_id = NULL, leader_name = NULL");
        try (var query = connection.createStatement();
             var rows = query.executeQuery("SELECT a.organization_unit_id, u.id, u.nickname, u.username FROM "
                     + TABLE + " a JOIN sys_position p ON p.id = a.position_id "
                     + "JOIN sys_user u ON u.id = a.user_id WHERE p.position_code = 'UNIT_LEADER'");
             var update = connection.prepareStatement("UPDATE sys_organization SET leader_id = ?, leader_name = ? WHERE id = ?")) {
            while (rows.next()) {
                String nickname = rows.getString(3);
                update.setString(1, rows.getString(2));
                update.setString(2, nickname == null || nickname.isBlank() ? rows.getString(4) : nickname);
                update.setString(3, rows.getString(1));
                update.executeUpdate();
            }
        }
    }

    /** 元数据按当前 schema 查询，兼容未加引号标识符的大小写折叠。 */
    private boolean hasTable(Connection connection, String name) throws SQLException {
        try (var tables = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), null, new String[]{"TABLE"})) {
            while (tables.next()) if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
        }
        return false;
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        if (!hasTable(connection, table)) return false;
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            var metadata = rows.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                if (column.equalsIgnoreCase(metadata.getColumnName(i))) return true;
            }
            return false;
        }
    }

    /** 索引单独检测，在非事务 DDL 中断后也能补齐，而不重建已复制的关系。 */
    private void ensureIndex(Connection connection, String name, String columns) throws SQLException {
        String actual;
        try (var query = connection.createStatement();
             var rows = query.executeQuery("SELECT * FROM " + TABLE + " WHERE 1 = 0")) {
            actual = rows.getMetaData().getTableName(1);
        }
        try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), connection.getSchema(), actual, false, false)) {
            while (indexes.next()) if (name.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return;
        }
        execute(connection, "CREATE INDEX " + name + " ON " + TABLE + " (" + columns + ")");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
