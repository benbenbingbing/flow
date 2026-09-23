package com.workflow.database;

import com.workflow.core.database.lock.JdbcDatabaseLock;
import com.workflow.integration.database.api.DatabaseVendor;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MySqlDatabaseLockTest {
    @Test
    void acquisitionFailureAndNullResultAlwaysCloseTheConnection() throws Exception {
        for (boolean nullResult : new boolean[]{false, true}) {
            var source = mock(DataSource.class);
            var connection = mock(Connection.class);
            var statement = mock(PreparedStatement.class);
            var result = mock(ResultSet.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            if (nullResult) {
                when(statement.executeQuery()).thenReturn(result);
                when(result.next()).thenReturn(true);
                when(result.wasNull()).thenReturn(true);
            } else when(statement.executeQuery()).thenThrow(new SQLException("network", "08006"));
            assertThrows(IllegalStateException.class, () -> new JdbcDatabaseLock(source, DatabaseVendor.MYSQL).tryAcquire("flow:entity", "id"));
            verify(connection).close();
        }
    }

    @Test
    void busyIsNotAnErrorAndDoesNotLeakAConnection() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(0);
        assertTrue(new JdbcDatabaseLock(source, DatabaseVendor.MYSQL).tryAcquire("flow:entity", "id").isEmpty());
        verify(connection).close();
    }

    @Test
    void releaseFailureStillClosesPhysicalSessionAndSecondCloseDoesNothing() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var acquire = mock(PreparedStatement.class);
        var release = mock(PreparedStatement.class);
        var result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("GET_LOCK"))).thenReturn(acquire);
        when(connection.prepareStatement(contains("RELEASE_LOCK"))).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        when(release.executeQuery()).thenThrow(new SQLException("disconnected", "08006"));
        var handle = new JdbcDatabaseLock(source, DatabaseVendor.MYSQL).tryAcquire("flow:entity", "id").orElseThrow();
        assertThrows(IllegalStateException.class, handle::close);
        assertDoesNotThrow(handle::close);
        verify(connection).close();
        verify(release).executeQuery();
    }
}
