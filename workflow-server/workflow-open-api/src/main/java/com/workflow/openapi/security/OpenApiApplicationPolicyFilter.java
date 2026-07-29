package com.workflow.openapi.security;

import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.core.error.RateLimitExceededException;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.web.OpenRequestTrace;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class OpenApiApplicationPolicyFilter
        extends OncePerRequestFilter {

    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationClientNetworkPolicy networkPolicy;
    private final OpenIntegrationClientAddressResolver addressResolver;
    private final IntegrationRateLimitService rateLimitService;
    private final OpenApiConcurrencyLeaseService concurrencyService;
    private final OpenApiSecurityResponseWriter responseWriter;
    private final SystemAuditPort auditPort;

    public OpenApiApplicationPolicyFilter(
            IntegrationApplicationMapper applicationMapper,
            IntegrationClientNetworkPolicy networkPolicy,
            OpenIntegrationClientAddressResolver addressResolver,
            IntegrationRateLimitService rateLimitService,
            OpenApiConcurrencyLeaseService concurrencyService,
            OpenApiSecurityResponseWriter responseWriter,
            SystemAuditPort auditPort) {
        this.applicationMapper = applicationMapper;
        this.networkPolicy = networkPolicy;
        this.addressResolver = addressResolver;
        this.rateLimitService = rateLimitService;
        this.concurrencyService = concurrencyService;
        this.responseWriter = responseWriter;
        this.auditPort = auditPort;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        String applicationId = null;
        String clientId = null;
        String address = addressResolver.resolve(request);
        OpenApiConcurrencyLeaseService.Lease lease = null;
        try {
            if (!(SecurityContextHolder.getContext()
                    .getAuthentication()
                    instanceof JwtAuthenticationToken token)) {
                responseWriter.write(
                        request,
                        response,
                        401,
                        "INVALID_ACCESS_TOKEN",
                        "Access token is invalid",
                        null);
                return;
            }
            applicationId = token.getToken()
                    .getClaimAsString("application_id");
            clientId = token.getToken().getSubject();
            IntegrationApplicationRecord application =
                    applicationId == null
                            ? null
                            : applicationMapper.selectById(
                            applicationId);
            if (application == null
                    || clientId == null
                    || !clientId.equals(application.getClientId())) {
                responseWriter.write(
                        request,
                        response,
                        401,
                        "INVALID_ACCESS_TOKEN",
                        "Access token is invalid",
                        null);
                return;
            }
            IntegrationClientNetworkPolicy.Decision network =
                    networkPolicy.evaluate(clientId, address);
            if (!applicationId.equals(network.applicationId())
                    || !network.allowed()) {
                responseWriter.write(
                        request,
                        response,
                        403,
                        "SOURCE_ADDRESS_NOT_ALLOWED",
                        "Source address is not allowed",
                        null);
                return;
            }
            try {
                rateLimitService.acquire(
                        "open-api-application",
                        applicationId,
                        application.getRateLimitPerMinute());
            } catch (RateLimitExceededException exception) {
                responseWriter.write(
                        request,
                        response,
                        429,
                        "RATE_LIMIT_EXCEEDED",
                        "Application request quota exceeded",
                        exception.getRetryAfterSeconds());
                return;
            }
            try {
                lease = concurrencyService.acquire(
                        applicationId,
                        application.getMaxConcurrency());
            } catch (OpenApiConcurrencyLeaseService
                    .ConcurrencyRejectedException exception) {
                responseWriter.write(
                        request,
                        response,
                        429,
                        "RATE_LIMIT_EXCEEDED",
                        "Application concurrency quota exceeded",
                        1L);
                return;
            }
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            log.warn(
                    "开放接口策略检查失败: applicationId={}, traceId={}",
                    applicationId,
                    OpenRequestTrace.get(request),
                    exception);
            if (response.isCommitted()) {
                throw exception;
            }
            responseWriter.write(
                    request,
                    response,
                    503,
                    "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                    "Integration capability is temporarily unavailable",
                    null);
        } finally {
            if (lease != null) {
                try {
                    concurrencyService.release(lease);
                } catch (RuntimeException exception) {
                    log.warn(
                            "开放接口并发租约释放失败: applicationId={},"
                                    + " leaseId={}, traceId={}",
                            applicationId,
                            lease.id(),
                            OpenRequestTrace.get(request),
                            exception);
                }
            }
            recordAudit(
                    request,
                    response,
                    applicationId,
                    clientId,
                    address,
                    started);
        }
    }

    private void recordAudit(
            HttpServletRequest request,
            HttpServletResponse response,
            String applicationId,
            String clientId,
            String address,
            long started) {
        if (applicationId == null) {
            return;
        }
        try {
            auditPort.record(SystemAuditEvent.builder()
                    .traceId(OpenRequestTrace.get(request))
                    .module(AuditModule.INTEGRATION)
                    .action("GET".equals(request.getMethod())
                            ? AuditAction.OTHER
                            : AuditAction.START)
                    .operationName("调用开放流程接口")
                    .riskLevel(AuditRiskLevel.MEDIUM)
                    .result(response.getStatus() < 400
                            ? AuditResult.SUCCESS
                            : AuditResult.FAILURE)
                    .required(false)
                    .operatorId(applicationId)
                    .operatorName(clientId)
                    .operatorIp(address)
                    .requestMethod(request.getMethod())
                    .requestPath(request.getRequestURI())
                    .targetType("OPEN_API")
                    .targetId(applicationId)
                    .summary("开放接口响应状态 "
                            + response.getStatus())
                    .errorCode(response.getStatus() < 400
                            ? null
                            : "HTTP_" + response.getStatus())
                    .durationMs(
                            (System.nanoTime() - started)
                                    / 1_000_000)
                    .createdAt(LocalDateTime.now(
                            ZoneOffset.UTC))
                    .build());
        } catch (RuntimeException exception) {
            log.warn(
                    "开放接口审计记录失败: applicationId={}, traceId={}",
                    applicationId,
                    OpenRequestTrace.get(request),
                    exception);
        }
    }
}
