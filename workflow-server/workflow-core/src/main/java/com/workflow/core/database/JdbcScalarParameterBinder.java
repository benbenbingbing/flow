package com.workflow.core.database;

import com.workflow.integration.database.api.DatabaseScalarValues;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;

/** 本系统通用 JDBC 写入器的参数绑定；不包装数据源，也不改变第三方引擎的绑定行为。 */
final class JdbcScalarParameterBinder {
    private JdbcScalarParameterBinder() {}

    /** 布尔参数按业务存储约定绑定为整数，其余值交给 Spring 保留既有类型与 NULL 处理。 */
    static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof Boolean flag) {
            StatementCreatorUtils.setParameterValue(statement, index, Types.INTEGER,
                    DatabaseScalarValues.numericBoolean(flag));
        } else {
            StatementCreatorUtils.setParameterValue(statement, index, SqlTypeValue.TYPE_UNKNOWN, value);
        }
    }
}
