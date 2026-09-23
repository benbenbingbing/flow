package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 为动态实体增加独立流程生命周期。只修改登记的业务表，不改写历史发布快照和业务 status。
 * MySQL DDL 非事务性，逐步检查列是否存在，允许人工排除故障后安全重跑未完成步骤。
 */
public class V102__entity_process_lifecycle extends BaseJavaMigration {
    /**
     * 判断能否{@code execute}事务；判断结果决定调用方的后续分支。
     *
     * @return {@code execute}事务条件成立时为 true，否则为 false
     */
    @Override public boolean canExecuteInTransaction() { return false; }

    /**
     * 处理迁移，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续迁移步骤传递身份、配置或状态
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        // 尚未建物理表的草稿实体也必须检查同名字段，避免后续注册系统字段时冲突。
        try (var query = connection.prepareStatement("SELECT e.id FROM entity_field f JOIN entity_definition e ON e.id=f.entity_id "
                + "WHERE e.storage_mode='DYNAMIC' AND (f.field_code IN ('processStatus','process_status') OR f.db_column_name='process_status') "
                + "AND COALESCE(f.is_system,0)=0 LIMIT 1"); var rows = query.executeQuery()) {
            if (rows.next()) throw new SQLException("V102 流程状态字段与自定义字段冲突，实体: " + rows.getString(1));
        }
        List<String> tables = businessTables(connection);
        boolean history = tableExists(connection, "ACT_HI_PROCINST");
        boolean runtime = tableExists(connection, "ACT_RU_EXECUTION");
        // 在任何 DDL 前检查无法确认的关联，不能把丢失的引擎实例误判为已完成。
        for (String table : tables) validateInstances(connection, table, history, runtime);
        for (String table : tables) {
            if (!columnExists(connection, table, "process_status")) {
                execute(connection, "ALTER TABLE `" + table + "` ADD COLUMN process_status "
                        + "VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '流程生命周期'");
            }
            String state = "CASE WHEN b.process_instance_id IS NULL OR b.process_instance_id='' "
                    + "THEN 'NOT_STARTED' "
                    + (runtime ? "WHEN EXISTS (SELECT 1 FROM ACT_RU_EXECUTION r WHERE r.ID_=b.process_instance_id) THEN 'RUNNING' " : "")
                    + (history ? "WHEN EXISTS (SELECT 1 FROM ACT_HI_PROCINST h WHERE h.PROC_INST_ID_=b.process_instance_id AND h.END_TIME_ IS NOT NULL) THEN 'COMPLETED' " : "")
                    + "ELSE b.process_status END";
            execute(connection, "UPDATE `" + table + "` b SET b.process_status=" + state);
            // 后续流程条件与 lifecycle 字段保持一致；只修正可验证的结束时间。
            if (history) execute(connection, "UPDATE `" + table + "` b JOIN ACT_HI_PROCINST h "
                    + "ON h.PROC_INST_ID_=b.process_instance_id SET b.process_end_time=h.END_TIME_ "
                    + "WHERE h.END_TIME_ IS NOT NULL AND b.process_status='COMPLETED'");
        }
        if (!columnExists(connection, "entity_process_link", "end_type")) {
            execute(connection, "ALTER TABLE entity_process_link ADD COLUMN end_type VARCHAR(20) NULL COMMENT '独立流程结束类型'");
        }
        if (history) execute(connection, "UPDATE entity_process_link l JOIN ACT_HI_PROCINST h "
                + "ON h.PROC_INST_ID_=l.process_instance_id SET l.end_type=CASE "
                + "WHEN h.DELETE_REASON_ LIKE '%撤回%' THEN 'WITHDRAWN' "
                + "WHEN h.DELETE_REASON_ IS NOT NULL AND h.DELETE_REASON_<>'' THEN 'TERMINATED' "
                + "ELSE 'COMPLETED' END WHERE h.END_TIME_ IS NOT NULL AND l.end_type IS NULL");
        registerField(connection);
    }

    /**
     * 表名只来自已登记的动态实体，并按运行时相同的标识符规则校验。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @return {@code v102}实体流程生命周期集合，供调用方遍历或展示
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private List<String> businessTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (var query = connection.prepareStatement("SELECT DISTINCT e.table_name FROM entity_definition e "
                + "JOIN information_schema.tables t ON t.table_schema=DATABASE() AND t.table_name=e.table_name "
                + "WHERE e.storage_mode='DYNAMIC' AND t.table_type='BASE TABLE'"); var rows = query.executeQuery()) {
            while (rows.next()) {
                String table = rows.getString(1);
                if (!table.matches("biz_[a-z0-9_]+") || table.length() > 63) {
                    throw new SQLException("非法动态实体物理表名: " + table);
                }
                tables.add(table);
            }
        }
        return tables;
    }

    /**
     * 校验{@code instances}；不满足约束时阻止后续处理。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param history 历史，供本方法校验{@code instances}时使用
     * @param runtime 运行时，后续用于判断有效期或展示该事件的发生时间
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private void validateInstances(Connection connection, String table, boolean history, boolean runtime) throws SQLException {
        String known = (runtime ? "EXISTS (SELECT 1 FROM ACT_RU_EXECUTION r WHERE r.ID_=b.process_instance_id)" : "FALSE")
                + " OR " + (history ? "EXISTS (SELECT 1 FROM ACT_HI_PROCINST h WHERE h.PROC_INST_ID_=b.process_instance_id AND h.END_TIME_ IS NOT NULL)" : "FALSE");
        try (var query = connection.prepareStatement("SELECT b.id FROM `" + table + "` b "
                + "WHERE b.process_instance_id IS NOT NULL AND b.process_instance_id<>'' AND NOT (" + known + ") LIMIT 1");
             var rows = query.executeQuery()) {
            if (rows.next()) throw new SQLException("V102 发现无法确认的流程关联，请先修复: " + table + "/" + rows.getString(1));
        }
        // 用户自定义同名字段不能被悄悄收编为系统字段。
        try (var query = connection.prepareStatement("SELECT f.id FROM entity_field f JOIN entity_definition e ON e.id=f.entity_id "
                + "WHERE e.table_name=? AND (f.field_code IN ('processStatus','process_status') OR f.db_column_name='process_status') "
                + "AND COALESCE(f.is_system,0)=0 LIMIT 1")) {
            query.setString(1, table);
            try (var rows = query.executeQuery()) {
                if (rows.next()) throw new SQLException("V102 流程状态列与自定义字段冲突: " + table);
            }
        }
    }

    /**
     * 只注册新增系统字段；既有字段 ID 与不可变发布文档保持不变。
     *
     * @param connection 连接，作为 {@code execute} 的输入影响后续处理
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private void registerField(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO entity_field(entity_id,field_code,field_name,field_type,db_type,field_length,
                    db_column_name,is_required,is_unique,sort_order,is_system,is_published,editable,value_storage,deleted)
                SELECT e.id,'processStatus','流程状态','STRING','varchar(20)',20,'process_status',0,0,
                    COALESCE(o.max_order,0)+1,1,CASE WHEN e.status='PUBLISHED' THEN 1 ELSE 0 END,0,'SCALAR',0
                FROM entity_definition e
                LEFT JOIN entity_field f ON f.entity_id=e.id AND f.db_column_name='process_status'
                LEFT JOIN (SELECT entity_id,MAX(sort_order) max_order FROM entity_field GROUP BY entity_id) o ON o.entity_id=e.id
                WHERE e.storage_mode='DYNAMIC' AND f.id IS NULL
                """);
    }
    /**
     * 判断表存在条件是否成立，供调用方选择后续分支。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 表存在条件成立时为 true，否则为 false
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")) {
            query.setString(1, table);
            try (var rows=query.executeQuery()) { return rows.next(); }
        }
    }
    /**
     * 判断列存在条件是否成立，供调用方选择后续分支。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，作为 {@code query.setString} 的输入影响后续处理
     * @return 列存在条件成立时为 true，否则为 false
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?")) {
            query.setString(1, table); query.setString(2, column);
            try (var rows=query.executeQuery()) { return rows.next(); }
        }
    }
    /**
     * 执行{@code v102}实体流程生命周期，并将结果传给后续步骤。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @param sql SQL，供本方法执行{@code v102}实体流程生命周期时使用
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement=connection.createStatement()) { statement.execute(sql); }
    }
}
