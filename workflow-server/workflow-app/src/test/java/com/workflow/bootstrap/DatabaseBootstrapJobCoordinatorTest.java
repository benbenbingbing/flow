package com.workflow.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import com.workflow.core.database.jdbc.JdbcLockedRow;
import org.junit.jupiter.api.Test;
import com.workflow.bootstrap.infrastructure.persistence.mapper.BootstrapJobMapper;

class DatabaseBootstrapJobCoordinatorTest {
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-22T02:00:00");

    @Test
    void skipsActionWhenRequiredVersionWasCompleted() {
        BootstrapJobMapper mapper = mock(BootstrapJobMapper.class);
        when(mapper.completedVersion("catalog"))
                .thenReturn(2);
        DatabaseBootstrapJobCoordinator coordinator =
                new DatabaseBootstrapJobCoordinator(mapper, mock(JdbcLockedRow.class), () -> NOW);
        @SuppressWarnings("unchecked")
        Supplier<String> action = mock(Supplier.class);

        Optional<String> result =
                coordinator.executeOnce("catalog", 2, action);

        assertEquals(Optional.empty(), result);
        verify(action, never()).get();
    }

    @Test
    void executesAndRecordsIncompleteVersion() {
        BootstrapJobMapper mapper = mock(BootstrapJobMapper.class);
        when(mapper.completedVersion("catalog"))
                .thenReturn(1);
        when(mapper.complete(eq("catalog"), eq(2), anyString(), eq(NOW)))
                .thenReturn(1);
        DatabaseBootstrapJobCoordinator coordinator =
                new DatabaseBootstrapJobCoordinator(mapper, mock(JdbcLockedRow.class), () -> NOW);

        Optional<String> result =
                coordinator.executeOnce(
                        "catalog",
                        2,
                        () -> "completed");

        assertEquals(Optional.of("completed"), result);
        verify(mapper).complete(eq("catalog"), eq(2), anyString(), eq(NOW));
    }

    @Test
    void rejectsInvalidJobIdentityAndVersion() {
        DatabaseBootstrapJobCoordinator coordinator =
                new DatabaseBootstrapJobCoordinator(
                        mock(BootstrapJobMapper.class), mock(JdbcLockedRow.class), () -> NOW);

        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.executeOnce(
                        " ",
                        1,
                        () -> true));
        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.executeOnce(
                        "catalog",
                        0,
                        () -> true));
    }
}
