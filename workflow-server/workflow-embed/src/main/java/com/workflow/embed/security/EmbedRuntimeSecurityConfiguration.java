package com.workflow.embed.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.config.EmbedProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Embed API 的独立安全链。
 *
 * <p>该安全链无论功能开关是否启用都注册，并默认拒绝全部 Embed 请求。这样在后续过滤器或
 * 路由漏配时，请求也不会落入平台已有的 catch-all permitAll 安全链。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbedProperties.class)
public class EmbedRuntimeSecurityConfiguration {

    @Bean
    @Order(3)
    SecurityFilterChain embedRuntimeSecurity(
            HttpSecurity http,
            ObjectMapper objectMapper,
            EmbedProperties properties,
            ObjectProvider<EmbedSessionAuthenticationFilter> filterProvider)
            throws Exception {
        var exchange = PathPatternRequestMatcher.withDefaults().matcher(
                HttpMethod.POST,
                "/api/embed/v1/launches/{launchId}/exchange");
        var runtime = PathPatternRequestMatcher.withDefaults().matcher(
                "/api/embed/v1/runtime/**");
        var sessionRead = PathPatternRequestMatcher.withDefaults().matcher(
                HttpMethod.GET,
                "/api/embed/v1/session");
        var sessionLogout = PathPatternRequestMatcher.withDefaults().matcher(
                HttpMethod.DELETE,
                "/api/embed/v1/session");
        var heartbeat = PathPatternRequestMatcher.withDefaults().matcher(
                HttpMethod.POST,
                "/api/embed/v1/session/heartbeat");

        http
                .securityMatcher("/api/embed/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        exchange,
                        runtime,
                        sessionRead,
                        sessionLogout,
                        heartbeat))
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, error) ->
                                writeDenied(objectMapper, request, response, 401,
                                        "EMBED_SESSION_INVALID",
                                        "Embed session is invalid"))
                        .accessDeniedHandler((request, response, error) ->
                                writeDenied(objectMapper, request, response, 403,
                                        "EMBED_ACCESS_DENIED",
                                        "Embed access is denied")));

        if (properties.isEnabled()) {
            // 启用状态必须同时存在专属过滤器；缺少任一依赖时启动失败，不能悄悄放行。
            EmbedSessionAuthenticationFilter filter = filterProvider.getIfAvailable();
            if (filter == null) {
                throw new IllegalStateException(
                        "workflow.embed.enabled=true requires "
                                + "EmbedSessionAuthenticationFilter");
            }
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(exchange).permitAll()
                            .requestMatchers(runtime).permitAll()
                            .requestMatchers(sessionRead).permitAll()
                            .requestMatchers(sessionLogout).permitAll()
                            .requestMatchers(heartbeat).permitAll()
                            .anyRequest().denyAll())
                    .addFilterBefore(filter, AuthorizationFilter.class)
                    .addFilterBefore(
                            new EmbedRequestGuardFilter(
                                    objectMapper,
                                    properties.getMaxPayloadBytes()),
                            EmbedSessionAuthenticationFilter.class);
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                    .anyRequest().denyAll());
        }
        return http.build();
    }

    /**
     * 带 Embed 协议头的普通 {@code /api/**} 请求进入独立委托链。
     *
     * <p>匹配任意协议值而不是只匹配 {@code 1}，确保错误版本也不会落入普通 JWT
     * 链。opaque Bearer 必须先由 Embed filter 验证，之后 MVC 的 EndpointAuthorization
     * 和 DataScope 仍按映射 Flow 用户执行。</p>
     */
    @Bean
    @Order(4)
    SecurityFilterChain embedDelegatedRuntimeSecurity(
            HttpSecurity http,
            ObjectMapper objectMapper,
            EmbedProperties properties,
            ObjectProvider<EmbedSessionAuthenticationFilter> filterProvider)
            throws Exception {
        RequestMatcher delegated = request -> {
            String path = request.getRequestURI();
            return path != null
                    && path.startsWith("/api/")
                    && !path.startsWith("/api/embed/")
                    && request.getHeader(
                            EmbedSessionAuthenticationFilter.PROTOCOL_HEADER)
                            != null;
        };
        http
                .securityMatcher(delegated)
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, error) ->
                                writeDenied(objectMapper, request, response, 401,
                                        "EMBED_SESSION_INVALID",
                                        "Embed session is invalid"))
                        .accessDeniedHandler((request, response, error) ->
                                writeDenied(objectMapper, request, response, 403,
                                        "EMBED_ACCESS_DENIED",
                                        "Embed access is denied")));
        if (properties.isEnabled()) {
            EmbedSessionAuthenticationFilter filter =
                    filterProvider.getIfAvailable();
            if (filter == null) {
                throw new IllegalStateException(
                        "workflow.embed.enabled=true requires "
                                + "EmbedSessionAuthenticationFilter");
            }
            http.authorizeHttpRequests(authorize -> authorize
                            // opaque Embed Session 只是原生 UI 数据面身份，
                            // 不得进入登录、Embed 管理或 Open API 控制面。
                            .requestMatchers(
                                    "/api/auth/**",
                                    "/api/embed-management/**",
                                    "/api/open/**",
                                    "/api/integration-applications/**")
                            .denyAll()
                            .anyRequest().permitAll())
                    .addFilterBefore(filter, AuthorizationFilter.class);
            // 委托页面是 Flow 原生数据面，不能套用 Embed V1 投影 API
            // 的 1 MiB JSON 上限，否则平台可保存的大富文本在嵌入页会
            // 额外失败。文件与请求体大小继续由平台统一 Servlet/
            // multipart 限制和业务校验约束。
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                    .anyRequest().denyAll());
        }
        return http.build();
    }

    /**
     * 输出稳定且不泄露内部鉴权原因的错误包络。
     */
    private static void writeDenied(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String errorCode,
            String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String traceId = CorrelationContext.businessTraceId(request);
        response.setHeader(CorrelationContext.BUSINESS_TRACE_HEADER, traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status);
        body.put("message", message);
        body.put("errorCode", errorCode);
        body.put("data", null);
        body.put("traceId", traceId);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
