package com.workflow.entity.data;

import com.workflow.entity.data.infrastructure.JdbcSchemaDdlExecutor;
import com.workflow.integration.database.schema.dialect.MySqlSchemaDdlDialect;
import com.workflow.core.database.port.DatabaseConnections;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcSchemaDdlExecutorTest {
    @Test
    void rejectsUnsafeSqlBeforeRequestingAConnection() {
        var connections = mock(DatabaseConnections.class);
        var source = mock(DataSource.class);
        when(connections.schema()).thenReturn(source);
        var executor = new JdbcSchemaDdlExecutor(connections, new MySqlSchemaDdlDialect());
        assertThrows(IllegalArgumentException.class, () -> executor.execute("SELECT * FROM sys_user"));
        assertThrows(IllegalArgumentException.class, () -> executor.execute("CREATE TABLE biz_x (id INT); DROP TABLE sys_user"));
        verifyNoInteractions(source);
    }

    @Test
    void closesDedicatedConnectionWhenDdlFails() throws Exception {
        var connections = mock(DatabaseConnections.class);
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        when(connections.schema()).thenReturn(source);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("CREATE TABLE biz_x (id INT)")).thenThrow(new SQLException("failure", "42000"));
        var executor = new JdbcSchemaDdlExecutor(connections, new MySqlSchemaDdlDialect());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> executor.execute("CREATE TABLE biz_x (id INT)"));
        verify(connection).close();
        verify(statement).close();
        verify(connections, never()).application();
    }
}
