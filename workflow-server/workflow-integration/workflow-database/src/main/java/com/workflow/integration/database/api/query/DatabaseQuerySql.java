package com.workflow.integration.database.api.query;

import java.util.regex.Pattern;

/**
 * 注解/XML 查询使用的显式方言片段入口，不解析或重写已有 SQL。
 * 调用方式为 MyBatis 标准动态 SQL 表达式，数据库标识取自当前 SqlSessionFactory；
 * offset/limit 只接收源码内固定的绑定参数名或整数常量，不接收请求中的 SQL。
 */
public final class DatabaseQuerySql {
    private static final Pattern PARAMETER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");

    /**
     * 初始化数据库查询SQL，保存构造参数供后续方法使用。
     */
    private DatabaseQuerySql() {}

    /**
     * 生成分页子句；例如 page(databaseId, "offset", "limit") 保留两个 MyBatis 绑定，
     * page(databaseId, "0", "1") 则固定取首行。未知方言或非法参数名直接失败。
     * 在 ${...} 中只展开本方法生成的模板，实际分页值仍由随后 #{...} 阶段绑定。
     *
     * @param databaseId 数据库ID，后续用于分页查询数据库查询SQL时定位或关联目标
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 分页查询后的数据库查询SQL文本，供调用方比较或展示
     */
    public static String page(String databaseId, String offset, String limit) {
        return DatabaseQueryDialects.forDatabaseId(databaseId).paginationClause(token(offset), token(limit));
    }

    /**
     * 读取保护片段只取决于当前工厂方言；事务与共享/独占锁的业务顺序仍由调用方负责。
     *
     * @param databaseId 数据库ID，后续用于读取保护时定位或关联目标
     * @return 读取后的保护文本，供调用方比较或展示
     */
    public static String readGuard(String databaseId) {
        return DatabaseQueryDialects.forDatabaseId(databaseId).readGuardClause();
    }

    /**
     * 将源码中固定的整数标识列转为文本；只生成表达式，不绑定业务值或执行数据库操作。
     *
     * @param databaseId 数据库ID，后续用于处理整数标识符文本时定位或关联目标
     * @param column 列，供本方法处理整数标识符文本时使用
     * @return 处理后的整数标识符文本文本，供调用方比较或展示
     */
    public static String integerIdentifierText(String databaseId, String column) {
        return DatabaseQueryDialects.forDatabaseId(databaseId).integerIdentifierText(column);
    }

    /**
     * 生成令牌文本，供后续匹配或展示。
     *
     * @param parameterOrConstant 参数或{@code constant}，供本方法处理令牌时使用
     * @return 处理后的令牌文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static String token(String parameterOrConstant) {
        if (parameterOrConstant != null && INTEGER.matcher(parameterOrConstant).matches()) return parameterOrConstant;
        if (parameterOrConstant != null && PARAMETER.matcher(parameterOrConstant).matches()) return "#{" + parameterOrConstant + "}";
        throw new IllegalArgumentException("分页片段只接受固定参数名或非负整数常量");
    }
}
