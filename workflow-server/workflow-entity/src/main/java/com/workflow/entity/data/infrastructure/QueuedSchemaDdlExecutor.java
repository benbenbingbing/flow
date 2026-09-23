package com.workflow.entity.data.infrastructure;

import com.workflow.entity.data.infrastructure.schema.SchemaChangeQueuePort;
import com.workflow.entity.data.application.SchemaDdlExecutor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 发布业务等待结果；入队及独立提交由实体基础设施执行，worker 归结构发布进程。 */
@Component
@ConditionalOnProperty(name = "workflow.schema-publisher.mode", havingValue = "queue")
public class QueuedSchemaDdlExecutor implements SchemaDdlExecutor {
    private final SchemaChangeQueuePort queue;
    private final Duration timeout;
    private final Duration pollInterval;

    public QueuedSchemaDdlExecutor(SchemaChangeQueuePort queue,
            @Value("${workflow.schema-publisher.wait-timeout:120s}") Duration timeout,
            @Value("${workflow.schema-publisher.poll-interval:500ms}") Duration pollInterval) {
        requirePositive(timeout, "wait-timeout");
        requirePositive(pollInterval, "poll-interval");
        this.queue = queue;
        this.timeout = timeout;
        this.pollInterval = pollInterval;
    }

    @Override
    public void execute(String ddl) {
        String requestId = queue.enqueue(ddl);
        long started = System.nanoTime();
        while (System.nanoTime() - started < timeout.toNanos()) {
            var state = queue.state(requestId).orElseThrow(
                    () -> new IllegalStateException("Schema change request disappeared"));
            if ("APPLIED".equals(state.status())) return;
            if ("FAILED".equals(state.status())) {
                throw new IllegalStateException("Schema change failed: " +
                        (StringUtils.hasText(state.error()) ? state.error() : "schema worker reported an unknown error"));
            }
            try {
                Thread.sleep(Math.max(1, pollInterval.toMillis()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for schema worker", exception);
            }
        }
        throw new IllegalStateException("Timed out waiting for schema worker");
    }

    private static void requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(label + " must be positive");
        }
        // 超出 nanoTime 可表示持续时间的配置在启动时失败。
        value.toNanos();
    }
}
