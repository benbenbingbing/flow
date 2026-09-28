package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 注册系统管理下的人员交接入口和独立操作权限，仅为已有超级管理员补齐授权。
 * 使用标准 JDBC DML，避免菜单注册依赖 MySQL 的 MD5、INSERT IGNORE 或无 FROM 查询语法。
 */
public class V110__task_handover_menu extends BaseJavaMigration {
    private static final String PARENT_ID = "400";
    private static final Menu PAGE = new Menu("task_handover_menu_001", PARENT_ID, "人员交接", "C",
            "/system/task-handover", "system/TaskHandoverManagement", "system:task-handover:view",
            "Switch", 36, "查询任意状态人员的待办并执行管理员交接");
    private static final Menu TRANSFER = new Menu("task_handover_transfer_001", PAGE.id(), "批量交接", "F",
            null, null, "system:task-handover:transfer", null, 1, "将选中的待办交接给状态正常的人员");

    private record Menu(String id, String parentId, String name, String type, String path,
                        String component, String permission, String icon, int sort, String remark) { }

    /**
     * 菜单和授权必须整体成功；已有固定 ID 或路由语义冲突时停止，不能覆盖租户自定义菜单。
     *
     * @param context Flyway 提供的当前连接，所有校验和写入复用同一事务
     * @throws SQLException 父目录缺失、菜单冲突或数据库写入失败
     */
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        boolean ownTransaction = connection.getAutoCommit();
        if (ownTransaction) connection.setAutoCommit(false);
        try {
            validateParent(connection);
            for (Menu menu : List.of(PAGE, TRANSFER)) ensureMenu(connection, menu);
            grantAdministrators(connection);
            if (ownTransaction) connection.commit();
        } catch (SQLException error) {
            if (ownTransaction) connection.rollback();
            throw error;
        } finally {
            if (ownTransaction) connection.setAutoCommit(true);
        }
    }

    /** V070 已建立固定父目录；缺失或被重新利用意味着需要先核实环境，不能静默创建第二套目录。 */
    private void validateParent(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT path, menu_type, deleted FROM sys_menu WHERE id = ?")) {
            query.setString(1, PARENT_ID);
            try (var rows = query.executeQuery()) {
                if (!rows.next() || !"/system".equals(rows.getString("path"))
                        || !"M".equals(rows.getString("menu_type")) || rows.getInt("deleted") != 0) {
                    throw new SQLException("V110: 系统管理父菜单 400 缺失或语义冲突，请先核实菜单配置");
                }
            }
        }
    }

    /** 允许未完成迁移重试，但仅复用语义一致的固定菜单，不覆盖其名称、排序或人工启停配置。 */
    private void ensureMenu(Connection connection, Menu menu) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT id FROM sys_menu WHERE id <> ? AND (path = ? OR perm = ?)")) {
            query.setString(1, menu.id());
            query.setString(2, menu.path());
            query.setString(3, menu.permission());
            try (var rows = query.executeQuery()) {
                if (rows.next()) throw new SQLException("V110: 人员交接路由或权限已被其他菜单占用: " + rows.getString(1));
            }
        }
        try (var query = connection.prepareStatement(
                "SELECT parent_id, menu_type, path, component, perm, deleted FROM sys_menu WHERE id = ?")) {
            query.setString(1, menu.id());
            try (var rows = query.executeQuery()) {
                if (rows.next()) {
                    if (!Objects.equals(menu.parentId(), rows.getString("parent_id"))
                            || !Objects.equals(menu.type(), rows.getString("menu_type"))
                            || !Objects.equals(menu.path(), rows.getString("path"))
                            || !Objects.equals(menu.component(), rows.getString("component"))
                            || !Objects.equals(menu.permission(), rows.getString("perm"))
                            || rows.getInt("deleted") != 0) {
                        throw new SQLException("V110: 人员交接菜单固定 ID 发生冲突: " + menu.id());
                    }
                    return;
                }
            }
        }
        try (var insert = connection.prepareStatement("""
                INSERT INTO sys_menu
                  (id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
                   status, visible, is_frame, is_cache, keep_alive, breadcrumb, remark, deleted,
                   create_by, create_time, update_by, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '0', '0', '0', '0', '0', '1', ?, 0,
                        'migration-v110', CURRENT_TIMESTAMP, 'migration-v110', CURRENT_TIMESTAMP)
                """)) {
            insert.setString(1, menu.id());
            insert.setString(2, menu.parentId());
            insert.setString(3, menu.name());
            insert.setString(4, menu.type());
            insert.setString(5, menu.icon());
            insert.setInt(6, menu.sort());
            insert.setString(7, menu.path());
            insert.setString(8, menu.component());
            insert.setString(9, menu.permission());
            insert.setString(10, menu.remark());
            insert.executeUpdate();
        }
    }

    /** 只授权已有超级管理员；普通用户管理员可由权限管理员后续显式分配，不能继承所有系统菜单使用者。 */
    private void grantAdministrators(Connection connection) throws SQLException {
        List<String> administratorIds = new ArrayList<>();
        try (var query = connection.prepareStatement(
                "SELECT id FROM sys_role WHERE role_code = 'super_admin' AND deleted = 0");
             var rows = query.executeQuery()) {
            while (rows.next()) administratorIds.add(rows.getString(1));
        }
        for (String roleId : administratorIds) {
            for (String menuId : List.of(PARENT_ID, PAGE.id(), TRANSFER.id())) {
                try (var query = connection.prepareStatement(
                        "SELECT id FROM sys_role_menu WHERE role_id = ? AND menu_id = ?")) {
                    query.setString(1, roleId);
                    query.setString(2, menuId);
                    try (var rows = query.executeQuery()) {
                        if (rows.next()) continue;
                    }
                }
                try (var insert = connection.prepareStatement(
                        "INSERT INTO sys_role_menu (id, role_id, menu_id, create_time) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
                    insert.setString(1, UUID.randomUUID().toString());
                    insert.setString(2, roleId);
                    insert.setString(3, menuId);
                    insert.executeUpdate();
                }
            }
        }
    }
}
