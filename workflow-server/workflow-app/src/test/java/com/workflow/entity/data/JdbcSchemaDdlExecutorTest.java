package com.workflow.entity.data;

import com.workflow.entity.data.infrastructure.JdbcSchemaDdlExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcSchemaDdlExecutorTest {

    @Test
    void rejectsMissingDedicatedCredentials() {
        assertThrows(
                IllegalStateException.class,
                () -> new JdbcSchemaDdlExecutor("", "schema_user", "schema_password"));
        assertThrows(
                IllegalStateException.class,
                () -> new JdbcSchemaDdlExecutor("jdbc:mysql://localhost/workflow", "", "schema_password"));
        assertThrows(
                IllegalStateException.class,
                () -> new JdbcSchemaDdlExecutor("jdbc:mysql://localhost/workflow", "schema_user", " "));
    }

    @Test
    void acceptsCompleteDedicatedCredentialsWithoutConnectingEagerly() {
        assertDoesNotThrow(() -> new JdbcSchemaDdlExecutor(
                "jdbc:mysql://localhost/workflow",
                "schema_user",
                "schema_password"));
    }
}
