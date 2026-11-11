package com.workflow.dbmigrator.runner;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.exception.FlywayBlockStatementExecutionException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 校验兼容范围严格受限，并且只有等价 SQL 真正成功后才阻止原语句重复执行。 */
class MySqlV062CompatibilityCallbackTest {
    private final MySqlV062CompatibilityCallback callback = new MySqlV062CompatibilityCallback();

    @Test
    void matchesOnlyOriginalV062StatementAndKeepsOtherStatementsUntouched() throws Exception {
        Context context = context();
        assertTrue(callback.supports(Event.BEFORE_EACH_MIGRATE_STATEMENT, context));
        assertFalse(callback.supports(Event.AFTER_EACH_MIGRATE_STATEMENT, context));
        when(context.getMigrationInfo().getVersion()).thenReturn(MigrationVersion.fromVersion("63"));
        assertFalse(callback.supports(Event.BEFORE_EACH_MIGRATE_STATEMENT, context));
        when(context.getMigrationInfo().getVersion()).thenReturn(MigrationVersion.fromVersion("62"));
        when(context.getStatement().getSql()).thenReturn("DELETE FROM sys_role_menu");
        assertFalse(callback.supports(Event.BEFORE_EACH_MIGRATE_STATEMENT, context));
    }

    @Test
    void replacesStatementOnlyAfterSuccessfulMysqlExecution() throws Exception {
        Context context = context();
        Connection connection = mysqlConnection(context);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        assertThrows(FlywayBlockStatementExecutionException.class,
                () -> callback.handle(Event.BEFORE_EACH_MIGRATE_STATEMENT, context));
        verify(statement).executeUpdate(argThat(sql -> sql.contains("OR removed_root.menu_id = menu.parent_id")
                && sql.indexOf("tmp_scope_inventory_menu_root") == sql.lastIndexOf("tmp_scope_inventory_menu_root")));
        verify(statement).close();
    }

    @Test
    void propagatesDatabaseFailureAndDoesNotActivateForOtherDatabases() throws Exception {
        Context context = context();
        Connection connection = mysqlConnection(context);
        when(connection.createStatement()).thenThrow(new SQLException("permission denied"));
        FlywayException failure = assertThrows(FlywayException.class,
                () -> callback.handle(Event.BEFORE_EACH_MIGRATE_STATEMENT, context));
        assertFalse(failure instanceof FlywayBlockStatementExecutionException);
        assertInstanceOf(SQLException.class, failure.getCause());
        when(connection.getMetaData().getDatabaseProductName()).thenReturn("PostgreSQL");
        assertDoesNotThrow(() -> callback.handle(Event.BEFORE_EACH_MIGRATE_STATEMENT, context));
        verify(connection, times(1)).createStatement();
    }

    private Connection mysqlConnection(Context context) throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        return connection;
    }

    /** 从不可变的原脚本提取失败语句，测试不维护另一份历史 SQL。 */
    private Context context() throws Exception {
        Context context = mock(Context.class);
        MigrationInfo migration = mock(MigrationInfo.class);
        var statement = mock(org.flywaydb.core.api.callback.Statement.class);
        when(context.getMigrationInfo()).thenReturn(migration);
        when(context.getStatement()).thenReturn(statement);
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion("62"));
        when(migration.getScript()).thenReturn("V062__remove_entity_list_scope_inventory.sql");
        try (var input = getClass().getResourceAsStream("/db/migration/V062__remove_entity_list_scope_inventory.sql")) {
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int start = script.indexOf("DELETE role_grant");
            when(statement.getSql()).thenReturn(script.substring(start, script.indexOf(';', start) + 1));
        }
        return context;
    }
}
