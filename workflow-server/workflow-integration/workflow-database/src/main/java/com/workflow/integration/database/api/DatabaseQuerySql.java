package com.workflow.integration.database.api;

import java.util.regex.Pattern;

/**
 * 注解/XML 查询使用的显式方言片段入口，不解析或重写已有 SQL。
 * 调用方式为 MyBatis 标准动态 SQL 表达式，数据库标识取自当前 SqlSessionFactory；
 * offset/limit 只接收源码内固定的绑定参数名或整数常量，不接收请求中的 SQL。
 */
public final class DatabaseQuerySql {
    private static final Pattern PARAMETER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");

    private DatabaseQuerySql() {}

    /**
     * 生成分页子句；例如 page(databaseId, "offset", "limit") 保留两个 MyBatis 绑定，
     * page(databaseId, "0", "1") 则固定取首行。未知方言或非法参数名直接失败。
     * 在 ${...} 中只展开本方法生成的模板，实际分页值仍由随后 #{...} 阶段绑定。
     */
    public static String page(String databaseId, String offset, String limit) {
        return DatabaseQueryDialects.forDatabaseId(databaseId).paginationClause(token(offset), token(limit));
    }

    /** 读取保护片段只取决于当前工厂方言；事务与共享/独占锁的业务顺序仍由调用方负责。 */
    public static String readGuard(String databaseId) {
        return DatabaseQueryDialects.forDatabaseId(databaseId).readGuardClause();
    }

    /** 将源码中固定的整数标识列转为文本；只生成表达式，不绑定业务值或执行数据库操作。 */
    public static String integerIdentifierText(String databaseId, String column) {
        return DatabaseQueryDialects.forDatabaseId(databaseId).integerIdentifierText(column);
    }

    private static String token(String parameterOrConstant) {
        if (parameterOrConstant != null && INTEGER.matcher(parameterOrConstant).matches()) return parameterOrConstant;
        if (parameterOrConstant != null && PARAMETER.matcher(parameterOrConstant).matches()) return "#{" + parameterOrConstant + "}";
        throw new IllegalArgumentException("分页片段只接受固定参数名或非负整数常量");
    }
}
