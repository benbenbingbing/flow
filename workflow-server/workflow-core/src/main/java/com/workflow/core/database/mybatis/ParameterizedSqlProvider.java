package com.workflow.core.database.mybatis;

import java.util.Map;

/** 将已校验的服务端 SQL 编译为 MyBatis 绑定表达式，不把业务值拼入 SQL。 */
public final class ParameterizedSqlProvider {
    private ParameterizedSqlProvider() { }

    /**
     * 仅替换 SQL 词法层面的问号。字符串/标识符、注释中的问号不能消耗参数；
     * 数量不一致或未闭合引号直接失败，防止值错位后误读或误写记录。
     */
    public static String sql(Map<String, Object> arguments) {
        ParameterizedSql statement = (ParameterizedSql) arguments.get("statement");
        String source = statement.sql();
        if (source.contains("#{") || source.contains("${")) throw new IllegalArgumentException("编译 SQL 不允许 MyBatis 文本或参数插值");
        StringBuilder result = new StringBuilder();
        int parameter = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '\'' || current == '"' || current == '`' || current == '[') {
                char end = current == '[' ? ']' : current;
                int start = index;
                boolean closed = false;
                while (++index < source.length()) {
                    if (source.charAt(index) != end) continue;
                    if (index + 1 < source.length() && source.charAt(index + 1) == end) { index++; continue; }
                    closed = true; break;
                }
                if (!closed) throw new IllegalArgumentException("SQL 引号未闭合");
                result.append(source, start, index + 1);
            } else if (current == '-' && index + 1 < source.length() && source.charAt(index + 1) == '-') {
                int start = index;
                while (index + 1 < source.length() && source.charAt(index + 1) != '\n') index++;
                result.append(source, start, index + 1);
            } else if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
                int end = source.indexOf("*/", index + 2);
                if (end < 0) throw new IllegalArgumentException("SQL 注释未闭合");
                result.append(source, index, end + 2); index = end + 1;
            } else if (current == '?') {
                if (parameter >= statement.parameters().size()) throw new IllegalArgumentException("SQL 绑定参数不足");
                var value = statement.parameters().get(parameter);
                result.append("#{statement.parameters[").append(parameter++).append("].value");
                if (value.jdbcType() != null) {
                    result.append(",jdbcType=").append(value.jdbcType().name());
                    // Object 参数在 CLOB 上必须显式选择字符串处理器，不能用 setObject 交给驱动猜测。
                    if (value.jdbcType() == org.apache.ibatis.type.JdbcType.CLOB)
                        result.append(",javaType=java.lang.String,typeHandler=org.apache.ibatis.type.ClobTypeHandler");
                    if (value.jdbcType() == org.apache.ibatis.type.JdbcType.NCLOB)
                        result.append(",javaType=java.lang.String,typeHandler=org.apache.ibatis.type.NClobTypeHandler");
                }
                result.append('}');
            } else result.append(current);
        }
        if (parameter != statement.parameters().size()) throw new IllegalArgumentException("SQL 绑定参数过多");
        return result.toString();
    }
}
