package com.workflow.admin.auth.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Distributed login throttling thresholds.
 */
@Data
@Component
@ConfigurationProperties(prefix = "workflow.security.login-throttle")
public class LoginThrottleProperties {

    private int accountMaxFailures = 5;
    private int clientMaxFailures = 30;
    private int windowSeconds = 900;
    private int blockSeconds = 900;
}
