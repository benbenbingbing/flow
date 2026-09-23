package com.workflow.config;

import com.workflow.core.database.port.DatabaseClockPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AsyncQueueMetricsTest {
    @Test
    void publishesConsistentSnapshotsAndKeepsBothPreviousValuesAfterPartialReadFailure() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        var clock = mock(DatabaseClockPort.class);
        when(clock.utcNow()).thenReturn(now);
        var failSecond = new AtomicBoolean(false);
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public <T> T queryForObject(String sql, RowMapper<T> mapper, Object... args) {
                assertEquals(now, args[0]);
                boolean outbox = sql.contains("workflow_outbox_event");
                if (!outbox && failSecond.get()) throw new IllegalStateException("模拟第二张队列不可读");
                var row = mock(java.sql.ResultSet.class);
                try {
                    when(row.getLong("ready_count")).thenReturn(failSecond.get() ? 99L : outbox ? 7L : 3L);
                    when(row.getLong("running_count")).thenReturn(outbox ? 2L : 1L);
                    when(row.getLong("dead_count")).thenReturn(outbox ? 1L : 0L);
                    when(row.getObject("oldest_ready_at", LocalDateTime.class)).thenReturn(now.minusSeconds(outbox ? 45 : 12));
                    return mapper.mapRow(row, 0);
                } catch (java.sql.SQLException error) { throw new AssertionError(error); }
            }
        };
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new AsyncQueueMetrics(jdbc, registry, clock);
            metrics.refresh();
            assertGauge(registry, "outbox", "ready", 7);
            assertGauge(registry, "flow_action", "running", 1);
            assertEquals(45, registry.get("workflow.queue.oldest.ready.seconds").tag("queue", "outbox").gauge().value());
            failSecond.set(true);
            metrics.refresh();
            assertGauge(registry, "outbox", "ready", 7);
            assertGauge(registry, "flow_action", "ready", 3);
        } finally {
            registry.close();
        }
    }

    private static void assertGauge(SimpleMeterRegistry registry, String queue, String state, double expected) {
        assertEquals(expected, registry.get("workflow.queue.items").tag("queue", queue).tag("state", state).gauge().value());
    }
}
