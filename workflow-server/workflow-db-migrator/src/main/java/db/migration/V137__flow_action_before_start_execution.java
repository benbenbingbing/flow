package db.migration;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.util.List;

/** 发起前动作还没有流程实例；保留真实 NULL，不伪造实例 ID，也不改写已有执行记录。 */
public class V137__flow_action_before_start_execution extends BaseJavaMigration {
    public static final String COMMENT = "流程实例 ID；发起前动作尚未创建实例时为空，按发布版本和实体定位。";

    @Override public boolean canExecuteInTransaction() { return false; }

    /** DDL 隐式提交后可重试；只放宽可空性，保留字段长度、已有值及所有索引。 */
    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String url = connection.getMetaData().getURL();
        if (url.startsWith("jdbc:h2:")) {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='MODE'")) {
                if (rows.next()) url += ";MODE=" + rows.getString(1);
            }
        }
        var vendor = DatabaseDialects.resolve(System.getenv("WORKFLOW_DATABASE_VENDOR"), url);
        boolean nullable;
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT process_instance_id FROM process_action_execution WHERE 1=0")) {
            nullable = rows.getMetaData().isNullable(1) == ResultSetMetaData.columnNullable;
        }
        var statements = statements(vendor);
        boolean mysql = vendor == DatabaseVendor.MYSQL || vendor == DatabaseVendor.OCEANBASE_MYSQL;
        for (int index = nullable && !mysql ? 1 : 0; index < statements.size(); index++) {
            try (var statement = connection.createStatement()) { statement.execute(statements.get(index)); }
        }
    }

    /** 七方言保持相同可空语义，供迁移及空库脚本一致性测试核对。 */
    public static List<String> statements(DatabaseVendor vendor) {
        return switch (vendor) {
            case MYSQL, OCEANBASE_MYSQL -> List.of("ALTER TABLE process_action_execution MODIFY process_instance_id VARCHAR(64) NULL COMMENT '" + COMMENT + "'");
            case POSTGRESQL, KINGBASE -> List.of(
                    "ALTER TABLE process_action_execution ALTER COLUMN process_instance_id DROP NOT NULL",
                    "COMMENT ON COLUMN process_action_execution.process_instance_id IS '" + COMMENT + "'");
            case ORACLE, DM, OCEANBASE_ORACLE -> List.of(
                    "ALTER TABLE process_action_execution MODIFY (process_instance_id NULL)",
                    "COMMENT ON COLUMN process_action_execution.process_instance_id IS '" + COMMENT + "'");
        };
    }
}
