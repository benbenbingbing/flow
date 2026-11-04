package com.workflow.biz.project.embed.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 固定连接地址、连接/请求超时和重定向策略，TLS 使用 JVM 信任库。 */
@Component
@Profile("partner-embed-demo")
class FlowHttpClient {
    private final EmbedDemoProperties.Flow config;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    FlowHttpClient(EmbedDemoProperties properties, ObjectMapper mapper) {
        this.config = properties.flow();
        this.mapper = mapper;
    }

    /** 请求路径仅由内部代码传入；失败时抛出脱敏异常，禁止记录请求体、响应原文和 Authorization。 */
    Reply post(String path, String authorization, String contentType, String body, String traceId) {
        HttpRequest request = HttpRequest.newBuilder(config.apiBaseUrl().resolve(path))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", authorization)
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .header("X-Trace-Id", traceId)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 非 JSON（包括代理错误页）统一作为上游故障处理，不把内容带到宿主页面。
            JsonNode document = mapper.readTree(response.body());
            if (document == null || !document.isObject()) {
                throw new IOException("Invalid response");
            }
            String retryAfter = response.headers().firstValue("Retry-After")
                    .filter(value -> value.matches("[0-9]{1,6}")).orElse(null);
            return new Reply(response.statusCode(), document, retryAfter);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmbedDemoException(502, "FLOW_UNAVAILABLE", traceId, null);
        } catch (IOException exception) {
            throw new EmbedDemoException(502, "FLOW_UNAVAILABLE", traceId, null);
        }
    }

    record Reply(int status, JsonNode body, String retryAfter) {
        /** 403/429 供调用方处理授权和退避，其他上游状态不能冒充宿主登录状态。 */
        EmbedDemoException failure(String traceId) {
            String code = body.path("errorCode").asText("");
            if (!code.matches("[A-Z][A-Z0-9_]{0,99}")) {
                code = "FLOW_REQUEST_FAILED";
            }
            return new EmbedDemoException(status == 403 || status == 429 ? status : 502,
                    code, traceId, status == 429 ? retryAfter : null);
        }

        @Override
        public String toString() {
            return "Reply[status=" + status + ", body=<redacted>]";
        }
    }
}
