package com.workflow.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用启动时执行：为已有实体数据表补充 dept_id 列并回填数据
 * 幂等设计：可多次执行，不会覆盖已有 dept_id
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "workflow.data-fix.dept-id", name = "enabled", havingValue = "true")
@Order(100)
@RequiredArgsConstructor
public class DeptIdDataFixRunner implements CommandLineRunner {

    /** 数据源，用于直连执行 SQL 进行数据修复 */
    private final DataSource dataSource;

    /**
     * 应用启动时执行入口
     *
     * @param args 启动参数
     * @throws Exception 执行过程中发生异常时抛出
     */
    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            fixDeptId(connection);
        }
    }

    /**
     * 为各实体数据表补充 dept_id 列并回填数据
     * <p>
     * 流程：读取实体定义 -> 逐表检查并新增 dept_id 列 -> 通过 submitter_id 关联 sys_user 回填 dept_id。
     * </p>
     *
     * @param connection 数据库连接
     * @throws SQLException 执行 SQL 发生异常时抛出
     */
    private void fixDeptId(Connection connection) throws SQLException {
        // 1. 获取所有有效实体编码
        List<EntityTableRef> entities = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT entity_code, table_name FROM entity_definition")) {
            while (rs.next()) {
                String entityCode = rs.getString("entity_code");
                String tableName = rs.getString("table_name");
                if (tableName == null || tableName.isBlank()) {
                    log.warn("[DeptIdFix] 实体 {} 未登记物理表名，跳过", entityCode);
                    continue;
                }
                if (!tableName.matches("[a-z][a-z0-9_]{0,63}")) {
                    log.warn("[DeptIdFix] 实体 {} 的物理表名不合法，跳过: {}", entityCode, tableName);
                    continue;
                }
                entities.add(new EntityTableRef(entityCode, tableName));
            }
        }

        if (entities.isEmpty()) {
            log.info("[DeptIdFix] 未发现实体定义，跳过");
            return;
        }

        log.info("[DeptIdFix] 发现 {} 个实体定义，开始处理 dept_id ...", entities.size());

        int totalUpdated = 0;
        for (EntityTableRef entity : entities) {
            String entityCode = entity.entityCode();
            String tableName = entity.tableName();

            // 2. 检查表是否存在
            if (!tableExists(connection, tableName)) {
                continue;
            }

            // 3. 检查 dept_id 列是否存在，不存在则添加
            if (!columnExists(connection, tableName, "dept_id")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE `" + tableName + "` ADD COLUMN `dept_id` VARCHAR(64) DEFAULT NULL COMMENT '所属部门ID（数据权限用）'");
                    log.info("[DeptIdFix] 表 {} 新增 dept_id 列", tableName);
                }
            }

            // 4. 检查 submitter_id 列是否存在（没有则无法关联）
            if (!columnExists(connection, tableName, "submitter_id")) {
                log.warn("[DeptIdFix] 表 {} 缺少 submitter_id 列，跳过数据填充", tableName);
                continue;
            }

            // 5. 尝试通过 submitter_id = sys_user.username 回填
            int updated = updateDeptIdByUsername(connection, tableName);

            // 6. 对于仍未填充的，尝试 submitter_id = sys_user.id 回填
            int updatedById = updateDeptIdByUserId(connection, tableName);

            totalUpdated += updated + updatedById;
            log.info("[DeptIdFix] 表 {} 更新 dept_id 记录数: username关联={}, id关联={}", tableName, updated, updatedById);
        }

        log.info("[DeptIdFix] 处理完成，共更新 {} 条记录", totalUpdated);
    }

    /**
     * 实体表引用记录（实体编码 + 物理表名）
     *
     * @param entityCode 实体编码
     * @param tableName  物理表名
     */
    private record EntityTableRef(String entityCode, String tableName) {
    }

    /**
     * 检查数据表是否存在
     *
     * @param connection 数据库连接
     * @param tableName  物理表名
     * @return 表存在返回 true，否则 false
     * @throws SQLException 执行 SQL 发生异常时抛出
     */
    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            ps.setString(1, tableName);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * 检查表中指定列是否存在
     *
     * @param connection  数据库连接
     * @param tableName   物理表名
     * @param columnName  列名
     * @return 列存在返回 true，否则 false
     * @throws SQLException 执行 SQL 发生异常时抛出
     */
    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * 通过 submitter_id = sys_user.username 回填 dept_id
     *
     * @param connection 数据库连接
     * @param tableName  物理表名
     * @return 回填的记录数
     * @throws SQLException 执行 SQL 发生异常时抛出
     */
    private int updateDeptIdByUsername(Connection connection, String tableName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "UPDATE `" + tableName + "` t " +
                "JOIN sys_user u ON t.submitter_id = u.username " +
                "SET t.dept_id = u.dept_id " +
                "WHERE (t.dept_id IS NULL OR t.dept_id = '') AND u.dept_id IS NOT NULL AND u.dept_id != ''"
            );
            return stmt.getUpdateCount();
        }
    }

    /**
     * 通过 submitter_id = sys_user.id 回填 dept_id（username 关联未命中的兜底）
     *
     * @param connection 数据库连接
     * @param tableName  物理表名
     * @return 回填的记录数
     * @throws SQLException 执行 SQL 发生异常时抛出
     */
    private int updateDeptIdByUserId(Connection connection, String tableName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "UPDATE `" + tableName + "` t " +
                "JOIN sys_user u ON t.submitter_id = u.id " +
                "SET t.dept_id = u.dept_id " +
                "WHERE (t.dept_id IS NULL OR t.dept_id = '') AND u.dept_id IS NOT NULL AND u.dept_id != ''"
            );
            return stmt.getUpdateCount();
        }
    }
}
