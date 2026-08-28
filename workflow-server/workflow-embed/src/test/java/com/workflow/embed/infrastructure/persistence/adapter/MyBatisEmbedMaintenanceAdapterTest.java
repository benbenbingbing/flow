package com.workflow.embed.infrastructure.persistence.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.embed.application.port.EmbedMaintenancePort.CounterCursor;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedMaintenanceMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterObservationRow;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class MyBatisEmbedMaintenanceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");

    @Test
    void mapsCounterPagesAndUsesEmptyStartCursor() {
        EmbedMaintenanceMapper mapper = mock(EmbedMaintenanceMapper.class);
        when(mapper.inspectStoredCounterPage("", "", 20)).thenReturn(List.of(
                new EmbedSessionCounterObservationRow("grant-1", "user-1", 2, 1)));
        MyBatisEmbedMaintenanceAdapter adapter = new MyBatisEmbedMaintenanceAdapter(mapper);

        var page = adapter.inspectStoredCounterPage(null, 20);

        assertEquals(1, page.size());
        assertEquals(new CounterCursor("grant-1", "user-1"), page.get(0).cursor());
        assertEquals(2, page.get(0).storedCount());
        assertEquals(1, page.get(0).actualCount());
    }

    @Test
    void translatesPersistenceFailuresWithoutLeakingDatabaseDetails() {
        EmbedMaintenanceMapper mapper = mock(EmbedMaintenanceMapper.class);
        when(mapper.deleteExpiredAssertionReplays(LocalDateTime.parse("2026-08-28T08:00"), 10))
                .thenThrow(new DataAccessResourceFailureException("secret jdbc url"));
        MyBatisEmbedMaintenanceAdapter adapter = new MyBatisEmbedMaintenanceAdapter(mapper);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.deleteExpiredAssertionReplays(NOW, 10));

        assertEquals("Embed maintenance persistence is unavailable", error.getMessage());
    }

    @Test
    void validatesBatchBeforeExecutingSql() {
        EmbedMaintenanceMapper mapper = mock(EmbedMaintenanceMapper.class);
        MyBatisEmbedMaintenanceAdapter adapter = new MyBatisEmbedMaintenanceAdapter(mapper);

        assertThrows(IllegalArgumentException.class,
                () -> adapter.expireIssuedLaunches(NOW, 0));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.expireIssuedLaunches(NOW, 1001));
    }

    @Test
    void writeAndReadMethodsUseIndependentSmallTransactions() {
        for (Method method : MyBatisEmbedMaintenanceAdapter.class.getMethods()) {
            if (method.getDeclaringClass() != MyBatisEmbedMaintenanceAdapter.class) {
                continue;
            }
            Transactional transaction = method.getAnnotation(Transactional.class);
            assertTrue(transaction != null, method.getName());
            assertEquals(Propagation.REQUIRES_NEW, transaction.propagation(), method.getName());
            boolean expectedReadOnly = method.getName().startsWith("inspect");
            assertEquals(expectedReadOnly, transaction.readOnly(), method.getName());
        }
    }

    @Test
    void terminalCleanupPassesUtcCutoffAndLimitExactly() {
        EmbedMaintenanceMapper mapper = mock(EmbedMaintenanceMapper.class);
        MyBatisEmbedMaintenanceAdapter adapter = new MyBatisEmbedMaintenanceAdapter(mapper);

        adapter.deleteTerminalSessions(NOW, 25);

        verify(mapper).deleteTerminalSessions(LocalDateTime.parse("2026-08-28T08:00"), 25);
    }
}
