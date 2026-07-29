package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AsyncQueueMetricsTest {

    @Test
    void publishesDurableQueueCountsAndAge() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForMap(anyString()))
                .thenReturn(Map.of(
                        "ready_count", 7L,
                        "running_count", 2L,
                        "dead_count", 1L,
                        "oldest_ready_seconds", 45L))
                .thenReturn(Map.of(
                        "ready_count", 3L,
                        "running_count", 1L,
                        "dead_count", 0L,
                        "oldest_ready_seconds", 12L));
        SimpleMeterRegistry registry =
                new SimpleMeterRegistry();
        AsyncQueueMetrics metrics =
                new AsyncQueueMetrics(jdbcTemplate, registry);

        metrics.refresh();

        assertGauge(
                registry,
                "workflow.queue.items",
                "outbox",
                "ready",
                7);
        assertGauge(
                registry,
                "workflow.queue.items",
                "flow_action",
                "running",
                1);
        assertEquals(
                45,
                registry.get(
                                "workflow.queue.oldest.ready.seconds")
                        .tag("queue", "outbox")
                        .gauge()
                        .value());
    }

    private void assertGauge(
            SimpleMeterRegistry registry,
            String name,
            String queue,
            String state,
            double expected) {
        assertEquals(
                expected,
                registry.get(name)
                        .tag("queue", queue)
                        .tag("state", state)
                        .gauge()
                        .value());
    }
}
