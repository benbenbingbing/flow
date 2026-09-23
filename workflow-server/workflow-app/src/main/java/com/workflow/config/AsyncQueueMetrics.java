package com.workflow.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import com.workflow.core.database.port.DatabaseClockPort;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期读取持久化工作队列的数量与等待时长，供生产告警使用。
 */
@Slf4j
@Component
public class AsyncQueueMetrics {

    private static final String OUTBOX_METRICS_SQL = metricsSql("workflow_outbox_event", "PROCESSING");
    private static final String FLOW_ACTION_METRICS_SQL = metricsSql("process_action_execution", "RUNNING");

    /**
     * 表名和运行状态只来自上面的源码常量。三个聚合子查询分别保留状态过滤，
     * 空队列也各返回一行；最早时间用 MIN 读取，不依赖 LIMIT 或数据库日期差函数。
     */
    private static String metricsSql(String table, String runningStatus) {
        return "SELECT ready.item_count AS ready_count, running.item_count AS running_count,"
                + " dead.item_count AS dead_count, ready.oldest_ready_at"
                + " FROM (SELECT COUNT(*) AS item_count, MIN(create_time) AS oldest_ready_at FROM " + table
                + " WHERE status IN ('PENDING', 'FAILED') AND (next_retry_time IS NULL OR next_retry_time <= ?)) ready"
                + " CROSS JOIN (SELECT COUNT(*) AS item_count FROM " + table + " WHERE status = '" + runningStatus + "') running"
                + " CROSS JOIN (SELECT COUNT(*) AS item_count FROM " + table + " WHERE status = 'DEAD') dead";
    }

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseClockPort clock;
    private final QueueGauges outbox = new QueueGauges();
    private final QueueGauges flowAction = new QueueGauges();

    /** 注册队列指标，运行时间统一从注入的数据库 UTC 时钟读取。 */
    public AsyncQueueMetrics(
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry,
            DatabaseClockPort clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        register(meterRegistry, "outbox", outbox);
        register(meterRegistry, "flow_action", flowAction);
    }

    /** 刷新两张队列的指标；数据库读取失败时保留上一轮完整结果。 */
    @Scheduled(
            fixedDelayString =
                    "${workflow.metrics.queue-refresh-ms:15000}")
    public void refresh() {
        try {
            // 同一轮绑定同一个数据库 UTC 时间，避免节点时钟差和两张队列统计边界不一致。
            LocalDateTime now = clock.utcNow();
            QueueSnapshot outboxSnapshot = snapshot(OUTBOX_METRICS_SQL, now);
            QueueSnapshot flowActionSnapshot = snapshot(FLOW_ACTION_METRICS_SQL, now);
            // 两张队列均读取成功后才发布本轮指标，失败时完整保留上一轮值。
            update(outbox, outboxSnapshot, now);
            update(flowAction, flowActionSnapshot, now);
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

    private QueueSnapshot snapshot(String sql, LocalDateTime now) {
        return jdbcTemplate.queryForObject(sql, (row, index) -> new QueueSnapshot(
                row.getLong("ready_count"), row.getLong("running_count"), row.getLong("dead_count"),
                row.getObject("oldest_ready_at", LocalDateTime.class)), now);
    }

    /** 时间列保存 UTC 墙钟值；在 Java 计算整秒，并把空队列或未来创建时间归零。 */
    private void update(QueueGauges gauges, QueueSnapshot values, LocalDateTime now) {
        gauges.ready.set(values.ready());
        gauges.running.set(values.running());
        gauges.dead.set(values.dead());
        gauges.oldestReadySeconds.set(values.oldestReadyAt() == null ? 0
                : Math.max(0, Duration.between(values.oldestReadyAt(), now).getSeconds()));
    }

    private record QueueSnapshot(long ready, long running, long dead, LocalDateTime oldestReadyAt) {}

    private static final class QueueGauges {

        private final AtomicLong ready = new AtomicLong();
        private final AtomicLong running = new AtomicLong();
        private final AtomicLong dead = new AtomicLong();
        private final AtomicLong oldestReadySeconds =
                new AtomicLong();
    }
}
