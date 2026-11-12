package com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.io.*;
import java.sql.*;

/** 原文用字符流读写，统一覆盖 TEXT/LONGTEXT/CLOB，避免 Oracle 大于 4000 字符时隐式 VARCHAR 绑定失败。 */
public class IdentityRawJsonTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String value, JdbcType jdbcType) throws SQLException {
        ps.setCharacterStream(i, new StringReader(value), value.length());
    }
    @Override public String getNullableResult(ResultSet rs, String name) throws SQLException { return read(rs.getCharacterStream(name)); }
    @Override public String getNullableResult(ResultSet rs, int index) throws SQLException { return read(rs.getCharacterStream(index)); }
    @Override public String getNullableResult(CallableStatement cs, int index) throws SQLException { return read(cs.getCharacterStream(index)); }

    private String read(Reader reader) throws SQLException {
        if (reader == null) return null;
        try (reader) {
            var text = new StringWriter();
            reader.transferTo(text);
            return text.toString();
        } catch (IOException e) { throw new SQLException("无法读取来源原文", e); }
    }
}
