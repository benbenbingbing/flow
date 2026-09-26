package com.workflow.database;

import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 故障注入证明恢复失败不能被业务的 DuplicateKeyException 捕获分支误当成幂等成功。 */
class MySqlWriteAttemptFailureTest {
    private DataSource boundSource;

    @AfterEach void clean() {
        if (boundSource != null) TransactionSynchronizationManager.unbindResource(boundSource);
        TransactionSynchronizationManager.clear();
    }

    @Test void failedSavepointStopsBeforeCallback() throws Exception {
        var f = fixture();
        when(f.connection.setSavepoint()).thenThrow(new SQLException("no savepoint", "0A000"));
        var callback = mock(java.util.function.IntSupplier.class);
        assertThrows(DataAccessException.class, () -> f.attempt.execute(callback));
        verifyNoInteractions(callback);
    }

    @Test void failedRecoveryIsNotReportedAsDuplicate() throws Exception {
        var f = fixture();
        doThrow(new SQLException("lost savepoint", "3B001")).when(f.connection).rollback(f.point);
        assertThrows(DataAccessResourceFailureException.class, () -> f.attempt.execute(() -> { throw duplicate(); }));
        verify(f.connection, never()).commit();
        verify(f.connection, never()).rollback();
        verify(f.connection, never()).releaseSavepoint(f.point);
    }

    @Test void failedReleaseIsNotReportedAsDuplicate() throws Exception {
        var f = fixture();
        doThrow(new SQLException("connection lost", "08006")).when(f.connection).releaseSavepoint(f.point);
        assertThrows(DataAccessResourceFailureException.class, () -> f.attempt.execute(() -> { throw duplicate(); }));
    }

    @Test void translatesVendorDuplicateOnlyAfterSuccessfulRecovery() throws Exception {
        var f = fixture();
        var raw = new DataIntegrityViolationException("write", new SQLException("unique", "23000", 1062));
        assertThrows(DuplicateKeyException.class, () -> f.attempt.execute(() -> { throw raw; }));
        var ordered = inOrder(f.connection);
        ordered.verify(f.connection).setSavepoint();
        ordered.verify(f.connection).rollback(f.point);
        ordered.verify(f.connection).releaseSavepoint(f.point);
    }

    @Test void mixedSqlErrorChainCannotBecomeReplay() throws Exception {
        var f = fixture();
        var sql = new SQLException("unique", "23000", 1062);
        sql.setNextException(new SQLException("connection lost", "08006"));
        assertThrows(DataAccessResourceFailureException.class, () -> f.attempt.execute(() -> {
            throw new DuplicateKeyException("duplicate and connection failure", sql);
        }));
    }

    @Test void suppressedSqlFailureCannotBeReclassifiedAsDuplicateAfterRestore() throws Exception {
        var f = fixture();
        var sql = new SQLException("unique", "23000", 1062);
        sql.addSuppressed(new SQLException("close failed", "08006"));
        var translator = new com.workflow.core.database.jdbc.DatabaseSQLExceptionTranslator(
                new com.workflow.core.database.jdbc.DatabaseExceptionClassifier(DatabaseDialects.errors(DatabaseVendor.MYSQL)));
        var translated = translator.translate("insert", "sql", sql);
        assertFalse(translated instanceof DuplicateKeyException);
        assertSame(translated, assertThrows(DataAccessException.class,
                () -> f.attempt.execute(() -> { throw translated; })));
    }

    @Test void unsupportedReleaseDoesNotCommitOrFailSuccessfulWrite() throws Exception {
        var f = fixture();
        doThrow(new SQLFeatureNotSupportedException()).when(f.connection).releaseSavepoint(f.point);
        assertEquals(1, f.attempt.execute(() -> 1));
        verify(f.connection, never()).commit();
        verify(f.connection, never()).rollback(f.point);
    }

    @Test void anotherDataSourceTransactionCannotExecuteCallback() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        try { when(source.getConnection()).thenReturn(connection); }
        catch (SQLException error) { throw new AssertionError(error); }
        var attempt = new JdbcWriteAttempt(new JdbcTemplate(source), DatabaseDialects.insert(DatabaseVendor.MYSQL));
        var callback = mock(java.util.function.IntSupplier.class);
        assertThrows(IllegalStateException.class, () -> attempt.execute(callback));
        verifyNoInteractions(callback);
    }

    private Fixture fixture() throws SQLException {
        boundSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var point = mock(Savepoint.class);
        when(connection.setSavepoint()).thenReturn(point);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(boundSource, new ConnectionHolder(connection));
        return new Fixture(connection, point,
                new JdbcWriteAttempt(new JdbcTemplate(boundSource), DatabaseDialects.insert(DatabaseVendor.MYSQL)));
    }

    private DuplicateKeyException duplicate() {
        return new DuplicateKeyException("unique", new SQLException("unique", "23000", 1062));
    }

    private record Fixture(Connection connection, Savepoint point, JdbcWriteAttempt attempt) {}
}
