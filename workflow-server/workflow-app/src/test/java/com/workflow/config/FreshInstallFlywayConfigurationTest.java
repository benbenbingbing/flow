package com.workflow.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreshInstallFlywayConfigurationTest {

    private final CurrentBaselineSchemaUpgrade baselineSchemaUpgrade =
            mock(CurrentBaselineSchemaUpgrade.class);
    private final FlywayMigrationStrategy strategy =
            new FreshInstallFlywayConfiguration()
                    .freshInstallOnlyMigrationStrategy(
                            baselineSchemaUpgrade);

    @Test
    void migratesAnEmptyDatabase() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infoService = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(infoService);
        when(infoService.current()).thenReturn(null);

        strategy.migrate(flyway);

        verify(baselineSchemaUpgrade, never()).apply(flyway);
        verify(flyway).migrate();
    }

    @Test
    void acceptsAnExistingV001Database() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infoService = mock(MigrationInfoService.class);
        MigrationInfo current = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(infoService);
        when(infoService.current()).thenReturn(current);
        when(current.getVersion()).thenReturn(MigrationVersion.fromVersion("1"));

        strategy.migrate(flyway);

        verify(baselineSchemaUpgrade).apply(flyway);
        verify(flyway).repair();
        verify(flyway).migrate();
    }

    @Test
    void rejectsAHistoricalDatabaseBeforeMigrationRuns() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infoService = mock(MigrationInfoService.class);
        MigrationInfo current = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(infoService);
        when(infoService.current()).thenReturn(current);
        when(current.getVersion()).thenReturn(MigrationVersion.fromVersion("42"));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> strategy.migrate(flyway));

        assertTrue(exception.getMessage().contains("历史数据库版本 42"));
        assertTrue(exception.getMessage().contains("V001 基线"));
        assertTrue(exception.getMessage().contains("一次性基线接管"));
        verify(baselineSchemaUpgrade, never()).apply(flyway);
        verify(flyway, never()).repair();
        verify(flyway, never()).migrate();
    }
}
