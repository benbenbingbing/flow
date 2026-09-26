package com.workflow.openapi.infrastructure.security;

import com.workflow.openapi.application.security.IntegrationCredentialUsageService;
import com.workflow.openapi.application.security.IntegrationRateLimitService;
import com.workflow.openapi.infrastructure.config.OpenIntegrationProperties;
import com.workflow.openapi.security.IntegrationClientNetworkPolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.core.error.RateLimitExceededException;
import com.workflow.core.logging.LogValue;
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

/**
 * 封装令牌接口端点频率上限过滤相关能力和状态；供同一业务流程的后续处理使用。
 */
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

    /**
     * 初始化令牌接口端点频率上限过滤，保存构造参数供后续方法使用。
     *
     * @param rateLimitService 频率上限服务依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param networkPolicy {@code network}策略依赖，保存到当前对象供后续业务方法调用
     * @param addressResolver 地址解析器依赖，保存到当前对象供后续业务方法调用
     * @param auditPort 审计端口依赖，保存到当前对象供后续业务方法调用
     * @param credentialUsageService 凭据使用场景服务依赖，保存到当前对象供后续业务方法调用
     */
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

    /**
     * 判断是否需要非过滤；判断结果决定调用方的后续分支。
     *
     * @param request 本次请求，后续经校验后用于判断是否需要非过滤
     * @return 非过滤条件成立时为 true，否则为 false
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/oauth2/token".equals(request.getRequestURI());
    }

    /**
     * 处理{@code do}过滤内部，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理{@code do}过滤内部
     * @param response 响应，作为 {@code writeInvalidClient} 的输入影响后续处理
     * @param filterChain 过滤链，供本方法处理{@code do}过滤内部时使用
     * @throws ServletException 过滤器或请求处理链执行失败时抛出
     * @throws IOException 读取或写入外部资源失败时抛出
     */
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
                    LogValue.safe(clientId),
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

    /**
     * 记录成功凭据{@code use}；供后续追溯或审计使用。
     *
     * @param clientId 客户端ID，后续用于记录成功凭据{@code use}时定位或关联目标
     */
    private void recordSuccessfulCredentialUse(String clientId) {
        try {
            credentialUsageService.recordSuccessfulUse(clientId);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Unable to update integration credential usage metadata"
                            + " for client {}",
                    LogValue.safe(clientId),
                    exception);
        }
    }

    /**
     * 记录审计；供后续追溯或审计使用。
     *
     * @param request 本次请求，后续经校验后用于记录审计
     * @param applicationId 应用ID，后续用于记录审计时定位或关联目标
     * @param clientId 客户端ID，后续用于记录审计时定位或关联目标
     * @param clientAddress 客户端地址，供本方法记录审计时使用
     * @param completed {@code completed}，供本方法记录审计时使用
     * @param status 状态标识，决定后续审计采用的处理分支
     */
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
                    LogValue.safe(clientId),
                    exception);
        }
    }

    /**
     * 写入无效客户端；后续读取或执行将使用更新后的状态。
     *
     * @param response 响应，作为 {@code objectMapper.writeValue} 的输入影响后续处理
     * @throws IOException 读取或写入外部资源失败时抛出
     */
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

    /**
     * 提取客户端ID；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于提取客户端ID
     * @return 提取后的客户端ID文本，供调用方比较或展示
     */
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
