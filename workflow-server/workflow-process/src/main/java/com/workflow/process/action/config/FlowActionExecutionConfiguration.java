package com.workflow.process.action.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 封装流程动作执行配置相关能力和状态；供同一业务流程的后续处理使用。
 */
@Configuration
public class FlowActionExecutionConfiguration {

    /**
     * 处理流程动作任务执行器，并将结果传给后续步骤。
     *
     * @param concurrency {@code concurrency}，作为 {@code Math.max} 的输入影响后续处理
     * @param queueCapacity 队列{@code capacity}，作为 {@code executor.setQueueCapacity} 的输入影响后续处理
     * @return 处理后的流程动作任务执行器结果，供调用方继续处理
     */
    @Bean(name = "flowActionTaskExecutor", defaultCandidate = false)
    Executor flowActionTaskExecutor(
            @Value("${workflow.flow-action.executor.concurrency:4}")
            int concurrency,
            @Value("${workflow.flow-action.executor.queue-capacity:100}")
            int queueCapacity) {
        int threads = Math.max(1, concurrency);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setThreadNamePrefix("flow-action-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 处理流程动作心跳{@code scheduler}，并将结果传给后续步骤。
     *
     * @return 处理后的流程动作心跳{@code scheduler}结果，供调用方继续处理
     */
    @Bean(name = "flowActionHeartbeatScheduler", defaultCandidate = false)
    ThreadPoolTaskScheduler flowActionHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("flow-action-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
