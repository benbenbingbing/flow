package com.workflow.embed.api.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.session.EmbedSessionAuthenticationService;
import com.workflow.embed.application.session.EmbedSessionExchangeCommand;
import com.workflow.embed.application.session.EmbedSessionExchangeService;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedSessionIssued;
import com.workflow.embed.domain.EmbedSessionState;
import com.workflow.embed.security.EmbedSessionAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for Launch exchange, session inspection, heartbeat and idempotent logout. */
@Validated
@RestController
@RequestMapping("/api/embed/v1")
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedSessionController {

    private final EmbedSessionExchangeService exchangeService;
    private final EmbedSessionAuthenticationService authenticationService;

    public EmbedSessionController(
            EmbedSessionExchangeService exchangeService,
            EmbedSessionAuthenticationService authenticationService) {
        this.exchangeService = exchangeService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/launches/{launchId}/exchange")
    public ResponseEntity<EmbedApiEnvelope<ExchangeResponse>> exchange(
            @PathVariable String launchId,
            @RequestHeader(value = "X-Flow-Embed-Protocol", required = false) String protocol,
            HttpServletRequest servletRequest,
            @Valid @RequestBody ExchangeRequest request) {
        if (!"1".equals(protocol)) {
            throw new EmbedException(400, EmbedErrorCode.INVALID_REQUEST,
                    "X-Flow-Embed-Protocol must be 1");
        }
        EmbedSessionIssued issued = exchangeService.exchange(
                new EmbedSessionExchangeCommand(
                        launchId, request.launchCode(), request.channelId(), request.parentOrigin(),
                        request.parentNonce(), request.childNonce(), request.sdkVersion(),
                        normalizePeerAddress(servletRequest.getRemoteAddr())),
                correlation(servletRequest));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EmbedApiEnvelope.ok(
                        ExchangeResponse.from(issued),
                        CorrelationContext.businessTraceId(servletRequest)));
    }

    @GetMapping("/session")
    public ResponseEntity<EmbedApiEnvelope<EmbedSessionState>> session(
            HttpServletRequest request) {
        AuthenticatedEmbedSession authenticated = authenticated(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EmbedApiEnvelope.ok(
                        authenticationService.state(authenticated),
                        CorrelationContext.businessTraceId(request)));
    }

    @PostMapping("/session/heartbeat")
    public ResponseEntity<EmbedApiEnvelope<EmbedSessionState>> heartbeat(
            HttpServletRequest request,
            @RequestBody(required = false) HeartbeatRequest diagnostics) {
        AuthenticatedEmbedSession authenticated = authenticated(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EmbedApiEnvelope.ok(
                        authenticationService.heartbeat(authenticated),
                        CorrelationContext.businessTraceId(request)));
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            HttpServletRequest request) {
        authenticationService.logoutAuthorization(
                authorization, correlation(request));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private static AuthenticatedEmbedSession authenticated(HttpServletRequest request) {
        Object value = request.getAttribute(
                EmbedSessionAuthenticationFilter.AUTHENTICATED_SESSION_ATTRIBUTE);
        if (value instanceof AuthenticatedEmbedSession session) {
            return session;
        }
        throw new EmbedException(401, EmbedErrorCode.EMBED_SESSION_INVALID,
                "Embed session is invalid");
    }

    /** HTTP 适配器只传递 Guard 已规范化的关联 ID，不把 Launch/Session ID 当作请求 ID。 */
    private static EmbedAuditCorrelation correlation(HttpServletRequest request) {
        return EmbedAuditCorrelation.of(
                CorrelationContext.businessTraceId(request),
                CorrelationContext.requestId(request));
    }

    /**
     * 只使用 Servlet 连接对端，不信任浏览器可伪造的 X-Forwarded-For。
     * 
     * <p>生产网关未提供可验证的代理链时，多个用户会共享代理对端 bucket，
     * 这是安全保守的降级；不得为了精细限流直接信任请求头。</p>
     */
    static String normalizePeerAddress(String value) {
        if (value == null
                || value.isBlank()
                || value.length() > 45
                || !value.matches("[0-9A-Fa-f:.]+")) {
            return "unknown";
        }
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException error) {
            return "unknown";
        }
    }

    public record ExchangeRequest(
            @NotBlank String launchCode,
            @NotBlank String channelId,
            @NotBlank String parentOrigin,
            @NotBlank String parentNonce,
            @NotBlank String childNonce,
            String sdkVersion) {

        /** 兑换请求不接受任何额外授权坐标或服务端状态字段。 */
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Exchange request contains unsupported fields");
        }

        @Override
        public String toString() {
            return "ExchangeRequest[launchCode=<redacted>, channelId=" + channelId
                    + ", parentOrigin=" + parentOrigin
                    + ", parentNonce=<redacted>, childNonce=<redacted>"
                    + ", sdkVersion=" + sdkVersion + "]";
        }
    }

    public record ExchangeResponse(
            String sessionId,
            String accessToken,
            String tokenType,
            Instant expiresAt,
            Instant idleExpiresAt,
            int heartbeatAfterSeconds,
            String bootstrapUrl,
            String protocolVersion) {

        static ExchangeResponse from(EmbedSessionIssued issued) {
            return new ExchangeResponse(
                    issued.sessionId(), issued.accessToken(), "Bearer", issued.expiresAt(),
                    issued.idleExpiresAt(), issued.heartbeatAfterSeconds(),
                    issued.bootstrapUrl(), issued.protocolVersion());
        }
    }

    /** Diagnostics only; server time remains authoritative. */
    public record HeartbeatRequest(Boolean visible, Instant clientTime) {

        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Heartbeat request contains unsupported fields");
        }
    }
}
