package com.workflow.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import com.workflow.core.database.port.DatabaseClockPort;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import com.workflow.observability.infrastructure.persistence.mapper.AsyncQueueMetricsMapper;
import com.workflow.observability.infrastructure.persistence.mapper.AsyncQueueMetricsMapper.QueueSnapshot;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期读取持久化工作队列的数量与等待时长，供生产告警使用。
 */
@Slf4j
@Component
public class AsyncQueueMetrics {

    private final AsyncQueueMetricsMapper metricsMapper;
    private final DatabaseClockPort clock;
    private final QueueGauges outbox = new QueueGauges();
    private final QueueGauges flowAction = new QueueGauges();

    /**
     * 注册队列指标，运行时间统一从注入的数据库 UTC 时钟读取。
     *
     * @param metricsMapper 队列聚合Mapper依赖，保存到当前对象供后续业务方法调用
     * @param meterRegistry {@code meter}{@code registry}，保存在对象中供后续校验、查询或展示
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    public AsyncQueueMetrics(
            AsyncQueueMetricsMapper metricsMapper,
            MeterRegistry meterRegistry,
            DatabaseClockPort clock) {
        this.metricsMapper = metricsMapper;
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
            QueueSnapshot outboxSnapshot = metricsMapper.outbox(now);
            QueueSnapshot flowActionSnapshot = metricsMapper.flowAction(now);
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
