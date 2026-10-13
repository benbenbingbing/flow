package com.workflow.core.database.jdbc;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 只允许已确认的“不支持释放”降级；真实连接失败和混合错误必须保留。 */
class JdbcSavepointsTest {
    @Test void oceanBaseUnsupportedReleaseKeepsTheOuterTransaction() throws Exception {
        var connection = oceanBase();
        var point = mock(Savepoint.class);
        doThrow(unsupported()).when(connection).releaseSavepoint(point);
        assertDoesNotThrow(() -> JdbcSavepoints.release(connection, point));
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).rollback(any(Savepoint.class));
    }

    @Test void standardUnsupportedReleaseAlsoDefersCleanupToTheTransaction() throws Exception {
        var connection = mock(Connection.class);
        var point = mock(Savepoint.class);
        doThrow(new SQLFeatureNotSupportedException()).when(connection).releaseSavepoint(point);
        assertDoesNotThrow(() -> JdbcSavepoints.release(connection, point));
        verify(connection, never()).getMetaData();
        verify(connection, never()).commit();
    }

    @Test void matchingCodeFromAnotherDriverIsNotIgnored() throws Exception {
        var connection = oceanBase();
        when(connection.getMetaData().getDriverName()).thenReturn("Another JDBC Driver");
        var point = mock(Savepoint.class);
        var failure = unsupported();
        doThrow(failure).when(connection).releaseSavepoint(point);
        assertSame(failure, assertThrows(SQLException.class, () -> JdbcSavepoints.release(connection, point)));
    }

    @Test void connectionStateAndDifferentVendorCodeRemainFailures() throws Exception {
        for (var failure : new SQLException[]{new SQLTransientConnectionException("lost", "08006", 17023),
                new SQLException("unknown", "99999", 17024)}) {
            var connection = oceanBase();
            var point = mock(Savepoint.class);
            doThrow(failure).when(connection).releaseSavepoint(point);
            assertSame(failure, assertThrows(SQLException.class, () -> JdbcSavepoints.release(connection, point)));
        }
    }

    @Test void additionalFailuresCannotBeHiddenByAnUnsupportedCode() throws Exception {
        for (int chain = 0; chain < 3; chain++) {
            var connection = oceanBase();
            var point = mock(Savepoint.class);
            var failure = unsupported();
            var lost = new SQLException("lost", "08006");
            if (chain == 0) failure.setNextException(lost);
            else if (chain == 1) failure.addSuppressed(lost);
            else failure.initCause(lost);
            doThrow(failure).when(connection).releaseSavepoint(point);
            assertSame(failure, assertThrows(SQLException.class, () -> JdbcSavepoints.release(connection, point)));
        }
    }

    @Test void successfulReleaseAndMissingSavepointNeedNoDriverInspection() throws Exception {
        var connection = mock(Connection.class);
        var point = mock(Savepoint.class);
        JdbcSavepoints.release(connection, null);
        verifyNoInteractions(connection);
        JdbcSavepoints.release(connection, point);
        verify(connection).releaseSavepoint(point);
        verify(connection, never()).getMetaData();
    }

    private Connection oceanBase() throws SQLException {
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDriverName()).thenReturn("OceanBase Connector/J");
        return connection;
    }

    private SQLException unsupported() {
        return new SQLTransientConnectionException("releaseSavepoint is not supported", "99999", 17023);
    }
}
