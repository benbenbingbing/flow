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

public class PortableLargeTextTypeHandler extends BaseTypeHandler<String> {

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
            statement.setCharacterStream(index, new java.io.StringReader(parameter));
        } else {
            statement.setString(index, parameter);
        }
    }

    @Override
    public String getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return read(resultSet.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return read(resultSet.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(
            CallableStatement statement,
            int columnIndex) throws SQLException {
        return read(statement.getObject(columnIndex));
    }

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
