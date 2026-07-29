package com.workflow.openapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.core.error.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class TokenEndpointRateLimitFilter
        extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(
            TokenEndpointRateLimitFilter.class);
    private static final int MAXIMUM_AUTHORIZATION_LENGTH = 1024;
    private static final Pattern SAFE_CLIENT_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final IntegrationRateLimitService rateLimitService;
    private final OpenIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final IntegrationClientNetworkPolicy networkPolicy;
    private final OpenIntegrationClientAddressResolver addressResolver;
    private final SystemAuditPort auditPort;
    private final IntegrationCredentialUsageService credentialUsageService;

    public TokenEndpointRateLimitFilter(
            IntegrationRateLimitService rateLimitService,
            OpenIntegrationProperties properties,
            ObjectMapper objectMapper,
            IntegrationClientNetworkPolicy networkPolicy,
            OpenIntegrationClientAddressResolver addressResolver,
            SystemAuditPort auditPort,
            IntegrationCredentialUsageService credentialUsageService) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.networkPolicy = networkPolicy;
        this.addressResolver = addressResolver;
        this.auditPort = auditPort;
        this.credentialUsageService = credentialUsageService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/oauth2/token".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String clientId = extractClientId(request);
        String clientAddress = addressResolver.resolve(request);
        String applicationId = null;
        boolean completed = false;
        try {
            rateLimitService.acquire(
                    "token-client",
                    clientId,
                    properties.getTokenClientLimitPerMinute());
            rateLimitService.acquire(
                    "token-address",
                    clientAddress,
                    properties.getTokenAddressLimitPerMinute());
            IntegrationClientNetworkPolicy.Decision decision =
                    networkPolicy.evaluate(clientId, clientAddress);
            applicationId = decision.applicationId();
            if (!decision.allowed()) {
                writeInvalidClient(response);
                completed = true;
                return;
            }
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 200
                    && response.getStatus() < 300) {
                recordSuccessfulCredentialUse(clientId);
            }
            completed = true;
        } catch (RateLimitExceededException exception) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader(
                    "Retry-After",
                    Long.toString(exception.getRetryAfterSeconds()));
            objectMapper.writeValue(
                    response.getOutputStream(),
                    Map.of(
                            "error", "temporarily_unavailable",
                            "error_description",
                            "Token endpoint rate limit exceeded"));
            completed = true;
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Token endpoint policy check failed for client {}",
                    clientId,
                    exception);
            if (response.isCommitted()) {
                throw exception;
            }
            response.setStatus(503);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
            objectMapper.writeValue(
                    response.getOutputStream(),
                    Map.of(
                            "error", "temporarily_unavailable",
                            "error_description",
                            "Token service is temporarily unavailable"));
            completed = true;
        } finally {
            recordAudit(
                    request,
                    applicationId,
                    clientId,
                    clientAddress,
                    completed,
                    response.getStatus());
        }
    }

    private void recordSuccessfulCredentialUse(String clientId) {
        try {
            credentialUsageService.recordSuccessfulUse(clientId);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Unable to update integration credential usage metadata"
                            + " for client {}",
                    clientId,
                    exception);
        }
    }

    private void recordAudit(
            HttpServletRequest request,
            String applicationId,
            String clientId,
            String clientAddress,
            boolean completed,
            int status) {
        boolean success = completed && status >= 200 && status < 300;
        try {
            auditPort.record(SystemAuditEvent.builder()
                    .module(AuditModule.INTEGRATION)
                    .action(AuditAction.LOGIN)
                    .operationName("机器令牌签发")
                    .riskLevel(success
                            ? AuditRiskLevel.LOW
                            : AuditRiskLevel.HIGH)
                    .result(success
                            ? AuditResult.SUCCESS
                            : AuditResult.FAILURE)
                    .operatorName(clientId)
                    .operatorIp(clientAddress)
                    .requestMethod(request.getMethod())
                    .requestPath(request.getRequestURI())
                    .targetType(applicationId == null
                            ? "INTEGRATION_CLIENT"
                            : "INTEGRATION_APPLICATION")
                    .targetId(applicationId == null
                            ? clientId
                            : applicationId)
                    .summary(success
                            ? "机器令牌签发成功"
                            : "机器令牌签发失败")
                    .errorCode(success
                            ? null
                            : "TOKEN_ISSUANCE_FAILED")
                    .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build());
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Token endpoint audit write failed for client {}",
                    clientId,
                    exception);
        }
    }

    private void writeInvalidClient(HttpServletResponse response)
            throws IOException {
        response.setHeader(
                "WWW-Authenticate",
                "Basic realm=\"oauth2/client\", "
                        + "error=\"invalid_client\"");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(
                "application/json;charset=UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                Map.of("error", "invalid_client"));
    }

    private String extractClientId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null
                || header.length() > MAXIMUM_AUTHORIZATION_LENGTH
                || !header.startsWith("Basic ")) {
            return "anonymous";
        }
        try {
            String value = new String(
                    Base64.getDecoder().decode(header.substring(6)),
                    StandardCharsets.ISO_8859_1);
            int separator = value.indexOf(':');
            if (separator <= 0) {
                return "anonymous";
            }
            String clientId = value.substring(0, separator);
            return SAFE_CLIENT_ID.matcher(clientId).matches()
                    ? clientId
                    : "anonymous";
        } catch (IllegalArgumentException exception) {
            return "anonymous";
        }
    }
}
