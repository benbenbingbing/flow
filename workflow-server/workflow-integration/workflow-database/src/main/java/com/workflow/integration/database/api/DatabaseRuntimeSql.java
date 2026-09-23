package com.workflow.integration.database.api;

/** MyBatis 动态 SQL 的纯语法入口；仅展开可信模板，业务值仍通过 #{...} 绑定。 */
public final class DatabaseRuntimeSql {
    private DatabaseRuntimeSql() {}

    /** 返回当前工厂的数据库 UTC 时间表达式，不触发数据库查询。 */
    public static String utcNow(String databaseId) { return dialect(databaseId).utcTimestampExpression(); }

    /** 保留业务时间列原有的会话时区语义；租约调用方必须显式使用 utcNow。 */
    public static String currentNow(String databaseId) { return dialect(databaseId).currentTimestampExpression(); }

    /** 秒数取固定参数名，不接受请求中的表达式或原始 SQL。 */
    public static String utcAfterSeconds(String databaseId, String parameter) {
        if (parameter == null || !parameter.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
            throw new IllegalArgumentException("时间偏移必须指定固定绑定参数名");
        }
        return dialect(databaseId).utcAfterSeconds("#{" + parameter + "}");
    }

    /** 只在完整主键等值 UPDATE 的表名后插入产品支持的访问提示。 */
    public static String primaryKeyUpdateHint(String databaseId) {
        return DatabaseDialects.mutationsForDatabaseId(databaseId).primaryKeyUpdateHint();
    }

    private static DatabaseRuntimeDialect dialect(String databaseId) {
        return DatabaseDialects.runtime(DatabaseQueryDialects.forDatabaseId(databaseId).vendor());
    }
}
