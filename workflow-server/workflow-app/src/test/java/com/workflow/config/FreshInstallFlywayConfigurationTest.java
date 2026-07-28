package com.workflow.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

class FreshInstallFlywayConfigurationTest {

    private final FlywayMigrationStrategy strategy =
            new FreshInstallFlywayConfiguration().forwardOnlyMigrationStrategy();

    @Test
    void validatesBeforeApplyingForwardMigrations() {
        Flyway flyway = mock(Flyway.class);

        strategy.migrate(flyway);

        var order = inOrder(flyway);
        order.verify(flyway).validate();
        order.verify(flyway).migrate();
    }

    @Test
    void delegatesCompatibilityChecksToFlyway() {
        Flyway flyway = mock(Flyway.class);

        strategy.migrate(flyway);

        verify(flyway).validate();
        verify(flyway).migrate();
    }
}
