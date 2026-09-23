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
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param runningStatus {@code running}状态标识，决定后续指标集合SQL采用的处理分支
     * @return 处理后的指标集合SQL文本，供调用方比较或展示
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

    /**
     * 注册队列指标，运行时间统一从注入的数据库 UTC 时钟读取。
     *
     * @param jdbcTemplate JDBC模板依赖，保存到当前对象供后续业务方法调用
     * @param meterRegistry {@code meter}{@code registry}，保存在对象中供后续校验、查询或展示
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
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

    /**
     * 处理{@code register}，并将结果传给后续步骤。
     *
     * @param registry {@code registry}，作为 {@code gauge} 的输入影响后续处理
     * @param queue 队列，作为 {@code gauge} 的输入影响后续处理
     * @param gauges 指标，作为 {@code gauge} 的输入影响后续处理
     */
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

    /**
     * 处理{@code gauge}，并将结果传给后续步骤。
     *
     * @param registry {@code registry}，供本方法处理{@code gauge}时使用
     * @param queue 队列，作为 {@code Gauge.builder} 的输入影响后续处理
     * @param state 状态标识，决定后续{@code gauge}采用的处理分支
     * @param value 待处理{@code gauge}的原始输入，结果供调用方继续使用
     */
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

    /**
     * 处理快照，并将结果传给后续步骤。
     *
     * @param sql SQL，作为 {@code jdbcTemplate.queryForObject} 的输入影响后续处理
     * @param now 当前时间，供本方法处理快照时使用
     * @return 处理后的快照结果，供调用方继续处理
     */
    private QueueSnapshot snapshot(String sql, LocalDateTime now) {
        return jdbcTemplate.queryForObject(sql, (row, index) -> new QueueSnapshot(
                row.getLong("ready_count"), row.getLong("running_count"), row.getLong("dead_count"),
                row.getObject("oldest_ready_at", LocalDateTime.class)), now);
    }

    /**
     * 时间列保存 UTC 墙钟值；在 Java 计算整秒，并把空队列或未来创建时间归零。
     *
     * @param gauges 指标，供本方法更新{@code async}队列指标集合时使用
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param now 当前时间，供本方法更新{@code async}队列指标集合时使用
     */
    private void update(QueueGauges gauges, QueueSnapshot values, LocalDateTime now) {
        gauges.ready.set(values.ready());
        gauges.running.set(values.running());
        gauges.dead.set(values.dead());
        gauges.oldestReadySeconds.set(values.oldestReadyAt() == null ? 0
                : Math.max(0, Duration.between(values.oldestReadyAt(), now).getSeconds()));
    }

    /**
     * 封装队列快照的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param ready 就绪，保存在对象中供后续校验、查询或展示
     * @param running {@code running}，保存在对象中供后续校验、查询或展示
     * @param dead {@code dead}，保存在对象中供后续校验、查询或展示
     * @param oldestReadyAt {@code oldest}就绪时间，后续用于判断有效期或展示该事件的发生时间
     */
    private record QueueSnapshot(long ready, long running, long dead, LocalDateTime oldestReadyAt) {}

    /**
     * 封装队列指标相关能力和状态；供同一业务流程的后续处理使用。
     */
    private static final class QueueGauges {

        private final AtomicLong ready = new AtomicLong();
        private final AtomicLong running = new AtomicLong();
        private final AtomicLong dead = new AtomicLong();
        private final AtomicLong oldestReadySeconds =
                new AtomicLong();
    }
}
