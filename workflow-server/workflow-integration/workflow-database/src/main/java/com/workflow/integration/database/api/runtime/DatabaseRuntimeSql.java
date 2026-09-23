package com.workflow.integration.database.api.runtime;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;

/** MyBatis 动态 SQL 的纯语法入口；仅展开可信模板，业务值仍通过 #{...} 绑定。 */
public final class DatabaseRuntimeSql {
    /**
     * 初始化数据库运行时SQL，保存构造参数供后续方法使用。
     */
    private DatabaseRuntimeSql() {}

    /**
     * 返回当前工厂的数据库 UTC 时间表达式，不触发数据库查询。
     *
     * @param databaseId 数据库ID，后续用于处理UTC当前时间时定位或关联目标
     * @return 处理后的UTC当前时间文本，供调用方比较或展示
     */
    public static String utcNow(String databaseId) { return dialect(databaseId).utcTimestampExpression(); }

    /**
     * 保留业务时间列原有的会话时区语义；租约调用方必须显式使用 utcNow。
     *
     * @param databaseId 数据库ID，后续用于处理当前当前时间时定位或关联目标
     * @return 处理后的当前当前时间文本，供调用方比较或展示
     */
    public static String currentNow(String databaseId) { return dialect(databaseId).currentTimestampExpression(); }

    /**
     * 秒数取固定参数名，不接受请求中的表达式或原始 SQL。
     *
     * @param databaseId 数据库ID，后续用于处理UTC之后秒数时定位或关联目标
     * @param parameter 参数，供本方法处理UTC之后秒数时使用
     * @return 处理后的UTC之后秒数文本，供调用方比较或展示
     */
    public static String utcAfterSeconds(String databaseId, String parameter) {
        if (parameter == null || !parameter.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
            throw new IllegalArgumentException("时间偏移必须指定固定绑定参数名");
        }
        return dialect(databaseId).utcAfterSeconds("#{" + parameter + "}");
    }

    /**
     * 只在完整主键等值 UPDATE 的表名后插入产品支持的访问提示。
     *
     * @param databaseId 数据库ID，后续用于处理主要键更新{@code hint}时定位或关联目标
     * @return 处理后的主要键更新{@code hint}文本，供调用方比较或展示
     */
    public static String primaryKeyUpdateHint(String databaseId) {
        return DatabaseDialects.mutationsForDatabaseId(databaseId).primaryKeyUpdateHint();
    }

    /**
     * 处理方言，并将结果传给后续步骤。
     *
     * @param databaseId 数据库ID，后续用于处理方言时定位或关联目标
     * @return 处理后的方言结果，供调用方继续处理
     */
    private static DatabaseRuntimeDialect dialect(String databaseId) {
        return DatabaseDialects.runtime(DatabaseQueryDialects.forDatabaseId(databaseId).vendor());
    }
}
