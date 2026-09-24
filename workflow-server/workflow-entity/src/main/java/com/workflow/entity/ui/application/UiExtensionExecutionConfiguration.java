package com.workflow.entity.ui.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/** 慢扩展不能占满审批、通知等应用共享执行器，队列满时立即按接口失败策略返回。 */
@Configuration(proxyBeanMethods = false)
public class UiExtensionExecutionConfiguration {
    /**
     * 创建扩展接口专用的有界线程池，通过 {@code @Qualifier} 注入扩展执行服务。
     * 声明为非默认候选，避免 Spring Boot 因发现该执行器而不再创建
     * Flowable 等组件依赖的 {@code applicationTaskExecutor}。
     *
     * @param threads 工作线程数，限制在 1 到 64 之间
     * @param queueCapacity 等待队列容量，限制在 0 到 256 之间
     * @return 由 Spring 管理初始化和关闭的专用线程池，容量耗尽时立即拒绝任务
     */
    @Bean(name = "uiExtensionTaskExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor uiExtensionTaskExecutor(
            @Value("${workflow.ui-extension.execution-threads:8}") int threads,
            @Value("${workflow.ui-extension.execution-queue-capacity:32}") int queueCapacity) {
        var executor = new ThreadPoolTaskExecutor();
        int capacity = Math.max(1, Math.min(64, threads));
        executor.setCorePoolSize(capacity);
        executor.setMaxPoolSize(capacity);
        executor.setQueueCapacity(Math.max(0, Math.min(256, queueCapacity)));
        executor.setThreadNamePrefix("ui-extension-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
