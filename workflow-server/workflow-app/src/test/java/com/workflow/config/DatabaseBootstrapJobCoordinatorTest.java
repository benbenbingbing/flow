package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseBootstrapJobCoordinatorTest {

    @Test
    void skipsActionWhenRequiredVersionWasCompleted() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(),
                        eq(Integer.class),
                        eq("catalog")))
                .thenReturn(2);
        DatabaseBootstrapJobCoordinator coordinator =
                new DatabaseBootstrapJobCoordinator(jdbcTemplate);
        @SuppressWarnings("unchecked")
        Supplier<String> action = mock(Supplier.class);

        Optional<String> result =
                coordinator.executeOnce("catalog", 2, action);

        assertEquals(Optional.empty(), result);
        verify(action, never()).get();
    }

    @Test
    void executesAndRecordsIncompleteVersion() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(),
                        eq(Integer.class),
                        eq("catalog")))
                .thenReturn(1);
        when(jdbcTemplate.update(
                        anyString(),
                        eq(2),
                        anyString(),
                        eq("catalog")))
                .thenReturn(1);
        DatabaseBootstrapJobCoordinator coordinator =
                new DatabaseBootstrapJobCoordinator(jdbcTemplate);

        Optional<String> result =
                coordinator.executeOnce(
                        "catalog",
                        2,
                        () -> "completed");

        assertEquals(Optional.of("completed"), result);
        verify(jdbcTemplate).update(
                anyString(),
                eq(2),
                anyString(),
                eq("catalog"));
    }

    @Test
    void rejectsInvalidJobIdentityAndVersion() {
        DatabaseBootstrapJobCoordinator coordinator =
                new DatabaseBootstrapJobCoordinator(
                        mock(JdbcTemplate.class));

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
