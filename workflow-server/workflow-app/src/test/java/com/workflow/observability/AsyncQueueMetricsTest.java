package com.workflow.observability;

import com.workflow.core.database.port.DatabaseClockPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import com.workflow.observability.infrastructure.persistence.mapper.AsyncQueueMetricsMapper;
import com.workflow.observability.infrastructure.persistence.mapper.AsyncQueueMetricsMapper.QueueSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AsyncQueueMetricsTest {
    @Test
    void publishesConsistentSnapshotsAndKeepsBothPreviousValuesAfterPartialReadFailure() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        var clock = mock(DatabaseClockPort.class);
        when(clock.utcNow()).thenReturn(now);
        var failSecond = new AtomicBoolean(false);
        var jdbc = mock(AsyncQueueMetricsMapper.class);
        when(jdbc.outbox(now)).thenAnswer(call -> new QueueSnapshot(failSecond.get() ? 99 : 7, 2, 1, now.minusSeconds(45)));
        when(jdbc.flowAction(now)).thenAnswer(call -> {
            if (failSecond.get()) throw new IllegalStateException("模拟第二张队列不可读");
            return new QueueSnapshot(3, 1, 0, now.minusSeconds(12));
        });
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
