package com.workflow.database;

import com.workflow.core.database.JdbcIdempotentInsert;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import java.sql.*;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 保存点无法恢复或错误链含连接异常时，幂等执行器必须向上失败，不能报告已存在。 */
class MySqlIdempotentInsertFailureTest {
    @Test
    void statementCloseFailureSuppressedByDuplicateIsNotSwallowed() throws Exception {
        var f = new Fixture();
        when(f.statement.executeUpdate()).thenThrow(new SQLException("duplicate", "23000", 1062));
        doThrow(new SQLException("close failed", "08006")).when(f.statement).close();
        var failure = assertThrows(DataAccessException.class,
                () -> f.insert.insertIfAbsent("biz_claim", Map.of("id", "same")));
        assertFalse(failure instanceof org.springframework.dao.DuplicateKeyException);
        verify(f.connection).rollback(f.point);
    }

    @Test
    void failedRollbackDoesNotBecomeDuplicateAndNeverCommits() throws Exception {
        var f = new Fixture();
        var duplicate = new SQLException("duplicate", "23000", 1062);
        when(f.statement.executeUpdate()).thenThrow(duplicate);
        doThrow(new SQLException("savepoint lost", "3B001")).when(f.connection).rollback(f.point);
        assertThrows(DataAccessException.class, () -> f.insert.insertIfAbsent("biz_claim", Map.of("id", "same")));
        assertEquals(1, duplicate.getSuppressed().length);
        verify(f.connection, never()).commit();
        verify(f.connection, never()).rollback();
        verify(f.connection, never()).releaseSavepoint(f.point);
    }

    @Test
    void connectionFailureInDuplicateChainIsNotSwallowed() throws Exception {
        var f = new Fixture();
        var duplicate = new SQLException("duplicate", "23000", 1062);
        duplicate.setNextException(new SQLException("connection lost", "08006"));
        when(f.statement.executeUpdate()).thenThrow(duplicate);
        assertThrows(DataAccessException.class, () -> f.insert.insertIfAbsent("biz_claim", Map.of("id", "same")));
        verify(f.connection).rollback(f.point);
    }

    @Test
    void failedSavepointStopsBeforeTheWrite() throws Exception {
        var f = new Fixture();
        when(f.connection.setSavepoint()).thenThrow(new SQLException("savepoint unsupported", "0A000"));
        assertThrows(DataAccessException.class, () -> f.insert.insertIfAbsent("biz_claim", Map.of("id", "same")));
        verify(f.statement, never()).executeUpdate();
    }

    private static final class Fixture {
        final Connection connection = mock(Connection.class);
        final PreparedStatement statement = mock(PreparedStatement.class);
        final Savepoint point = mock(Savepoint.class);
        final JdbcIdempotentInsert insert;

        Fixture() throws Exception {
            var source = mock(DataSource.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(false);
            when(connection.setSavepoint()).thenReturn(point);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            var jdbc = new JdbcTemplate(source);
            jdbc.setExceptionTranslator(new com.workflow.core.database.DatabaseSQLExceptionTranslator(
                    new com.workflow.core.database.DatabaseExceptionClassifier(DatabaseDialects.errors(DatabaseVendor.MYSQL))));
            insert = new JdbcIdempotentInsert(jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL));
        }
    }
}
