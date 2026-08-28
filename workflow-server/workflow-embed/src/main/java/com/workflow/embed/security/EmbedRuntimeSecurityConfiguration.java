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
