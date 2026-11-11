package com.workflow.dbmigrator.runner;

import org.flywaydb.core.api.ErrorCode;
import org.flywaydb.core.api.ErrorDetails;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.exception.FlywayBlockStatementExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Locale;

/**
 * 为不可变的 V062 提供 MySQL 临时表兼容执行，保留原文件及 Flyway 校验和。
 * 仅兼容已知临时表及角色授权清理语句，不跳过整个迁移，也不修复或改写迁移历史。
 */
public final class MySqlV062CompatibilityCallback implements Callback {
    private static final Logger LOG = LoggerFactory.getLogger(MySqlV062CompatibilityCallback.class);
    private static final String SCRIPT = "V062__remove_entity_list_scope_inventory.sql";
    private static final String CREATE_TEMPORARY = """
            CREATE TEMPORARY TABLE tmp_scope_inventory_menu_root (
              menu_id VARCHAR(64) NOT NULL PRIMARY KEY
            )
            """;
    private static final String ORIGINAL = """
            DELETE role_grant
            FROM sys_role_menu role_grant
            JOIN sys_menu menu ON menu.id = role_grant.menu_id
            LEFT JOIN tmp_scope_inventory_menu_root own_root ON own_root.menu_id = menu.id
            LEFT JOIN tmp_scope_inventory_menu_root parent_root ON parent_root.menu_id = menu.parent_id
            WHERE own_root.menu_id IS NOT NULL OR parent_root.menu_id IS NOT NULL
            """;
    private static final String COMPATIBLE = """
            DELETE role_grant
            FROM sys_role_menu role_grant
            JOIN sys_menu menu ON menu.id = role_grant.menu_id
            JOIN tmp_scope_inventory_menu_root removed_root
              ON removed_root.menu_id = menu.id OR removed_root.menu_id = menu.parent_id
            """;

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE_STATEMENT
                && context.getMigrationInfo() != null
                && MigrationVersion.fromVersion("62").equals(context.getMigrationInfo().getVersion())
                && SCRIPT.equals(context.getMigrationInfo().getScript())
                && context.getStatement() != null
                && (normalize(ORIGINAL).equals(normalize(context.getStatement().getSql()))
                    || normalize(CREATE_TEMPORARY).equals(normalize(context.getStatement().getSql())));
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    /**
     * 在 Flyway 当前连接上先完成兼容 SQL，成功后阻止原语句重复执行。
     * MySQL 不允许同一语句以两个别名重复读取临时表，单次 JOIN 的 OR 条件保留相同删除集合。
     * 任何 SQL 错误继续使迁移失败；不得将失败标成成功，也不得影响其他版本或其他 SQL。
     */
    @Override
    public void handle(Event event, Context context) {
        if (!supports(event, context)) return;
        try {
            if (!"MySQL".equalsIgnoreCase(context.getConnection().getMetaData().getDatabaseProductName())) return;
            if (normalize(CREATE_TEMPORARY).equals(normalize(context.getStatement().getSql()))) {
                createTemporaryRoot(context);
            } else {
                try (var statement = context.getConnection().createStatement()) {
                    int affected = statement.executeUpdate(COMPATIBLE);
                    LOG.info("V062 MySQL 临时表兼容执行完成，清理角色授权 {} 条；原迁移文件和校验和保持不变", affected);
                }
            }
        } catch (SQLException exception) {
            throw new FlywayException("V062 MySQL 临时表兼容执行失败", exception);
        }
        // 使用 Flyway 的语句级回调协议替代已执行的那一条语句，其余 V062 语句仍正常执行和记账。
        throw new FlywayBlockStatementExecutionException(
                new ErrorDetails(CompatibilityCode.MYSQL_V062, "已完成 V062 兼容语句"),
                "原语句已等价执行，迁移继续执行");
    }

    /** 临时表须继承被关联列的排序规则，不能依赖可能与历史表不同的空库默认排序规则。 */
    private void createTemporaryRoot(Context context) throws SQLException {
        try (var query = context.getConnection().createStatement();
             var rows = query.executeQuery("""
                     SELECT CHARACTER_SET_NAME, COLLATION_NAME
                     FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_menu' AND COLUMN_NAME = 'id'
                     """)) {
            if (!rows.next()) throw new SQLException("无法读取 sys_menu.id 排序规则");
            String charset = rows.getString(1);
            String collation = rows.getString(2);
            if (charset == null || collation == null || !charset.matches("[a-zA-Z0-9_]+")
                    || !collation.matches("[a-zA-Z0-9_]+")) {
                throw new SQLException("sys_menu.id 字符集或排序规则无效");
            }
            try (var statement = context.getConnection().createStatement()) {
                statement.executeUpdate(CREATE_TEMPORARY + " CHARACTER SET " + charset + " COLLATE " + collation);
            }
        }
    }

    @Override
    public String getCallbackName() {
        return "mysql-v062-temporary-table-compatibility";
    }

    private static String normalize(String sql) {
        return sql.replaceAll("(?m)--[^\\r\\n]*", " ")
                .replaceAll("\\s+", " ").trim().replaceFirst(";$", "").trim().toLowerCase(Locale.ROOT);
    }

    private enum CompatibilityCode implements ErrorCode { MYSQL_V062 }
}
