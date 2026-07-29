package com.workflow.openapi.webhook.delivery;

import com.workflow.http.RestEndpointPolicy;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import com.workflow.openapi.webhook.security.WebhookSecretCipher;
import com.workflow.openapi.webhook.security.WebhookSignatureService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

@Component
public class WebhookHttpClient {

    private static final int MAX_REQUEST_BYTES = 256 * 1024;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int MAX_STORED_RESPONSE_CHARS = 4096;
    private static final long MAX_RETRY_AFTER_SECONDS = 24 * 60 * 60;

    private final RestEndpointPolicy endpointPolicy;
    private final WebhookSecretCipher secretCipher;
    private final WebhookSignatureService signatureService;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Duration requestTimeout;

    public WebhookHttpClient(
            RestEndpointPolicy endpointPolicy,
            WebhookSecretCipher secretCipher,
            WebhookSignatureService signatureService) {
        this(
                endpointPolicy,
                secretCipher,
                signatureService,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(
                                HttpClient.Redirect.NEVER)
                        .build(),
                Clock.systemUTC(),
                Duration.ofSeconds(10));
    }

    WebhookHttpClient(
            RestEndpointPolicy endpointPolicy,
            WebhookSecretCipher secretCipher,
            WebhookSignatureService signatureService,
            HttpClient httpClient,
            Clock clock) {
        this(
                endpointPolicy,
                secretCipher,
                signatureService,
                httpClient,
                clock,
                Duration.ofSeconds(10));
    }

    WebhookHttpClient(
            RestEndpointPolicy endpointPolicy,
            WebhookSecretCipher secretCipher,
            WebhookSignatureService signatureService,
            HttpClient httpClient,
            Clock clock,
            Duration requestTimeout) {
        this.endpointPolicy = endpointPolicy;
        this.secretCipher = secretCipher;
        this.signatureService = signatureService;
        this.httpClient = httpClient;
        this.clock = clock;
        if (requestTimeout == null
                || requestTimeout.isZero()
                || requestTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "Webhook 请求超时必须为正数");
        }
        this.requestTimeout = requestTimeout;
    }

    public WebhookHttpResult send(
            WebhookDeliveryWorkRecord delivery)
            throws IOException, InterruptedException {
        byte[] body = delivery.payloadDocument()
                .getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException(
                    "Webhook 请求体超过 256 KiB");
        }
        URI endpoint = URI.create(delivery.endpointUrl());
        endpointPolicy.validate(endpoint);
        String secret = secretCipher.decrypt(
                delivery.signingSecretCiphertext());
        long timestamp = clock.instant().getEpochSecond();
        String signature = signatureService.sign(
                delivery.eventId(),
                timestamp,
                body,
                secret);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header(
                        "Content-Type",
                        "application/cloudevents+json")
                .header(
                        "Flow-Webhook-Id",
                        delivery.eventId())
                .header(
                        "Flow-Webhook-Timestamp",
                        Long.toString(timestamp))
                .header(
                        "Flow-Webhook-Signature",
                        signature)
                .header(
                        "X-Trace-Id",
                        delivery.traceId() == null
                                || delivery.traceId().isBlank()
                                ? delivery.eventId()
                                : delivery.traceId())
                .header("User-Agent", "Flow-Webhook/1")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream());
        byte[] responseBytes;
        try (InputStream stream = response.body()) {
            responseBytes = stream.readNBytes(
                    MAX_RESPONSE_BYTES + 1);
        }
        boolean truncated =
                responseBytes.length > MAX_RESPONSE_BYTES;
        int storedBytes = Math.min(
                responseBytes.length,
                MAX_RESPONSE_BYTES);
        String excerpt = response.statusCode() >= 200
                && response.statusCode() < 300
                ? null
                : sanitize(new String(
                        responseBytes,
                        0,
                        storedBytes,
                        StandardCharsets.UTF_8));
        return new WebhookHttpResult(
                response.statusCode(),
                excerpt,
                truncated,
                parseRetryAfter(
                        response.headers()
                                .firstValue("Retry-After")
                                .orElse(null)));
    }

    private Long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return Math.max(
                    0,
                    Math.min(
                            seconds,
                            MAX_RETRY_AFTER_SECONDS));
        } catch (NumberFormatException ignored) {
            try {
                Instant target = ZonedDateTime.parse(
                                value.trim(),
                                DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant();
                long seconds = Duration.between(
                        clock.instant(),
                        target).getSeconds();
                return Math.max(
                        0,
                        Math.min(
                                seconds,
                                MAX_RETRY_AFTER_SECONDS));
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }

    private String sanitize(String value) {
        String normalized = value
                .replaceAll("[\\p{Cntrl}]+", " ")
                .replaceAll(" +", " ")
                .trim();
        return normalized.length() <= MAX_STORED_RESPONSE_CHARS
                ? normalized
                : normalized.substring(
                        0,
                        MAX_STORED_RESPONSE_CHARS);
    }
}
