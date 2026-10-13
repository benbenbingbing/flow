package com.workflow.core.database.mybatis;

import org.apache.ibatis.type.JdbcType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 服务端查询编译器产生的 SQL 与绑定值；不接受客户端提供的完整 SQL。
 * 保留方言生成的参数顺序和显式 JDBC 类型，Mapper 仍通过预编译参数执行。
 */
public record ParameterizedSql(String sql, List<Parameter> parameters) {
    public ParameterizedSql {
        Objects.requireNonNull(sql, "SQL 不能为空");
        parameters = List.copyOf(parameters);
    }

    /** 为服务端 SQL 建立绑定参数；null 值也占据一个位置，不得通过 List.of 丢弃。 */
    public static ParameterizedSql of(String sql, Object... values) {
        return of(sql, Arrays.asList(values));
    }

    public static ParameterizedSql of(String sql, List<?> values) {
        List<Parameter> parameters = new ArrayList<>();
        for (Object value : values) parameters.add(value instanceof Parameter parameter ? parameter : new Parameter(value, null));
        return new ParameterizedSql(sql, parameters);
    }

    /** 显式类型用于权限 NULL 文本和跨数据库 CLOB 比较，避免驱动自行推断导致语义变化。 */
    public static Parameter typed(Object value, JdbcType jdbcType) { return new Parameter(value, Objects.requireNonNull(jdbcType)); }
    public record Parameter(Object value, JdbcType jdbcType) { }
}
