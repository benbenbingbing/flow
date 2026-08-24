package com.workflow.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreshInstallFlywayConfigurationTest {

    private final FlywayMigrationStrategy strategy =
            new FreshInstallFlywayConfiguration()
                    .forwardOnlyMigrationStrategy();

    @Test
    void appliesPendingForwardMigrations() throws Exception {
        Flyway flyway = mock(Flyway.class);
        Configuration configuration = mock(Configuration.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        when(flyway.getConfiguration()).thenReturn(configuration);
        when(configuration.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(
                nullable(String.class),
                nullable(String.class),
                anyString(),
                any(String[].class))).thenReturn(tables);
        when(tables.next()).thenReturn(false);

        strategy.migrate(flyway);

        verify(flyway).migrate();
    }

    @Test
    void rejectsMissingDatasourceBeforeApplyingAnyMigration() {
        Flyway flyway = mock(Flyway.class);
        Configuration configuration = mock(Configuration.class);
        when(flyway.getConfiguration()).thenReturn(configuration);

        assertThrows(IllegalStateException.class, () -> strategy.migrate(flyway));

        verify(flyway, never()).migrate();
    }
}
