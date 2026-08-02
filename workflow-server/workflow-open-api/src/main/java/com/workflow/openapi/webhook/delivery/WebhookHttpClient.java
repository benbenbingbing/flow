package com.workflow.openapi.webhook.delivery;

import com.workflow.http.HttpTransportRequest;
import com.workflow.http.HttpTransportResult;
import com.workflow.http.PinnedHttpTransport;
import com.workflow.http.WorkflowHttpProperties;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import com.workflow.openapi.webhook.security.WebhookSecretCipher;
import com.workflow.openapi.webhook.security.WebhookSignatureService;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WebhookHttpClient {

    private static final int MAX_REQUEST_BYTES = 256 * 1024;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int MAX_STORED_RESPONSE_CHARS = 4096;
    private static final long MAX_RETRY_AFTER_SECONDS = 24 * 60 * 60;

    private final WebhookSecretCipher secretCipher;
    private final WebhookSignatureService signatureService;
    private final PinnedHttpTransport httpTransport;
    private final WorkflowHttpProperties httpProperties;
    private final Clock clock;
    private final Duration requestTimeout;

    @Autowired
    public WebhookHttpClient(
            WebhookSecretCipher secretCipher,
            WebhookSignatureService signatureService,
            PinnedHttpTransport httpTransport,
            WorkflowHttpProperties httpProperties) {
        this(
                secretCipher,
                signatureService,
                httpTransport,
                httpProperties,
                Clock.systemUTC(),
                Duration.ofSeconds(10));
    }

    WebhookHttpClient(
            WebhookSecretCipher secretCipher,
            WebhookSignatureService signatureService,
            PinnedHttpTransport httpTransport,
            WorkflowHttpProperties httpProperties,
            Clock clock) {
        this(
                secretCipher,
                signatureService,
                httpTransport,
                httpProperties,
                clock,
                Duration.ofSeconds(10));
    }

    WebhookHttpClient(
            WebhookSecretCipher secretCipher,
            WebhookSignatureService signatureService,
            PinnedHttpTransport httpTransport,
            WorkflowHttpProperties httpProperties,
            Clock clock,
            Duration requestTimeout) {
        this.secretCipher = secretCipher;
        this.signatureService = signatureService;
        this.httpTransport = httpTransport;
        this.httpProperties = httpProperties;
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
        String secret = secretCipher.decrypt(
                delivery.signingSecretCiphertext());
        long timestamp = clock.instant().getEpochSecond();
        String signature = signatureService.sign(
                delivery.eventId(),
                timestamp,
                body,
                secret);
        Map<String, String> headers = Map.of(
                "Content-Type", "application/cloudevents+json",
                "Flow-Webhook-Id", delivery.eventId(),
                "Flow-Webhook-Key-Id",
                Long.toString(delivery.signingSecretVersion()),
                "Flow-Webhook-Timestamp", Long.toString(timestamp),
                "Flow-Webhook-Signature", signature,
                "X-Trace-Id", delivery.traceId() == null
                        || delivery.traceId().isBlank()
                        ? delivery.eventId()
                        : delivery.traceId(),
                "User-Agent", "Flow-Webhook/1");
        HttpTransportResult response = httpTransport.execute(
                new HttpTransportRequest(
                        "POST",
                        endpoint,
                        headers,
                        new String(body, StandardCharsets.UTF_8),
                        Math.toIntExact(requestTimeout.toMillis()),
                        Set.copyOf(httpProperties.getAllowedHosts()),
                        MAX_RESPONSE_BYTES,
                        true));
        String excerpt = response.statusCode() >= 200
                && response.statusCode() < 300
                ? null
                : sanitize(response.body());
        return new WebhookHttpResult(
                response.statusCode(),
                excerpt,
                response.responseTruncated(),
                parseRetryAfter(
                        response.retryAfter()));
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
