package com.workflow.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Browser cross-origin policy.
 */
@Data
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    /** 允许携带浏览器凭证访问 API 的明确来源列表。 */
    private List<String> allowedOrigins = new ArrayList<>();
    /** 跨域请求允许使用的 HTTP 方法。 */
    private List<String> allowedMethods = new ArrayList<>(
            List.of(
                    "GET",
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE",
                    "OPTIONS"));
    /** 跨域请求允许携带的请求头。 */
    private List<String> allowedHeaders = new ArrayList<>(
            List.of(
                    "Authorization",
                    "Content-Type",
                    "Idempotency-Key",
                    "X-Trace-Id",
                    "X-Business-Trace-Key"));
    /** 浏览器缓存预检结果的时间。 */
    private Duration maxAge = Duration.ofHours(1);
    /** 是否允许浏览器跨域请求携带 Cookie 等凭证。 */
    private boolean allowCredentials = true;
}
