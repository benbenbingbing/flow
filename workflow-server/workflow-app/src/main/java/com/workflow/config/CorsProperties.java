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

    private List<String> allowedOrigins = new ArrayList<>();
    private List<String> allowedMethods = new ArrayList<>(
            List.of(
                    "GET",
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE",
                    "OPTIONS"));
    private List<String> allowedHeaders = new ArrayList<>(
            List.of(
                    "Authorization",
                    "Content-Type",
                    "X-Trace-Id",
                    "X-Business-Trace-Key"));
    private Duration maxAge = Duration.ofHours(1);
}
