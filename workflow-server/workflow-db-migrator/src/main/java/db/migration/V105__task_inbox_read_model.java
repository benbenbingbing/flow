package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 复用空置的候选表，并增加工作台列表摘要。历史候选关联指向的旧任务实例表已被移除，
 * 因此任何未核实的旧关系都必须在 DDL 前拦截，不能把字符串 ID 猜测为本地任务主键。
 * 与现有业务迁移一致在 MySQL 上执行；每步检查元数据，以便排除故障后继续未完成的 DDL。
 */
public class V105__task_inbox_read_model extends BaseJavaMigration {
    private static final List<String> CANDIDATE_TABLES = List.of(
            "process_task_candidate_user", "process_task_candidate_group");

    @Override
    public boolean canExecuteInTransaction() { return false; }

    /** 校验全部旧表后再变更结构；任何未知旧关联均保留原数据并使迁移失败。 */
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        for (String table : CANDIDATE_TABLES) {
            if (columnExists(connection, table, "task_instance_id")) {
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rows.next();
                    if (rows.getLong(1) != 0) {
                        throw new SQLException("V105: " + table
                                + " 存在旧候选关系，须先核验 task_instance_id 的旧任务归属；禁止直接强转或清空");
                    }
                }
            }
        }
        for (String table : CANDIDATE_TABLES) {
            if (columnExists(connection, table, "task_instance_id")) {
                execute(connection, "ALTER TABLE " + table
                        + " CHANGE COLUMN task_instance_id process_task_id BIGINT NOT NULL COMMENT '关联 process_task.id'");
            }
            String identityColumn = table.endsWith("_user") ? "user_id" : "group_code";
            addIndex(connection, table, table.endsWith("_user") ? "idx_task_candidate_user_lookup" : "idx_task_candidate_group_lookup",
                    identityColumn + ",process_task_id");
        }
        // 混合候选的展示名称可能超过旧 varchar(64)，不能因同步最终候选而截断或使审批失败。
        try (var query = connection.prepareStatement("SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='process_task' AND column_name='assignee_name'");
             var rows = query.executeQuery()) {
            if (rows.next() && !"longtext".equalsIgnoreCase(rows.getString(1))) {
                execute(connection, "ALTER TABLE process_task MODIFY COLUMN assignee_name LONGTEXT NULL COMMENT '实际办理人或候选身份展示名称'");
            }
        }
        addColumn(connection, "start_user_id", "VARCHAR(100) NULL COMMENT '发起人身份，兼容用户ID和用户名'");
        // 摘要不建立字符串前缀索引；大文本避免业务名称/自定义映射被静默截断。
        for (String column : List.of("business_name", "business_code", "business_data_name",
                "business_current_task_name", "business_status")) {
            addColumn(connection, column, "LONGTEXT NULL COMMENT '任务列表业务摘要，与业务当前值同步'");
        }
        addColumn(connection, "inbox_summary_ready", "TINYINT NOT NULL DEFAULT 0 COMMENT '摘要已回填；0时保留旧读取语义'");
        addColumn(connection, "inbox_identity_ready", "TINYINT NOT NULL DEFAULT 0 COMMENT '候选身份已从引擎最终状态同步'");
        addIndex(connection, "process_task", "idx_task_done_page", "assignee_id,status,deleted,end_time,id");
        addIndex(connection, "process_task", "idx_task_todo_page", "status,deleted,create_time,id");
        addIndex(connection, "process_task", "idx_task_business_summary", "entity_code,entity_data_id,id");
    }

    private void addColumn(Connection connection, String column, String definition) throws SQLException {
        if (!columnExists(connection, "process_task", column)) {
            execute(connection, "ALTER TABLE process_task ADD COLUMN " + column + " " + definition);
        }
    }

    private void addIndex(Connection connection, String table, String index, String columns) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name=? AND index_name=?")) {
            query.setString(1, table);
            query.setString(2, index);
            try (var rows = query.executeQuery()) { if (rows.next()) return; }
        }
        execute(connection, "CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?")) {
            query.setString(1, table);
            query.setString(2, column);
            try (var rows = query.executeQuery()) { return rows.next(); }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
