package com.workflow.core.database.jdbc;

import java.sql.PreparedStatement;
import java.sql.Types;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

/** OB Oracle 预编译不会提前提供参数元数据，业务空值必须直接绑定标准 SQL NULL。 */
class JdbcScalarParameterBinderTest {
    @Test
    void bindsNullWithoutRequestingUnsupportedParameterMetadata() throws Exception {
        var statement = mock(PreparedStatement.class);
        JdbcScalarParameterBinder.bind(statement, 2, null);
        verify(statement).setNull(2, Types.NULL);
        verify(statement, never()).getParameterMetaData();
    }
}
