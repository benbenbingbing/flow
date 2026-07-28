package com.workflow.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class FreshInstallFlywayConfigurationTest {

    private final FlywayMigrationStrategy strategy =
            new FreshInstallFlywayConfiguration().forwardOnlyMigrationStrategy();

    @Test
    void appliesPendingForwardMigrations() {
        Flyway flyway = mock(Flyway.class);

        strategy.migrate(flyway);

        verify(flyway).migrate();
    }

    @Test
    void doesNotRejectPendingMigrationsWithStandaloneValidation() {
        Flyway flyway = mock(Flyway.class);

        strategy.migrate(flyway);

        verify(flyway).migrate();
        verifyNoMoreInteractions(flyway);
    }
}
