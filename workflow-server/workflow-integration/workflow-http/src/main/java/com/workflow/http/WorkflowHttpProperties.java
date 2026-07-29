package com.workflow.http;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Guardrails for outbound HTTP workflow tasks.
 */
@Data
@Component
@ConfigurationProperties(prefix = "workflow.http")
public class WorkflowHttpProperties {

    private List<String> allowedHosts = new ArrayList<>();
    private boolean allowHttp;
    private boolean allowPrivateAddresses;
    private int connectTimeoutSeconds = 5;
    private int maxRequestTimeoutSeconds = 30;
    private int maxRequestBytes = 262_144;
    private int maxResponseBytes = 1_048_576;
}
