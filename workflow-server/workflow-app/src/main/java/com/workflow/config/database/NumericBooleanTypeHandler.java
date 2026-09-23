package com.workflow.config.database;

import com.workflow.integration.database.api.DatabaseScalarValues;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.BooleanTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** 应用业务布尔列统一写入 0/1；读取沿用 JDBC Boolean 映射，保留 SQL NULL。 */
public final class NumericBooleanTypeHandler extends BooleanTypeHandler {
    /**
     * 已知为 Boolean 的空参数显式使用数值 NULL，避免 MyBatis 默认 OTHER 在部分驱动中被拒绝。
     * 本处理器仅注册到应用 MyBatis 工厂；Flowable 使用自身的工厂和类型处理器。
     */
    @Override
    public void setParameter(PreparedStatement statement, int index, Boolean value, JdbcType jdbcType)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            setNonNullParameter(statement, index, value, jdbcType);
        }
    }

    /** 使用数值绑定匹配全部方言的业务列定义，避免 PostgreSQL 把 Boolean 参数当作原生布尔类型。 */
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Boolean value, JdbcType jdbcType)
            throws SQLException {
        statement.setInt(index, DatabaseScalarValues.numericBoolean(value));
    }
}
