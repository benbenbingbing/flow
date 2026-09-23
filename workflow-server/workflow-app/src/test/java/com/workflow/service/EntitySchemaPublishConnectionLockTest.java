package com.workflow.service;

import com.workflow.entity.definition.application.EntitySchemaPublishLock;
import org.junit.jupiter.api.Test;
import com.workflow.core.database.lock.JdbcDatabaseLock;
import com.workflow.integration.database.api.DatabaseVendor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MySQL 命名锁必须在同一物理连接获取和释放。 */
class EntitySchemaPublishConnectionLockTest {

    @Test
    void releasesOnTheConnectionThatAcquiredTheLock() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet acquireResult = mock(ResultSet.class);
        ResultSet releaseResult = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("GET_LOCK"))).thenReturn(acquire);
        when(connection.prepareStatement(contains("RELEASE_LOCK"))).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(acquireResult);
        when(release.executeQuery()).thenReturn(releaseResult);
        when(acquireResult.next()).thenReturn(true);
        when(releaseResult.next()).thenReturn(true);
        when(acquireResult.getInt(1)).thenReturn(1);
        when(releaseResult.getInt(1)).thenReturn(1);
        EntitySchemaPublishLock lock = new EntitySchemaPublishLock(new JdbcDatabaseLock(dataSource, DatabaseVendor.MYSQL));

        assertTrue(lock.tryAcquire("entity-1"));
        lock.release("entity-1");

        verify(connection).close();
        verify(connection).prepareStatement(contains("GET_LOCK"));
        verify(connection).prepareStatement(contains("RELEASE_LOCK"));
    }
}
