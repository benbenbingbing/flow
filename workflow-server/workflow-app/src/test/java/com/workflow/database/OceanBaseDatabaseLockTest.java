package com.workflow.database;

import com.workflow.core.database.lock.JdbcDatabaseLock;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.runtime.DatabaseLockPlan;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 锁竞争和资源失败必须分开处理，避免数据库异常被误报为正常的并发抢锁失败。 */
class OceanBaseDatabaseLockTest {
    @Test
    void oceanBaseUsesIndependentTransactionWhileMysqlKeepsLegacyNamedLock() {
        var ob = DatabaseDialects.runtime(DatabaseVendor.OCEANBASE_MYSQL).lockPlan("flow:entity", "entity-id");
        var mysql = DatabaseDialects.runtime(DatabaseVendor.MYSQL).lockPlan("flow:entity", "entity-id");
        assertEquals(DatabaseLockPlan.Invocation.TEXT_TRANSACTION_INSERT, ob.invocation());
        assertTrue(ob.rollbackOnRelease());
        assertEquals(mysql.key(), ob.key());
        assertTrue(mysql.acquireSql().contains("GET_LOCK"));
        assertEquals(1205, ob.busyErrorCode());
        assertTrue(ob.sessionInitSql().contains("SET SESSION ob_trx_lock_timeout = 0"));
    }

    @Test
    void onlyNoWaitLockConflictReturnsEmptyAndAlwaysRollsBackAndCloses() throws Exception {
        var f = fixture();
        when(f.acquire.executeUpdate()).thenThrow(new SQLException("NO WAIT", "40001", 1205));
        assertTrue(f.locks.tryAcquire("flow:entity", "id").isEmpty());
        verify(f.connection).rollback();
        verify(f.connection).close();
        verify(f.connection, never()).commit();
    }

    @Test
    void schemaNetworkTransactionAndUnexpectedDuplicateFailuresAreNotBusy() throws Exception {
        for (int error : new int[]{1146, 1142, 1062, 6210, 6224, 0}) {
            var f = fixture();
            when(f.acquire.executeUpdate()).thenThrow(new SQLException("failure", "HY000", error));
            assertThrows(IllegalStateException.class, () -> f.locks.tryAcquire("flow:entity", "id"));
            verify(f.connection).rollback();
            verify(f.connection).close();
        }
    }

    @Test
    void sessionInitializationFailureAlsoClosesWithoutPretendingItIsBusy() throws Exception {
        var f = fixture();
        when(f.initialize.execute(anyString())).thenThrow(new SQLException("session failure", "40001", 1205));
        assertThrows(IllegalStateException.class, () -> f.locks.tryAcquire("flow:entity", "id"));
        verify(f.connection).close();
        verify(f.acquire, never()).executeUpdate();
    }

    @Test
    void closeChecksOwnershipRollsBackAndClosesOnlyOnce() throws Exception {
        var f = fixture();
        var handle = f.locks.tryAcquire("flow:entity", "id").orElseThrow();
        verify(f.connection).setAutoCommit(false);
        verify(f.connection, never()).commit();
        verify(f.connection, never()).rollback();
        handle.close();
        handle.close();
        verify(f.check).executeQuery();
        verify(f.connection).rollback();
        verify(f.connection).close();
    }

    @Test
    void lostTransactionMarkerIsReportedButStillRollsBackAndCloses() throws Exception {
        var f = fixture();
        when(f.result.getInt(1)).thenReturn(0);
        var handle = f.locks.tryAcquire("flow:entity", "id").orElseThrow();
        assertThrows(IllegalStateException.class, handle::close);
        verify(f.connection).rollback();
        verify(f.connection).close();
    }

    @Test
    void rollbackAndCloseFailuresArePreservedAndAbortPhysicalConnection() throws Exception {
        var f = fixture();
        when(f.acquire.executeUpdate()).thenThrow(new SQLException("missing table", "42S02", 1146));
        doThrow(new SQLException("rollback failed")).when(f.connection).rollback();
        doThrow(new SQLException("close failed")).when(f.connection).close();
        var failure = assertThrows(IllegalStateException.class, () -> f.locks.tryAcquire("flow:entity", "id"));
        assertEquals(1146, ((SQLException) failure.getCause()).getErrorCode());
        assertEquals(1, failure.getCause().getSuppressed().length);
        verify(f.connection).abort(any());
    }

    private Fixture fixture() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var initialize = mock(Statement.class);
        var acquire = mock(PreparedStatement.class);
        var check = mock(PreparedStatement.class);
        var result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(initialize);
        when(connection.prepareStatement(startsWith("INSERT"))).thenReturn(acquire);
        when(connection.prepareStatement(startsWith("SELECT"))).thenReturn(check);
        when(acquire.executeUpdate()).thenReturn(1);
        when(check.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        return new Fixture(new JdbcDatabaseLock(source, DatabaseVendor.OCEANBASE_MYSQL), connection,
                initialize, acquire, check, result);
    }

    private record Fixture(JdbcDatabaseLock locks, Connection connection, Statement initialize,
                           PreparedStatement acquire, PreparedStatement check, ResultSet result) { }
}
