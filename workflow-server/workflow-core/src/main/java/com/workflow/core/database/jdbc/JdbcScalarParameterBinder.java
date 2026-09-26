package com.workflow.core.database.jdbc;

import com.workflow.integration.database.api.sql.DatabaseScalarValues;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;

/** 本系统通用 JDBC 写入器的参数绑定；不包装数据源，也不改变第三方引擎的绑定行为。 */
final class JdbcScalarParameterBinder {
    /**
     * 初始化JDBC标量参数{@code binder}，保存构造参数供后续方法使用。
     */
    private JdbcScalarParameterBinder() {}

    /**
     * 布尔参数按业务存储约定绑定为整数，其余值交给 Spring 保留既有类型与 NULL 处理。
     *
     * @param statement {@code statement}，作为 {@code StatementCreatorUtils.setParameterValue} 的输入影响后续处理
     * @param index 索引，作为 {@code StatementCreatorUtils.setParameterValue} 的输入影响后续处理
     * @param value 待处理绑定的原始输入，结果供调用方继续使用
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof Boolean flag) {
            StatementCreatorUtils.setParameterValue(statement, index, Types.INTEGER,
                    DatabaseScalarValues.numericBoolean(flag));
        } else {
            StatementCreatorUtils.setParameterValue(statement, index, SqlTypeValue.TYPE_UNKNOWN, value);
        }
    }
}
