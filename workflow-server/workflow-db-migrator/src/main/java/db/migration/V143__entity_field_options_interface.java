package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** 扩展接口选项绑定；历史字段保持原代码表，不回填或改写业务数据。 */
public class V143__entity_field_options_interface extends BaseJavaMigration {
    @Override public boolean canExecuteInTransaction() { return false; }

    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String url = connection.getMetaData().getURL();
        String configured = System.getenv("WORKFLOW_DATABASE_VENDOR");
        // H2 仅用于执行方言回归，不改变生产数据库的方言识别。
        if (url.startsWith("jdbc:h2:") && (configured == null || configured.isBlank() || "auto".equalsIgnoreCase(configured))) {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='MODE'")) {
                if (rows.next()) configured = rows.getString(1);
            }
        }
        var vendor = com.workflow.integration.database.api.DatabaseDialects.resolve(configured, url);
        var column = new V138__flow_action_dependencies.Column("entity_field", "options_interface_id", false,
                "已注册的只读选项接口ID；与代码表互斥，供表单加载外部候选");
        boolean exists = false;
        // 隐式提交型数据库可能已完成 ADD；重试时仅补字段注释。
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT * FROM entity_field WHERE 1=0")) {
            var metadata = rows.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                if (column.name().equalsIgnoreCase(metadata.getColumnName(index))) exists = true;
            }
        }
        var statements = V138__flow_action_dependencies.columnStatements(vendor, column);
        for (int index = exists ? 1 : 0; index < statements.size(); index++) {
            try (var statement = connection.createStatement()) { statement.execute(statements.get(index)); }
        }
    }
}
