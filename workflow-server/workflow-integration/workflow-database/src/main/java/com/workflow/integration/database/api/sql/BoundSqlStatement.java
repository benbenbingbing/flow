package com.workflow.integration.database.api.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JDBC 语句与按问号顺序排列的值必须成对传递，防止方言改变占位符顺序后绑定错误。
 *
 * @param sql SQL，保存在对象中供后续校验、查询或展示
 * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
 */
public record BoundSqlStatement(String sql, List<Object> parameters) {
    /**
     * 初始化绑定SQL{@code statement}，保存构造参数供后续方法使用。
     *
     * @param sql SQL，保存在对象中供后续校验、查询或展示
     * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
     */
    public BoundSqlStatement {
        Objects.requireNonNull(sql, "sql");
        // JDBC 参数允许 NULL；List.copyOf 会错误拒绝合法的 SQL NULL 绑定。
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }
}
