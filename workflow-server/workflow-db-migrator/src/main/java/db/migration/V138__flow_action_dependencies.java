package db.migration;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.util.List;

/** 单前置动作及本次触发的执行关联；旧配置默认独立执行，不回填依赖或伪造业务输出。 */
public class V138__flow_action_dependencies extends BaseJavaMigration {
    public record Column(String table, String name, boolean large, String comment) {}
    public static final List<Column> COLUMNS = List.of(
            new Column("process_action", "predecessor_action_id", false, "同组提交后前置动作ID，发布时转换为该版本ID，不随排序改变"),
            new Column("process_action_execution", "predecessor_execution_id", false, "本次触发的前置执行ID，仅其成功或成功重放后放行"),
            new Column("process_action_execution", "action_snapshot", true, "入队动作完整快照，重试沿用原配置，不向执行详情返回"),
            new Column("process_action_execution", "output_json", true, "成功动作未脱敏业务输出，仅供前置结果引用，不向日志返回"));

    @Override public boolean canExecuteInTransaction() { return false; }

    /** 按元数据跳过已完成的 DDL，允许隐式提交型数据库在中断后继续，绝不改历史迁移。 */
    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String url = connection.getMetaData().getURL();
        if (url.startsWith("jdbc:h2:")) {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='MODE'")) {
                if (rows.next()) url += ";MODE=" + rows.getString(1);
            }
        }
        var vendor = DatabaseDialects.resolve(System.getenv("WORKFLOW_DATABASE_VENDOR"), url);
        for (var column : COLUMNS) {
            boolean exists = false;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM " + column.table() + " WHERE 1=0")) {
                var metadata = rows.getMetaData();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    if (column.name().equalsIgnoreCase(metadata.getColumnName(index))) exists = true;
                }
            }
            var ddl = columnStatements(vendor, column);
            for (int index = exists ? 1 : 0; index < ddl.size(); index++) {
                try (var statement = connection.createStatement()) { statement.execute(ddl.get(index)); }
            }
        }
        createIndex(connection, "idx_action_exec_predecessor", "status, predecessor_execution_id");
        createIndex(connection, "idx_action_exec_replay_success", "replay_root_id, status");
    }

    /** 七种方言的字段语义相同；大对象使用 LONGTEXT / TEXT / CLOB，避免截断业务输出。 */
    public static List<String> columnStatements(DatabaseVendor vendor, Column column) {
        boolean mysql = vendor == DatabaseVendor.MYSQL || vendor == DatabaseVendor.OCEANBASE_MYSQL;
        boolean oracle = vendor == DatabaseVendor.ORACLE || vendor == DatabaseVendor.DM || vendor == DatabaseVendor.OCEANBASE_ORACLE;
        String type = column.large() ? mysql ? "LONGTEXT" : oracle ? "CLOB" : "TEXT"
                : oracle ? "VARCHAR2(64 CHAR)" : "VARCHAR(64)";
        String definition = column.name() + " " + type + " DEFAULT NULL"
                + (mysql ? " COMMENT '" + column.comment() + "'" : "");
        String add = "ALTER TABLE " + column.table() + " ADD " + (oracle ? "(" + definition + ")" : "COLUMN " + definition);
        return mysql ? List.of(add) : List.of(add,
                "COMMENT ON COLUMN " + column.table() + "." + column.name() + " IS '" + column.comment() + "'");
    }

    private static void createIndex(Connection connection, String name, String columns) throws Exception {
        // 通过查询元数据取得实际大小写及 schema，兼容 Oracle、PG 和 MySQL 的标识符规则。
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM process_action_execution WHERE 1=0")) {
            var metadata = rows.getMetaData();
            try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(),
                    metadata.getSchemaName(1), metadata.getTableName(1), false, false)) {
                while (indexes.next()) if (name.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return;
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE INDEX " + name + " ON process_action_execution (" + columns + ")");
        }
    }
}
