package com.workflow.openapi.infrastructure.security;

import com.workflow.openapi.application.security.IntegrationRateLimitService;
import com.workflow.openapi.application.security.OpenApiConcurrencyLeaseService;
import com.workflow.openapi.security.IntegrationClientNetworkPolicy;

import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.core.error.RateLimitExceededException;
import com.workflow.core.logging.LogValue;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.infrastructure.web.OpenRequestTrace;
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

/**
 * 封装打开API应用策略过滤相关能力和状态；供同一业务流程的后续处理使用。
 */
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

    /**
     * 初始化打开API应用策略过滤，保存构造参数供后续方法使用。
     *
     * @param applicationMapper 应用映射器依赖，保存到当前对象供后续业务方法调用
     * @param networkPolicy {@code network}策略依赖，保存到当前对象供后续业务方法调用
     * @param addressResolver 地址解析器依赖，保存到当前对象供后续业务方法调用
     * @param rateLimitService 频率上限服务依赖，保存到当前对象供后续业务方法调用
     * @param concurrencyService {@code concurrency}服务依赖，保存到当前对象供后续业务方法调用
     * @param responseWriter 响应写入器依赖，保存到当前对象供后续业务方法调用
     * @param auditPort 审计端口依赖，保存到当前对象供后续业务方法调用
     */
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

    /**
     * 在开放接口进入业务处理前校验应用身份、来源地址及访问配额，并记录审计结果。
     *
     * @param request 当前 HTTP 请求，读取令牌、来源地址和追踪信息供策略判断与审计
     * @param response HTTP 响应，校验失败时写入错误结果，成功时交给后续过滤链
     * @param filterChain 后续过滤链，仅在全部策略检查通过后执行
     * @throws ServletException 过滤器或请求处理链执行失败时抛出
     * @throws IOException 读取或写入外部资源失败时抛出
     */
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
            // 每次请求重新关联数据库中的应用状态，使停用或过期立即作用于已签发令牌。
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
                    || !clientId.equals(application.getClientId())
                    || !isUsable(application)) {
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
            // 网络策略返回的应用还必须与令牌绑定应用一致，避免跨应用复用客户端策略。
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
                // 先取得并发租约再放行；租约在 finally 中释放，避免异常路径占满配额。
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
                    LogValue.safe(applicationId),
                    LogValue.safe(OpenRequestTrace.get(request)),
                    exception);
            // 响应已提交时不能再改写状态码，交由上层处理原异常。
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
            // 无论鉴权结果如何都记录审计；释放租约失败只记日志，不覆盖原响应。
            if (lease != null) {
                try {
                    concurrencyService.release(lease);
                } catch (RuntimeException exception) {
                    log.warn(
                            "开放接口并发租约释放失败: applicationId={},"
                                    + " leaseId={}, traceId={}",
                            LogValue.safe(applicationId),
                            LogValue.safe(lease.id()),
                            LogValue.safe(OpenRequestTrace.get(request)),
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

    /**
     * 校验令牌对应应用仍可使用，确保停用、吊销或过期后已签发令牌也会立即失效。
     *
     * @param application 应用，作为 {@code equals} 的输入影响后续处理
     * @return {@code usable}条件成立时为 true，否则为 false
     */
    private boolean isUsable(IntegrationApplicationRecord application) {
        return "ACTIVE".equals(application.getStatus())
                && (application.getExpiresAt() == null
                || application.getExpiresAt().isAfter(
                LocalDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * 记录审计；供后续追溯或审计使用。
     *
     * @param request 本次请求，后续经校验后用于记录审计
     * @param response 响应，作为 {@code summary} 的输入影响后续处理
     * @param applicationId 应用ID，后续用于记录审计时定位或关联目标
     * @param clientId 客户端ID，后续用于记录审计时定位或关联目标
     * @param address 地址，供本方法记录审计时使用
     * @param started 已启动，供本方法记录审计时使用
     */
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
                    .operationName("调用 Embed 开放接口")
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
                    LogValue.safe(applicationId),
                    LogValue.safe(OpenRequestTrace.get(request)),
                    exception);
        }
    }
}
