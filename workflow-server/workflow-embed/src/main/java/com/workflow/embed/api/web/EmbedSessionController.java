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

    /**
     * 初始化嵌入式会话控制器，保存构造参数供后续方法使用。
     *
     * @param exchangeService 交换服务依赖，保存到当前对象供后续业务方法调用
     * @param authenticationService 认证服务依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedSessionController(
            EmbedSessionExchangeService exchangeService,
            EmbedSessionAuthenticationService authenticationService) {
        this.exchangeService = exchangeService;
        this.authenticationService = authenticationService;
    }

    /**
     * 处理交换，并将结果传给后续步骤。
     *
     * @param launchId 启动记录ID，后续用于处理交换时定位或关联目标
     * @param protocol {@code protocol}，供本方法处理交换时使用
     * @param servletRequest Servlet请求，作为 {@code CorrelationContext.businessTraceId} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理交换
     * @return 处理后的交换结果，供调用方继续处理
     */
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

    /**
     * 处理会话，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理会话
     * @return 处理后的会话结果，供调用方继续处理
     */
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

    /**
     * 处理心跳，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理心跳
     * @param diagnostics {@code diagnostics}，供本方法处理心跳时使用
     * @return 处理后的心跳结果，供调用方继续处理
     */
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

    /**
     * 使用 Bearer Token 幂等退出会话；重复退出仍返回 204，便于 iframe 销毁时安全重试。
     *
     * @param authorization Embed Bearer Token，由会话服务校验并定位需要终止的会话
     * @param request 提供已规范化的关联 ID，供退出审计使用
     * @return 无响应体的 204，并禁止缓存退出结果
     */
    @PostMapping("/session")
    public ResponseEntity<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            HttpServletRequest request) {
        authenticationService.logoutAuthorization(
                authorization, correlation(request));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    /**
     * 处理已认证，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理已认证
     * @return 处理后的已认证结果，供调用方继续处理
     */
    private static AuthenticatedEmbedSession authenticated(HttpServletRequest request) {
        Object value = request.getAttribute(
                EmbedSessionAuthenticationFilter.AUTHENTICATED_SESSION_ATTRIBUTE);
        if (value instanceof AuthenticatedEmbedSession session) {
            return session;
        }
        throw new EmbedException(401, EmbedErrorCode.EMBED_SESSION_INVALID,
                "Embed session is invalid");
    }

    /**
     * HTTP 适配器只传递 Guard 已规范化的关联 ID，不把 Launch/Session ID 当作请求 ID。
     *
     * @param request 本次请求，后续经校验后用于处理关联
     * @return 处理后的关联结果，供调用方继续处理
     */
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
     *
     * @param value 待规范化{@code peer}地址的原始输入，结果供调用方继续使用
     * @return 规范化后的{@code peer}地址文本，供调用方比较或展示
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

    /**
     * 封装交换的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param launchCode 启动记录编码，后续用于处理交换请求时定位或关联目标
     * @param channelId 通道ID，后续用于处理交换请求时定位或关联目标
     * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
     * @param parentNonce 父级{@code nonce}，保存在对象中供后续校验、查询或展示
     * @param childNonce 子级{@code nonce}，保存在对象中供后续校验、查询或展示
     * @param sdkVersion {@code sdk}版本，保存在对象中供后续校验、查询或展示
     */
    public record ExchangeRequest(
            @NotBlank String launchCode,
            @NotBlank String channelId,
            @NotBlank String parentOrigin,
            @NotBlank String parentNonce,
            @NotBlank String childNonce,
            String sdkVersion) {

        /**
         * 兑换请求不接受任何额外授权坐标或服务端状态字段。
         *
         * @param name 名称，后续用于处理驳回{@code unknown}字段时匹配或展示
         * @param value 待处理驳回{@code unknown}字段的原始输入，结果供调用方继续使用
         */
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Exchange request contains unsupported fields");
        }

        /**
         * 生成当前对象的文本表示，供日志和排障使用。
         *
         * @return 转换为后的字符串文本，供调用方比较或展示
         */
        @Override
        public String toString() {
            return "ExchangeRequest[launchCode=<redacted>, channelId=" + channelId
                    + ", parentOrigin=" + parentOrigin
                    + ", parentNonce=<redacted>, childNonce=<redacted>"
                    + ", sdkVersion=" + sdkVersion + "]";
        }
    }

    /**
     * 封装交换的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param sessionId 会话ID，后续用于处理交换响应时定位或关联目标
     * @param accessToken 访问令牌，后续用于授权校验、关联或幂等去重
     * @param tokenType 令牌类型标识，决定后续交换响应采用的处理分支
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param heartbeatAfterSeconds 心跳之后秒数，保存在对象中供后续校验、查询或展示
     * @param bootstrapUrl 初始化URL，保存在对象中供后续校验、查询或展示
     * @param protocolVersion {@code protocol}版本，保存在对象中供后续校验、查询或展示
     */
    public record ExchangeResponse(
            String sessionId,
            String accessToken,
            String tokenType,
            Instant expiresAt,
            Instant idleExpiresAt,
            int heartbeatAfterSeconds,
            String bootstrapUrl,
            String protocolVersion) {

        /**
         * 处理起始，并将结果传给后续步骤。
         *
         * @param issued 已签发，作为 {@code ExchangeResponse} 的输入影响后续处理
         * @return 处理后的起始结果，供调用方继续处理
         */
        static ExchangeResponse from(EmbedSessionIssued issued) {
            return new ExchangeResponse(
                    issued.sessionId(), issued.accessToken(), "Bearer", issued.expiresAt(),
                    issued.idleExpiresAt(), issued.heartbeatAfterSeconds(),
                    issued.bootstrapUrl(), issued.protocolVersion());
        }
    }

    /**
     * Diagnostics only; server time remains authoritative.
     *
     * @param visible 可见，保存在对象中供后续校验、查询或展示
     * @param clientTime 客户端时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record HeartbeatRequest(Boolean visible, Instant clientTime) {

        /**
         * 处理驳回{@code unknown}字段，并将结果传给后续步骤。
         *
         * @param name 名称，后续用于处理驳回{@code unknown}字段时匹配或展示
         * @param value 待处理驳回{@code unknown}字段的原始输入，结果供调用方继续使用
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Heartbeat request contains unsupported fields");
        }
    }
}
