package db.migration;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.*;
import java.util.*;

/** 每个来源对象仅保留最新原文，不回填历史数据，也不把大文本放入常规来源绑定查询。 */
public class V131__identity_source_snapshot extends BaseJavaMigration {
    private static final String TABLE = "sys_identity_source_snapshot";

    @Override public boolean canExecuteInTransaction() { return false; }

    /** MySQL DDL 隐式提交后可重试注释步骤；不修改任何历史迁移和既有业务数据。 */
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String url = connection.getMetaData().getURL();
        if (url.startsWith("jdbc:h2:")) {
            // H2 元数据 URL 会移除 MODE；仅测试使用，恢复实际模式交给统一入口校验，不能盲目退回 MySQL。
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'")) {
                if (rows.next()) url += ";MODE=" + rows.getString(1);
            }
        }
        var vendor = DatabaseDialects.resolve(System.getenv("WORKFLOW_DATABASE_VENDOR"), url);
        List<String> statements = statements(vendor);
        boolean exists = false;
        try (var rows = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), null, new String[]{"TABLE"})) {
            while (rows.next()) if (TABLE.equalsIgnoreCase(rows.getString("TABLE_NAME"))) exists = true;
        }
        for (int i = exists ? 1 : 0; i < statements.size(); i++) {
            try (var statement = connection.createStatement()) { statement.execute(statements.get(i)); }
        }
    }

    /** 七种数据库使用同一字段清单；独立返回语句便于验证空库脚本与实际迁移的等价语义。 */
    public static List<String> statements(DatabaseVendor vendor) {
        boolean mysql = vendor == DatabaseVendor.MYSQL || vendor == DatabaseVendor.OCEANBASE_MYSQL;
        boolean oracle = vendor == DatabaseVendor.ORACLE || vendor == DatabaseVendor.DM || vendor == DatabaseVendor.OCEANBASE_ORACLE;
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put("source_key", "CHAR(64) NOT NULL");
        definitions.put("source_version", oracle ? "NUMBER(19)" : "BIGINT");
        definitions.put("payload_hash", "CHAR(64) NOT NULL");
        definitions.put("raw_json", (mysql ? "LONGTEXT" : oracle ? "CLOB" : "TEXT") + " NOT NULL");
        definitions.put("raw_hash", "CHAR(64) NOT NULL");
        definitions.put("raw_bytes", (oracle ? "NUMBER(10)" : "INTEGER") + " NOT NULL");
        definitions.put("is_current", (oracle ? "NUMBER(5)" : "SMALLINT") + " DEFAULT 1 NOT NULL");
        definitions.put("recorded_at", (mysql ? "DATETIME(6)" : "TIMESTAMP(6)") + " NOT NULL");
        definitions.put("recorded_by", (oracle ? "VARCHAR2(64)" : "VARCHAR(64)") + " NOT NULL");
        var comments = comments();
        List<String> columns = new ArrayList<>();
        definitions.forEach((name, definition) -> columns.add(name + " " + definition
                + (mysql ? " COMMENT '" + comments.get(name) + "'" : "")));
        columns.add("CONSTRAINT pk_identity_snapshot PRIMARY KEY (source_key)");
        List<String> result = new ArrayList<>();
        result.add("CREATE TABLE " + TABLE + " (\n  " + String.join(",\n  ", columns) + "\n)"
                + (mysql ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='来源身份最新原文快照'" : ""));
        if (!mysql) {
            result.add("COMMENT ON TABLE " + TABLE + " IS '来源身份最新原文快照'");
            comments.forEach((name, comment) -> result.add("COMMENT ON COLUMN " + TABLE + "." + name + " IS '" + comment + "'"));
        }
        return List.copyOf(result);
    }

    private static Map<String, String> comments() {
        Map<String, String> comments = new LinkedHashMap<>();
        comments.put("source_key", "关联来源绑定的稳定哈希主键，每个用户或组织仅保留一条原文");
        comments.put("source_version", "原文对应的来源版本，SERIAL 模式为空");
        comments.put("payload_hash", "原文对应的业务快照哈希，用于追溯映射后的资料");
        comments.put("raw_json", "单条来源 JSON 对象原文，仅在管理端授权后按需读取");
        comments.put("raw_hash", "原文 UTF-8 字节的 SHA-256，用于跳过完全重复的原文写入");
        comments.put("raw_bytes", "原文 UTF-8 字节数，用于大小校验及管理端展示");
        comments.put("is_current", "是否对应当前业务资料，无原文的业务更新后标为过期");
        comments.put("recorded_at", "实际留存原文的时间，重复同步和过期标记不刷新");
        comments.put("recorded_by", "实际留存原文的有效操作人，用于审计追溯");
        return comments;
    }
}
