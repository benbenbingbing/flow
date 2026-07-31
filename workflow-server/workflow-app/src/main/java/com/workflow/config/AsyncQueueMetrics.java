package com.workflow.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically snapshots durable worker queues for production alerting.
 */
@Slf4j
@Component
public class AsyncQueueMetrics {

    private static final String OUTBOX_METRICS_SQL = """
            SELECT
              (SELECT COUNT(*)
                 FROM workflow_outbox_event
                WHERE status IN ('PENDING', 'FAILED')
                  AND (next_retry_time IS NULL
                       OR next_retry_time <= UTC_TIMESTAMP(6)))
                AS ready_count,
              (SELECT COUNT(*)
                 FROM workflow_outbox_event
                WHERE status = 'PROCESSING')
                AS running_count,
              (SELECT COUNT(*)
                 FROM workflow_outbox_event
                WHERE status = 'DEAD')
                AS dead_count,
              COALESCE(
                (SELECT TIMESTAMPDIFF(
                          SECOND,
                          create_time,
                          UTC_TIMESTAMP(6))
                   FROM workflow_outbox_event
                  WHERE status IN ('PENDING', 'FAILED')
                    AND (next_retry_time IS NULL
                         OR next_retry_time <= UTC_TIMESTAMP(6))
                  ORDER BY create_time
                  LIMIT 1),
                0) AS oldest_ready_seconds
            """;
    private static final String FLOW_ACTION_METRICS_SQL = """
            SELECT
              (SELECT COUNT(*)
                 FROM process_action_execution
                WHERE status IN ('PENDING', 'FAILED')
                  AND (next_retry_time IS NULL
                       OR next_retry_time <= UTC_TIMESTAMP(6)))
                AS ready_count,
              (SELECT COUNT(*)
                 FROM process_action_execution
                WHERE status = 'RUNNING')
                AS running_count,
              (SELECT COUNT(*)
                 FROM process_action_execution
                WHERE status = 'DEAD')
                AS dead_count,
              COALESCE(
                (SELECT TIMESTAMPDIFF(
                          SECOND,
                          create_time,
                          UTC_TIMESTAMP(6))
                   FROM process_action_execution
                  WHERE status IN ('PENDING', 'FAILED')
                    AND (next_retry_time IS NULL
                         OR next_retry_time <= UTC_TIMESTAMP(6))
                  ORDER BY create_time
                  LIMIT 1),
                0) AS oldest_ready_seconds
            """;

    private final JdbcTemplate jdbcTemplate;
    private final QueueGauges outbox = new QueueGauges();
    private final QueueGauges flowAction = new QueueGauges();

    public AsyncQueueMetrics(
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        register(meterRegistry, "outbox", outbox);
        register(meterRegistry, "flow_action", flowAction);
    }

    @Scheduled(
            fixedDelayString =
                    "${workflow.metrics.queue-refresh-ms:15000}")
    public void refresh() {
        try {
            update(
                    outbox,
                    jdbcTemplate.queryForMap(OUTBOX_METRICS_SQL));
            update(
                    flowAction,
                    jdbcTemplate.queryForMap(
                            FLOW_ACTION_METRICS_SQL));
        } catch (RuntimeException exception) {
            log.warn(
                    "刷新异步队列指标失败，保留上次成功值",
                    exception);
        }
    }

    private void register(
            MeterRegistry registry,
            String queue,
            QueueGauges gauges) {
        gauge(registry, queue, "ready", gauges.ready);
        gauge(registry, queue, "running", gauges.running);
        gauge(registry, queue, "dead", gauges.dead);
        Gauge.builder(
                        "workflow.queue.oldest.ready.seconds",
                        gauges.oldestReadySeconds,
                        AtomicLong::get)
                .tag("queue", queue)
                .description(
                        "Age of the oldest ready durable queue item")
                .register(registry);
    }

    private void gauge(
            MeterRegistry registry,
            String queue,
            String state,
            AtomicLong value) {
        Gauge.builder(
                        "workflow.queue.items",
                        value,
                        AtomicLong::get)
                .tag("queue", queue)
                .tag("state", state)
                .description(
                        "Durable asynchronous queue items by state")
                .register(registry);
    }

    private void update(
            QueueGauges gauges,
            Map<String, Object> values) {
        gauges.ready.set(number(values.get("ready_count")));
        gauges.running.set(number(values.get("running_count")));
        gauges.dead.set(number(values.get("dead_count")));
        gauges.oldestReadySeconds.set(
                Math.max(
                        0,
                        number(values.get(
                                "oldest_ready_seconds"))));
    }

    private long number(Object value) {
        return value instanceof Number number
                ? number.longValue()
                : 0;
    }

    private static final class QueueGauges {

        private final AtomicLong ready = new AtomicLong();
        private final AtomicLong running = new AtomicLong();
        private final AtomicLong dead = new AtomicLong();
        private final AtomicLong oldestReadySeconds =
                new AtomicLong();
    }
}
