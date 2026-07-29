package com.workflow.openapi.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.openapi.api.error.OpenApiException;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationIdempotencyMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationIdempotencyRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenIdempotencyService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile(
            "[\\x21-\\x7E]{1,128}");
    private static final int RETENTION_DAYS = 7;
    private static final int PROCESSING_TIMEOUT_SECONDS = 120;

    private final IntegrationIdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OpenIdempotencyService(
            IntegrationIdempotencyMapper mapper,
            ObjectMapper objectMapper) {
        this(mapper, objectMapper, Clock.systemUTC());
    }

    OpenIdempotencyService(
            IntegrationIdempotencyMapper mapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(
            String applicationId,
            String operation,
            String idempotencyKey,
            Object request) {
        validateKey(idempotencyKey);
        String requestHash = hash(request);
        LocalDateTime now = now();
        int inserted = mapper.insertProcessing(
                IdWorker.getIdStr(),
                applicationId,
                operation,
                idempotencyKey,
                requestHash,
                now,
                now.plusDays(RETENTION_DAYS));
        IntegrationIdempotencyRecord record = mapper.find(
                applicationId,
                operation,
                idempotencyKey);
        if (record == null) {
            throw new IllegalStateException(
                    "幂等记录认领后无法读取");
        }
        if (!requestHash.equals(record.getRequestHash())) {
            throw new OpenApiException(
                    409,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was reused with different input");
        }
        if (inserted == 1) {
            return Claim.acquired(record);
        }
        if ("SUCCEEDED".equals(record.getStatus())) {
            return Claim.replay(record);
        }
        boolean failed = "FAILED_RETRYABLE".equals(record.getStatus());
        boolean stale = "PROCESSING".equals(record.getStatus())
                && record.getProcessingStartedAt() != null
                && record.getProcessingStartedAt().isBefore(
                        now.minusSeconds(PROCESSING_TIMEOUT_SECONDS));
        if (failed || stale) {
            int reacquired = mapper.reacquire(
                    record.getId(),
                    record.getFencingToken(),
                    now,
                    now.minusSeconds(PROCESSING_TIMEOUT_SECONDS),
                    now.plusDays(RETENTION_DAYS));
            IntegrationIdempotencyRecord current = mapper.find(
                    applicationId,
                    operation,
                    idempotencyKey);
            if (reacquired == 1) {
                return Claim.acquired(current);
            }
            if (current != null
                    && "SUCCEEDED".equals(current.getStatus())) {
                return Claim.replay(current);
            }
        }
        return Claim.processing(record);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeInBusinessTransaction(
            Claim claim,
            String resourceType,
            String resourceId,
            int responseStatus,
            Object responseData) {
        int updated = mapper.complete(
                claim.id(),
                claim.fencingToken(),
                resourceType,
                resourceId,
                responseStatus,
                write(responseData),
                now());
        if (updated != 1) {
            throw new IllegalStateException(
                    "幂等 fencing token 已失效");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRetryable(Claim claim) {
        if (claim.acquired()) {
            mapper.failRetryable(
                    claim.id(),
                    claim.fencingToken(),
                    now());
        }
    }

    public <T> T readReplay(
            Claim claim,
            Class<T> type) {
        if (!claim.replay()
                || claim.responseBody() == null) {
            throw new IllegalStateException(
                    "幂等记录不包含可重放结果");
        }
        try {
            return objectMapper.readValue(
                    claim.responseBody(),
                    type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "幂等响应摘要损坏",
                    exception);
        }
    }

    @Scheduled(cron = "${workflow.open-api.idempotency-cleanup-cron:"
            + "0 35 3 * * *}")
    @Transactional
    public void cleanup() {
        int removed;
        do {
            removed = mapper.deleteExpired(now(), 1_000);
        } while (removed == 1_000);
    }

    private void validateKey(String value) {
        if (value == null
                || !IDEMPOTENCY_KEY.matcher(value).matches()) {
            throw new OpenApiException(
                    400,
                    "INVALID_REQUEST",
                    "Idempotency-Key must contain 1 to 128 printable "
                            + "non-space ASCII characters");
        }
    }

    private String hash(Object value) {
        try {
            JsonNode canonical = canonicalize(
                    objectMapper.valueToTree(value));
            byte[] bytes = objectMapper.writeValueAsBytes(canonical);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(bytes));
        } catch (JsonProcessingException
                | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "无法计算幂等请求摘要",
                    exception);
        }
    }

    private JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            java.util.stream.StreamSupport.stream(
                            ((Iterable<Map.Entry<String, JsonNode>>) () ->
                                    value.fields()).spliterator(),
                            false)
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> result.set(
                            entry.getKey(),
                            canonicalize(entry.getValue())));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        return value;
    }

    private String write(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.getBytes(StandardCharsets.UTF_8).length > 65_535) {
                throw new IllegalArgumentException(
                        "幂等响应摘要超过限制");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "无法序列化幂等响应摘要",
                    exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
    }

    public record Claim(
            String id,
            long fencingToken,
            boolean acquired,
            boolean replay,
            boolean processing,
            int responseStatus,
            String responseBody) {

        static Claim acquired(IntegrationIdempotencyRecord record) {
            return new Claim(
                    record.getId(),
                    record.getFencingToken(),
                    true,
                    false,
                    false,
                    0,
                    null);
        }

        static Claim replay(IntegrationIdempotencyRecord record) {
            return new Claim(
                    record.getId(),
                    record.getFencingToken(),
                    false,
                    true,
                    false,
                    record.getResponseStatus(),
                    record.getResponseBody());
        }

        static Claim processing(IntegrationIdempotencyRecord record) {
            return new Claim(
                    record.getId(),
                    record.getFencingToken(),
                    false,
                    false,
                    true,
                    0,
                    null);
        }
    }
}
