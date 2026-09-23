package com.workflow.integration.database.schema.template;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 受控的审计时间触发器模板。校验器只接受这些完整模板，不能借支持存储程序开放任意多语句。
 * PostgreSQL 只在行实际变化且时间未改动时刷新；Oracle 系列保留显式指定的时间。
 */
public final class AuditTimestampDdl {
    /**
     * 初始化审计时间戳DDL，保存构造参数供后续方法使用。
     */
    private AuditTimestampDdl() {}
    private static final String PG_FUNCTION = "CREATE OR REPLACE FUNCTION @function@() RETURNS trigger LANGUAGE plpgsql AS $flow_audit$ BEGIN IF NEW IS DISTINCT FROM OLD AND NEW.@column@ IS NOT DISTINCT FROM OLD.@column@ THEN NEW.@column@ := LOCALTIMESTAMP; END IF; RETURN NEW; END; $flow_audit$";
    private static final String PG_DROP_TRIGGER = "DROP TRIGGER IF EXISTS @trigger@ ON @table@";
    private static final String PG_TRIGGER = "CREATE TRIGGER @trigger@ BEFORE UPDATE ON @table@ FOR EACH ROW EXECUTE PROCEDURE @function@()";
    private static final String PG_DROP_FUNCTION = "DROP FUNCTION IF EXISTS @function@()";
    private static final String ORACLE_TRIGGER = "CREATE OR REPLACE TRIGGER @trigger@ BEFORE UPDATE ON @table@ FOR EACH ROW BEGIN IF NOT UPDATING('@rawColumn@') THEN :NEW.@column@ := LOCALTIMESTAMP; END IF; END;";
    private static final String KINGBASE_FUNCTION = PG_FUNCTION.replace("LANGUAGE plpgsql", "LANGUAGE plsql");
    private static final List<String> TEMPLATES = List.of(PG_FUNCTION, KINGBASE_FUNCTION, PG_DROP_TRIGGER, PG_TRIGGER, PG_DROP_FUNCTION, ORACLE_TRIGGER);

    /**
     * 整理{@code postgres}数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理{@code postgres}时使用
     * @param function {@code function}，供本方法处理{@code postgres}时使用
     * @param trigger 触发条件，供本方法处理{@code postgres}时使用
     * @return 审计时间戳DDL集合，供调用方遍历或展示
     */
    public static List<String> postgres(String table, String column, String function, String trigger) {
        return List.of(fill(PG_FUNCTION, table, column, function, trigger),
                fill(PG_DROP_TRIGGER, table, column, function, trigger), fill(PG_TRIGGER, table, column, function, trigger));
    }
    /**
     * 整理{@code oracle}数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理{@code oracle}时使用
     * @param trigger 触发条件，供本方法处理{@code oracle}时使用
     * @return 审计时间戳DDL集合，供调用方遍历或展示
     */
    public static List<String> oracle(String table, String column, String trigger) {
        return List.of(fill(ORACLE_TRIGGER, table, column, "", trigger));
    }
    /**
     * 整理{@code kingbase}数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理{@code kingbase}时使用
     * @param function {@code function}，供本方法处理{@code kingbase}时使用
     * @param trigger 触发条件，供本方法处理{@code kingbase}时使用
     * @return 审计时间戳DDL集合，供调用方遍历或展示
     */
    public static List<String> kingbase(String table, String column, String function, String trigger) {
        return List.of(fill(KINGBASE_FUNCTION, table, column, function, trigger),
                fill(PG_DROP_TRIGGER, table, column, function, trigger), fill(PG_TRIGGER, table, column, function, trigger));
    }
    /**
     * 生成{@code drop}{@code postgres}{@code function}文本，供后续匹配或展示。
     *
     * @param function {@code function}，作为 {@code PG_DROP_FUNCTION.replace} 的输入影响后续处理
     * @return 处理后的{@code drop}{@code postgres}{@code function}文本，供调用方比较或展示
     */
    public static String dropPostgresFunction(String function) { return PG_DROP_FUNCTION.replace("@function@", function); }
    /**
     * 生成{@code drop}{@code postgres}触发条件文本，供后续匹配或展示。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param trigger 触发条件，供本方法处理{@code drop}{@code postgres}触发条件时使用
     * @return 处理后的{@code drop}{@code postgres}触发条件文本，供调用方比较或展示
     */
    public static String dropPostgresTrigger(String table, String trigger) {
        return PG_DROP_TRIGGER.replace("@table@", table).replace("@trigger@", trigger);
    }

    /**
     * 队列可能在 CREATE TRIGGER 成功后丢失 ACK；对固定审计模板重建触发器，允许安全重试。
     * 该组合与原发布计划的 DROP/CREATE 一致，只处理内部模板，不重写任意触发器 SQL。
     *
     * @param ddl DDL，作为 {@code matcher} 的输入影响后续处理
     * @return 审计时间戳DDL集合，供调用方遍历或展示
     */
    public static List<String> retryableExecution(String ddl) {
        var matcher = Pattern.compile("^CREATE TRIGGER (\"[A-Za-z][A-Za-z0-9_]{0,62}\") BEFORE UPDATE ON (\"[A-Za-z][A-Za-z0-9_]{0,62}\") .*").matcher(ddl);
        if (isGenerated(ddl) && matcher.matches()) {
            return List.of(dropPostgresTrigger(matcher.group(2), matcher.group(1)), ddl);
        }
        return List.of(ddl);
    }

    /**
     * 生成{@code fill}文本，供后续匹配或展示。
     *
     * @param template 模板，供本方法处理{@code fill}时使用
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，供本方法处理{@code fill}时使用
     * @param function {@code function}，供本方法处理{@code fill}时使用
     * @param trigger 触发条件，作为 {@code replace} 的输入影响后续处理
     * @return 处理后的{@code fill}文本，供调用方比较或展示
     */
    private static String fill(String template, String table, String column, String function, String trigger) {
        return template.replace("@table@", table).replace("@column@", column)
                .replace("@rawColumn@", column.replace("\"", ""))
                .replace("@function@", function).replace("@trigger@", trigger);
    }

    /**
     * 模板变量只接受已经引用的 ASCII 标识符，不允许函数体、表达式或 SQL 文本注入。
     *
     * @param sql SQL，供本方法判断是否{@code generated}时使用
     * @return {@code generated}条件成立时为 true，否则为 false
     */
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
