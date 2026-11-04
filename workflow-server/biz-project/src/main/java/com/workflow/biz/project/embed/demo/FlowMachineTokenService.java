package com.workflow.biz.project.embed.demo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 机器 Token 仅保存在后端内存；锁内刷新避免并发请求同时冲击 OAuth 接口。 */
@Service
@Profile("partner-embed-demo")
class FlowMachineTokenService {
    private final EmbedDemoProperties.Flow config;
    private final FlowHttpClient http;
    private final Clock clock;
    private String cachedToken;
    private Instant usableUntil = Instant.EPOCH;

    FlowMachineTokenService(EmbedDemoProperties properties, FlowHttpClient http, @Qualifier("partnerEmbedDemoClock") Clock clock) {
        this.config = properties.flow();
        this.http = http;
        this.clock = clock;
    }

    /** 返回仍在安全使用窗口内的 Token；上游错误直接交还调用方，不缓存失败。 */
    synchronized String getToken(String traceId) {
        Instant startedAt = clock.instant();
        if (cachedToken != null && startedAt.isBefore(usableUntil)) {
            return cachedToken;
        }
        // OAuth Basic 的两个组成部分先按表单规则编码，兼容凭据中的冒号和特殊字符。
        String credentials = encode(config.clientId()) + ":" + encode(config.clientSecret());
        String authorization = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        var reply = http.post("/oauth2/token", authorization,
                "application/x-www-form-urlencoded", "grant_type=client_credentials", traceId);
        if (reply.status() != 200) {
            throw reply.failure(traceId);
        }
        String token = reply.body().path("access_token").asText("");
        long lifetime = reply.body().path("expires_in").asLong(0);
        if (token.isBlank() || !"Bearer".equalsIgnoreCase(reply.body().path("token_type").asText())
                || lifetime < 1 || lifetime > 86_400) {
            throw new EmbedDemoException(502, "FLOW_TOKEN_RESPONSE_INVALID", traceId, null);
        }
        cachedToken = token;
        // 从请求开始计时并预留最多 30 秒，避免把网络耗时或临近过期的 Token 当成有效缓存。
        usableUntil = startedAt.plusSeconds(lifetime - Math.min(30, Math.max(1, lifetime / 10)));
        return token;
    }

    /** 仅清除被拒绝的那一版，防止并发请求的旧 401 清掉另一请求刚刷新的 Token。 */
    synchronized void invalidate(String rejectedToken) {
        if (rejectedToken.equals(cachedToken)) {
            cachedToken = null;
            usableUntil = Instant.EPOCH;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
