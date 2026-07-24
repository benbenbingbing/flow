package com.workflow.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.io.Reader;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 跨数据库的大文本字段类型处理器。
 *
 * <p>针对 CLOB/NCLOB/LONGVARCHAR/LONGNVARCHAR 等大文本类型提供可移植的读写支持，
 * 解决不同 JDBC 驱动返回类型不一致（Clob、NClob 或纯字符串）导致的兼容性问题。
 * 适用于 MySQL、Oracle 等异构数据库环境下的大字段读取。
 */
public class PortableLargeTextTypeHandler extends BaseTypeHandler<String> {

    /**
     * 设置非空参数：将字符串写入 PreparedStatement 指定位置。
     *
     * <p>当目标字段为大文本类型时以字符流方式写入，避免大字段直接 setString 的内存限制；
     * 其余类型按普通字符串写入。
     *
     * @param statement PreparedStatement 对象
     * @param index 参数位置索引（从 1 开始）
     * @param parameter 待写入的字符串值
     * @param jdbcType 字段 JDBC 类型
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            String parameter,
            JdbcType jdbcType) throws SQLException {
        if (jdbcType == JdbcType.CLOB
                || jdbcType == JdbcType.NCLOB
                || jdbcType == JdbcType.LONGVARCHAR
                || jdbcType == JdbcType.LONGNVARCHAR) {
            // 大文本类型以字符流写入，规避大字段直接 setString 的长度限制
            statement.setCharacterStream(index, new java.io.StringReader(parameter));
        } else {
            statement.setString(index, parameter);
        }
    }

    /**
     * 按列名从结果集中读取可空字符串结果。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 读取到的字符串，可能为 null
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public String getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return read(resultSet.getObject(columnName));
    }

    /**
     * 按列索引从结果集中读取可空字符串结果。
     *
     * @param resultSet 结果集
     * @param columnIndex 列索引（从 1 开始）
     * @return 读取到的字符串，可能为 null
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return read(resultSet.getObject(columnIndex));
    }

    /**
     * 从 CallableStatement 中按列索引读取可空字符串结果（存储过程场景）。
     *
     * @param statement CallableStatement 对象
     * @param columnIndex 列索引（从 1 开始）
     * @return 读取到的字符串，可能为 null
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public String getNullableResult(
            CallableStatement statement,
            int columnIndex) throws SQLException {
        return read(statement.getObject(columnIndex));
    }

    /**
     * 将 JDBC 返回的对象统一转换为字符串。
     *
     * <p>兼容 Clob、NClob 以及普通字符串类型，分别走对应的字符流或直接转字符串。
     *
     * @param value JDBC 返回的原始值
     * @return 转换后的字符串，可能为 null
     * @throws SQLException 读取大文本字符流失败时抛出
     */
    private String read(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof Clob clob) {
            return read(clob.getCharacterStream());
        }
        if (value instanceof NClob nclob) {
            return read(nclob.getCharacterStream());
        }
        return String.valueOf(value);
    }

    /**
     * 从字符流中读取完整字符串内容。
     *
     * @param reader 输入字符流
     * @return 读取到的完整字符串
     * @throws SQLException 读取过程中发生 IO 异常时包装抛出
     */
    private String read(Reader reader) throws SQLException {
        try (reader) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[8_192];
            int length;
            while ((length = reader.read(buffer)) >= 0) {
                result.append(buffer, 0, length);
            }
            return result.toString();
        } catch (java.io.IOException exception) {
            throw new SQLException("读取大文本字段失败", exception);
        }
    }
}
