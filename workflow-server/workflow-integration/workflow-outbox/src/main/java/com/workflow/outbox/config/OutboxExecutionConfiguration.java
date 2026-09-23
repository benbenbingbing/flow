package com.workflow.outbox.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 封装待发送事件执行配置相关能力和状态；供同一业务流程的后续处理使用。
 */
@Configuration
public class OutboxExecutionConfiguration {

    /**
     * 处理待发送事件任务执行器，并将结果传给后续步骤。
     *
     * @param concurrency {@code concurrency}，作为 {@code Math.max} 的输入影响后续处理
     * @param queueCapacity 队列{@code capacity}，作为 {@code executor.setQueueCapacity} 的输入影响后续处理
     * @return 处理后的待发送事件任务执行器结果，供调用方继续处理
     */
    @Bean(name = "outboxTaskExecutor", defaultCandidate = false)
    Executor outboxTaskExecutor(
            @Value("${workflow.outbox.executor.concurrency:4}") int concurrency,
            @Value("${workflow.outbox.executor.queue-capacity:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = Math.max(1, concurrency);
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setThreadNamePrefix("outbox-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 处理待发送事件心跳{@code scheduler}，并将结果传给后续步骤。
     *
     * @return 处理后的待发送事件心跳{@code scheduler}结果，供调用方继续处理
     */
    @Bean(name = "outboxHeartbeatScheduler", defaultCandidate = false)
    ThreadPoolTaskScheduler outboxHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("outbox-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
