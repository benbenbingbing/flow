package com.workflow.integration.database.schema;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 受控的审计时间触发器模板。校验器只接受这些完整模板，不能借支持存储程序开放任意多语句。
 * PostgreSQL 只在行实际变化且时间未改动时刷新；Oracle 系列保留显式指定的时间。
 */
public final class AuditTimestampDdl {
    private AuditTimestampDdl() {}
    private static final String PG_FUNCTION = "CREATE OR REPLACE FUNCTION @function@() RETURNS trigger LANGUAGE plpgsql AS $flow_audit$ BEGIN IF NEW IS DISTINCT FROM OLD AND NEW.@column@ IS NOT DISTINCT FROM OLD.@column@ THEN NEW.@column@ := LOCALTIMESTAMP; END IF; RETURN NEW; END; $flow_audit$";
    private static final String PG_DROP_TRIGGER = "DROP TRIGGER IF EXISTS @trigger@ ON @table@";
    private static final String PG_TRIGGER = "CREATE TRIGGER @trigger@ BEFORE UPDATE ON @table@ FOR EACH ROW EXECUTE PROCEDURE @function@()";
    private static final String PG_DROP_FUNCTION = "DROP FUNCTION IF EXISTS @function@()";
    private static final String ORACLE_TRIGGER = "CREATE OR REPLACE TRIGGER @trigger@ BEFORE UPDATE ON @table@ FOR EACH ROW BEGIN IF NOT UPDATING('@rawColumn@') THEN :NEW.@column@ := LOCALTIMESTAMP; END IF; END;";
    private static final String KINGBASE_FUNCTION = PG_FUNCTION.replace("LANGUAGE plpgsql", "LANGUAGE plsql");
    private static final List<String> TEMPLATES = List.of(PG_FUNCTION, KINGBASE_FUNCTION, PG_DROP_TRIGGER, PG_TRIGGER, PG_DROP_FUNCTION, ORACLE_TRIGGER);

    public static List<String> postgres(String table, String column, String function, String trigger) {
        return List.of(fill(PG_FUNCTION, table, column, function, trigger),
                fill(PG_DROP_TRIGGER, table, column, function, trigger), fill(PG_TRIGGER, table, column, function, trigger));
    }
    public static List<String> oracle(String table, String column, String trigger) {
        return List.of(fill(ORACLE_TRIGGER, table, column, "", trigger));
    }
    public static List<String> kingbase(String table, String column, String function, String trigger) {
        return List.of(fill(KINGBASE_FUNCTION, table, column, function, trigger),
                fill(PG_DROP_TRIGGER, table, column, function, trigger), fill(PG_TRIGGER, table, column, function, trigger));
    }
    public static String dropPostgresFunction(String function) { return PG_DROP_FUNCTION.replace("@function@", function); }
    public static String dropPostgresTrigger(String table, String trigger) {
        return PG_DROP_TRIGGER.replace("@table@", table).replace("@trigger@", trigger);
    }

    /**
     * 队列可能在 CREATE TRIGGER 成功后丢失 ACK；对固定审计模板重建触发器，允许安全重试。
     * 该组合与原发布计划的 DROP/CREATE 一致，只处理内部模板，不重写任意触发器 SQL。
     */
    public static List<String> retryableExecution(String ddl) {
        var matcher = Pattern.compile("^CREATE TRIGGER (\"[A-Za-z][A-Za-z0-9_]{0,62}\") BEFORE UPDATE ON (\"[A-Za-z][A-Za-z0-9_]{0,62}\") .*").matcher(ddl);
        if (isGenerated(ddl) && matcher.matches()) {
            return List.of(dropPostgresTrigger(matcher.group(2), matcher.group(1)), ddl);
        }
        return List.of(ddl);
    }

    private static String fill(String template, String table, String column, String function, String trigger) {
        return template.replace("@table@", table).replace("@column@", column)
                .replace("@rawColumn@", column.replace("\"", ""))
                .replace("@function@", function).replace("@trigger@", trigger);
    }

    /** 模板变量只接受已经引用的 ASCII 标识符，不允许函数体、表达式或 SQL 文本注入。 */
    public static boolean isGenerated(String sql) {
        for (String template : TEMPLATES) {
            String regex = Pattern.quote(template);
            for (String token : List.of("table", "column", "function", "trigger")) {
                regex = regex.replace("@" + token + "@", "\\E\"[A-Za-z][A-Za-z0-9_]{0,62}\"\\Q");
            }
            regex = regex.replace("@rawColumn@", "\\E[A-Za-z][A-Za-z0-9_]{0,62}\\Q");
            if (sql.matches(regex)) return true;
        }
        return false;
    }
}
