package com.workflow.openapi.webhook.delivery;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(
        name = "workflow.open-api.webhook.enabled",
        havingValue = "true")
public class WebhookDeliveryExecutionConfiguration {

    @Bean(name = "webhookDeliveryExecutor", defaultCandidate = false)
    Executor webhookDeliveryExecutor(
            @Value("${workflow.open-api.webhook.worker.concurrency:4}")
            int concurrency,
            @Value("${workflow.open-api.webhook.worker.queue-capacity:200}")
            int queueCapacity) {
        int boundedConcurrency = Math.max(
                1,
                Math.min(concurrency, 32));
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(boundedConcurrency);
        executor.setMaxPoolSize(boundedConcurrency);
        executor.setQueueCapacity(Math.max(
                1,
                Math.min(queueCapacity, 5000)));
        executor.setThreadNamePrefix("webhook-delivery-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean(name = "webhookHeartbeatScheduler", defaultCandidate = false)
    ThreadPoolTaskScheduler webhookHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("webhook-heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }
}
