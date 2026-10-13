package com.workflow.database;

import com.workflow.core.database.jdbc.OceanBaseOracleJdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.SqlTypeValue;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 驱动返回空 ParameterMetaData 时，公共 JDBC 调用仍须绑定 NULL，不能只修某个业务 Repository。 */
class OceanBaseOracleJdbcTemplateTest {
    @Test
    void varargsQueriesAndUpdatesBindUnknownNullsWithoutReadingMetadata() throws Exception {
        var f = fixture();
        Object[] args = {null, "unchanged", new SqlParameterValue(SqlTypeValue.TYPE_UNKNOWN, null)};
        assertEquals(1, f.jdbc.update("UPDATE t SET a=?,b=?,c=?", args));
        assertTrue(f.jdbc.queryForList("SELECT a FROM t WHERE a=?", (Object) null).isEmpty());
        verify(f.statement, times(2)).setNull(1, Types.NULL);
        verify(f.statement).setString(2, "unchanged");
        verify(f.statement).setNull(3, Types.NULL);
        verify(f.statement, never()).getParameterMetaData();
        assertNull(args[0]);
        assertEquals(SqlTypeValue.TYPE_UNKNOWN, ((SqlParameterValue) args[2]).getSqlType());
    }

    @Test
    void explicitTypesAndValuesKeepTheirOriginalBinding() throws Exception {
        var f = fixture();
        f.jdbc.update("UPDATE t SET a=?,b=?,c=?,d=?", new Object[]{null, null,
                        new SqlParameterValue(SqlTypeValue.TYPE_UNKNOWN, null), new SqlParameterValue(Types.INTEGER, 7)},
                new int[]{Types.TIMESTAMP, SqlTypeValue.TYPE_UNKNOWN, Types.VARCHAR, Types.INTEGER});
        verify(f.statement).setNull(1, Types.TIMESTAMP);
        verify(f.statement).setNull(2, Types.NULL);
        verify(f.statement).setNull(3, Types.VARCHAR);
        verify(f.statement).setObject(4, 7, Types.INTEGER);
        verify(f.statement, never()).getParameterMetaData();
    }

    @Test
    void bothArrayBatchOverloadsNormalizeNullsAndPreserveCallerArrays() throws Exception {
        var f = fixture();
        Object[] untyped = {null, new SqlParameterValue(SqlTypeValue.TYPE_UNKNOWN, null)};
        Object[] typed = {null, new SqlParameterValue(Types.VARCHAR, null)};
        assertArrayEquals(new int[]{1}, f.jdbc.batchUpdate("INSERT INTO t VALUES (?,?)", List.<Object[]>of(untyped)));
        assertArrayEquals(new int[]{1}, f.jdbc.batchUpdate("INSERT INTO t VALUES (?,?)", List.<Object[]>of(typed),
                new int[]{Types.TIMESTAMP, Types.VARCHAR}));
        verify(f.statement).setNull(1, Types.NULL);
        verify(f.statement).setNull(2, Types.NULL);
        verify(f.statement).setNull(1, Types.TIMESTAMP);
        verify(f.statement).setNull(2, Types.VARCHAR);
        verify(f.statement, never()).getParameterMetaData();
        assertNull(untyped[0]); assertNull(typed[0]);
    }

    private Fixture fixture() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var metadata = mock(DatabaseMetaData.class);
        var rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.supportsBatchUpdates()).thenReturn(true);
        when(statement.executeUpdate()).thenReturn(1);
        when(statement.executeBatch()).thenReturn(new int[]{1});
        when(statement.executeQuery()).thenReturn(rows);
        return new Fixture(new OceanBaseOracleJdbcTemplate(source), statement);
    }

    private record Fixture(OceanBaseOracleJdbcTemplate jdbc, PreparedStatement statement) { }
}
