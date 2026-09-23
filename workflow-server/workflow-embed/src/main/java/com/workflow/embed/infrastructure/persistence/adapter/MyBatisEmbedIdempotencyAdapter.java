package com.workflow.embed.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.port.EmbedIdempotencyPort;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedIdempotencyClaim;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedIdempotencyMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedIdempotencyRow;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.LinkedHashMap;
import com.workflow.core.database.JdbcIdempotentInsert;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 复用 integration_idempotency_record 的 Embed fenced 状态机适配器。 */
@Repository
public class MyBatisEmbedIdempotencyAdapter implements EmbedIdempotencyPort {

    static final int RETENTION_DAYS = 7;
    static final int PROCESSING_TIMEOUT_SECONDS = 120;
    static final int MAX_REPLAY_ENVELOPE_BYTES = 8 * 1024;

    private final EmbedIdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final JdbcIdempotentInsert inserts;

    public MyBatisEmbedIdempotencyAdapter(
            EmbedIdempotencyMapper mapper,
            ObjectMapper objectMapper,
            JdbcIdempotentInsert inserts) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.inserts = inserts;
    }

    /** 唯一约束插入和条件 reacquire 在独立事务中完成，竞态输家核验已有记录。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmbedIdempotencyClaim claim(
            String applicationId,
            String operation,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        LocalDateTime current = local(now);
        try {
            boolean inserted = insertProcessing(
                    "eii_" + UUID.randomUUID().toString().replace("-", ""),
                    applicationId, operation, idempotencyKey, requestHash,
                    current, current.plusDays(RETENTION_DAYS));
            EmbedIdempotencyRow record = requireRow(
                    mapper.find(applicationId, operation, idempotencyKey));
            if (!Objects.equals(requestHash, record.requestHash())) {
                throw reused();
            }
            if (inserted) {
                return acquired(record);
            }
            if ("SUCCEEDED".equals(record.status())) {
                return replay(record);
            }
            boolean failed = "FAILED_RETRYABLE".equals(record.status());
            boolean stale = "PROCESSING".equals(record.status())
                    && record.processingStartedAt() != null
                    && record.processingStartedAt().isBefore(
                            current.minusSeconds(PROCESSING_TIMEOUT_SECONDS));
            if (failed || stale) {
                int reacquired = mapper.reacquire(
                        record.id(), record.fencingToken(), current,
                        current.minusSeconds(PROCESSING_TIMEOUT_SECONDS),
                        current.plusDays(RETENTION_DAYS));
                EmbedIdempotencyRow winner = requireRow(
                        mapper.find(applicationId, operation, idempotencyKey));
                if (!Objects.equals(requestHash, winner.requestHash())) {
                    throw reused();
                }
                if (reacquired == 1) {
                    return acquired(winner);
                }
                if ("SUCCEEDED".equals(winner.status())) {
                    return replay(winner);
                }
                return processing(winner);
            }
            if (!"PROCESSING".equals(record.status())) {
                throw unavailable(null);
            }
            return processing(record);
        } catch (EmbedException error) {
            throw error;
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /** 初始占用仍依赖共享表的唯一约束；只忽略唯一冲突，不吞掉非法数据或连接错误。 */
    private boolean insertProcessing(String id, String applicationId, String operation,
            String idempotencyKey, String requestHash, LocalDateTime now, LocalDateTime expiresAt) {
        var values = new LinkedHashMap<String, Object>();
        values.put("id", id);
        values.put("application_id", applicationId);
        values.put("operation", operation);
        values.put("idempotency_key", idempotencyKey);
        values.put("request_hash", requestHash);
        values.put("status", "PROCESSING");
        values.put("fencing_token", 1L);
        values.put("processing_started_at", now);
        values.put("expires_at", expiresAt);
        values.put("create_time", now);
        values.put("update_time", now);
        return inserts.insertIfAbsent("integration_idempotency_record", values);
    }

    /** complete 必须命中当前 fencing token，否则抛错以回滚同一业务事务。 */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeInBusinessTransaction(
            EmbedIdempotencyClaim claim,
            String resourceType,
            String resourceId,
            int responseStatus,
            String responseBody,
            Instant now) {
        if (claim == null || !claim.acquired()
                || responseStatus < 200 || responseStatus > 299
                || responseBody == null
                || responseBody.getBytes(StandardCharsets.UTF_8).length
                > MAX_REPLAY_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Embed 幂等完成参数无效");
        }
        try {
            if (!objectMapper.readTree(responseBody).isObject()) {
                throw new IllegalArgumentException("Embed 幂等重放摘要必须是 JSON 对象");
            }
            int updated = mapper.complete(
                    claim.id(), claim.fencingToken(), resourceType,
                    resourceId, responseStatus, responseBody, local(now));
            if (updated != 1) {
                throw new IllegalStateException("Embed 幂等 fencing token 已失效");
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw error;
        } catch (DataAccessException error) {
            throw unavailable(error);
        } catch (Exception error) {
            throw new IllegalArgumentException("Embed 幂等重放摘要不是合法 JSON", error);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRetryable(EmbedIdempotencyClaim claim, Instant now) {
        if (claim == null || !claim.acquired()) {
            return;
        }
        try {
            // 更新 0 行表示新 Worker 已接管；绝不能覆盖新 fencing token 的状态。
            mapper.failRetryable(
                    claim.id(), claim.fencingToken(), local(now));
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    private static EmbedIdempotencyRow requireRow(EmbedIdempotencyRow row) {
        if (row == null || row.fencingToken() < 1) {
            throw unavailable(null);
        }
        return row;
    }

    private static EmbedIdempotencyClaim acquired(EmbedIdempotencyRow row) {
        return claim(row, EmbedIdempotencyClaim.Disposition.ACQUIRED);
    }

    private static EmbedIdempotencyClaim replay(EmbedIdempotencyRow row) {
        if (row.responseStatus() == null || row.responseBody() == null
                || row.resourceType() == null || row.resourceId() == null) {
            throw unavailable(null);
        }
        return claim(row, EmbedIdempotencyClaim.Disposition.REPLAY);
    }

    private static EmbedIdempotencyClaim processing(EmbedIdempotencyRow row) {
        return claim(row, EmbedIdempotencyClaim.Disposition.PROCESSING);
    }

    private static EmbedIdempotencyClaim claim(
            EmbedIdempotencyRow row,
            EmbedIdempotencyClaim.Disposition disposition) {
        return new EmbedIdempotencyClaim(
                row.id(), row.fencingToken(), disposition,
                row.responseStatus(), row.responseBody(),
                row.resourceType(), row.resourceId());
    }

    private static EmbedException reused() {
        return new EmbedException(
                409, EmbedErrorCode.EMBED_IDEMPOTENCY_KEY_REUSED,
                "Idempotency key was reused with different input");
    }

    private static LocalDateTime local(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("Embed 幂等时间不能为空");
        }
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static EmbedException unavailable(Throwable cause) {
        return new EmbedException(
                503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is temporarily unavailable", null, cause);
    }
}
