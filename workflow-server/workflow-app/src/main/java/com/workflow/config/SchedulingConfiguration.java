package com.workflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Keeps background workers out of one-shot bootstrap Jobs.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "workflow.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SchedulingConfiguration {
}
