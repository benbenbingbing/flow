package db.migration;

import com.workflow.integration.database.api.DatabaseDialects;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 退役职务定义上的单位类型和人数配置，保留职务定义及所有当前任职关系。 */
public class V145__remove_position_restrictions extends BaseJavaMigration {
    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    /**
     * 仅移除存在的旧列，让隐式提交的数据库可从已完成的步骤继续。
     *
     * @param context Flyway 提供的当前库连接
     * @throws SQLException 目标表不存在、目录查询或 DDL 失败时停止迁移
     */
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        Set<String> columns = new HashSet<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM sys_position WHERE 1 = 0")) {
            var metadata = rows.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                columns.add(metadata.getColumnName(index).toLowerCase(Locale.ROOT));
            }
        }
        if (!columns.contains("applicable_unit_type") && !columns.contains("holder_mode")) {
            return;
        }

        // 两列各自只有单列 CHECK。达梦兼容模式及 OB MySQL 先显式移除已知约束，避免级联删除其他对象。
        String url = connection.getMetaData().getURL();
        if (!url.startsWith("jdbc:h2:")) {
            switch (DatabaseDialects.resolve(System.getenv("WORKFLOW_DATABASE_VENDOR"), url)) {
                case DM -> dropChecks(connection, """
                        SELECT constraint_name FROM user_constraints
                        WHERE table_name = 'SYS_POSITION' AND constraint_type = 'C'
                        """, "DROP CONSTRAINT ", Set.of("CHK_SYS_POSITION_UNIT_TYPE",
                        "CHK_SYS_POSITION_HOLDER_MODE", "CK_SYS_POSITION_CHK_S_B4B285AE",
                        "CK_SYS_POSITION_CHK_S_B341DD2A"));
                case OCEANBASE_MYSQL -> dropChecks(connection, """
                        SELECT constraint_name FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE() AND table_name = 'sys_position' AND constraint_type = 'CHECK'
                        """, "DROP CHECK ", Set.of("chk_sys_position_unit_type", "chk_sys_position_holder_mode"));
                default -> { }
            }
        }
        for (String column : new String[]{"applicable_unit_type", "holder_mode"}) {
            if (columns.contains(column)) {
                execute(connection, "ALTER TABLE sys_position DROP COLUMN " + column);
            }
        }
    }

    /** 只移除历史迁移或空库脚本中属于这两列的已知检查约束。 */
    private void dropChecks(Connection connection, String query, String operation, Set<String> knownNames)
            throws SQLException {
        Set<String> present = new HashSet<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) {
                String name = rows.getString(1);
                if (knownNames.contains(name)) {
                    present.add(name);
                }
            }
        }
        for (String name : present) {
            execute(connection, "ALTER TABLE sys_position " + operation + name);
        }
    }

    private void execute(Connection connection, String ddl) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }
}
