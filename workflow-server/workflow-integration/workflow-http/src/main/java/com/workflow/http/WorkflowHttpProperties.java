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
    /** 全局和每个目标的在途上限；满载快速失败，防止慢目标占满请求线程。 */
    private int maxConcurrentRequests = 64;
    private int maxConcurrentPerTarget = 8;
    /** 连接池按地址与访问策略隔离，此上限同时约束缓存客户端和空闲连接数量。 */
    private int maxCachedPools = 32;
    private int poolIdleSeconds = 60;
    private int connectionTtlSeconds = 300;
    /** 连续网络/服务端失败达到阈值后短暂拒绝该目标，只放行一个恢复探测。 */
    private int circuitFailureThreshold = 5;
    private int circuitOpenSeconds = 30;
}
