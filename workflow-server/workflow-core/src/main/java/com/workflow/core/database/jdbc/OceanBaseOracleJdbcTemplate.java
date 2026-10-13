package com.workflow.core.database.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.SqlTypeValue;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.List;

/**
 * OceanBase Oracle 服务端预编译协议在执行前不提供 ParameterMetaData。
 * 为未声明类型的空参数使用标准 SQL NULL，避免 Spring 为推断类型读取空元数据；
 * 只由 OB Oracle 应用装配，显式类型、非空参数及调用方自行绑定的 Statement 不受影响。
 */
public final class OceanBaseOracleJdbcTemplate extends JdbcTemplate {
    public OceanBaseOracleJdbcTemplate(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected PreparedStatementSetter newArgPreparedStatementSetter(Object[] args) {
        return super.newArgPreparedStatementSetter(typedNulls(args, null));
    }

    @Override
    protected PreparedStatementSetter newArgTypePreparedStatementSetter(Object[] args, int[] argTypes) {
        return super.newArgTypePreparedStatementSetter(typedNulls(args, argTypes), argTypes);
    }

    /**
     * Spring 的数组批处理绕过上面两个工厂方法，必须在此处理每行。
     * 继续使用 Spring 原有批执行与资源清理机制；不修改调用方持有的参数数组。
     */
    @Override
    public int[] batchUpdate(String sql, List<Object[]> batchArgs, int[] argTypes) {
        return super.batchUpdate(sql, batchArgs.stream().map(args -> typedNulls(args, argTypes)).toList(), argTypes);
    }

    private static Object[] typedNulls(Object[] args, int[] argTypes) {
        if (args == null) return null;
        Object[] result = args;
        for (int index = 0; index < args.length; index++) {
            Object value = args[index];
            int declaredType = argTypes != null && index < argTypes.length ? argTypes[index] : SqlTypeValue.TYPE_UNKNOWN;
            boolean unknownWrappedNull = value instanceof SqlParameterValue parameter
                    && parameter.getValue() == null && parameter.getSqlType() == SqlTypeValue.TYPE_UNKNOWN;
            if ((value == null && declaredType == SqlTypeValue.TYPE_UNKNOWN) || unknownWrappedNull) {
                if (result == args) result = args.clone();
                result[index] = new SqlParameterValue(declaredType == SqlTypeValue.TYPE_UNKNOWN ? Types.NULL : declaredType, null);
            }
        }
        return result;
    }
}
