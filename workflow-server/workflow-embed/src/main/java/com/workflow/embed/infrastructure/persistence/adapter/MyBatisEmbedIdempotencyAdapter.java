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

    /**
     * 初始化MyBatis嵌入式幂等适配器，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param inserts {@code inserts}依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedIdempotencyAdapter(
            EmbedIdempotencyMapper mapper,
            ObjectMapper objectMapper,
            JdbcIdempotentInsert inserts) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.inserts = inserts;
    }

    /**
     * 唯一约束插入和条件 reacquire 在独立事务中完成，竞态输家核验已有记录。
     *
     * @param applicationId 应用ID，后续用于认领MyBatis嵌入式幂等时定位或关联目标
     * @param operation 操作标识，决定后续MyBatis嵌入式幂等采用的处理分支
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param requestHash 请求哈希，供本方法认领MyBatis嵌入式幂等时使用
     * @param now 当前时间，作为 {@code local} 的输入影响后续处理
     * @return 认领后的MyBatis嵌入式幂等结果，供调用方继续处理
     */
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

    /**
     * 初始占用仍依赖共享表的唯一约束；只忽略唯一冲突，不吞掉非法数据或连接错误。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param applicationId 应用ID，后续用于插入处理时定位或关联目标
     * @param operation 操作标识，决定后续处理采用的处理分支
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param requestHash 请求哈希，作为 {@code values.put} 的输入影响后续处理
     * @param now 当前时间，作为 {@code values.put} 的输入影响后续处理
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @return 处理条件成立时为 true，否则为 false
     */
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

    /**
     * complete 必须命中当前 fencing token，否则抛错以回滚同一业务事务。
     *
     * @param claim 认领，作为 {@code mapper.complete} 的输入影响后续处理
     * @param resourceType 资源类型标识，决定后续完成业务事务采用的处理分支
     * @param resourceId 资源ID，后续用于处理完成业务事务时定位或关联目标
     * @param responseStatus 响应状态标识，决定后续完成业务事务采用的处理分支
     * @param responseBody 响应请求体，供本方法处理完成业务事务时使用
     * @param now 当前时间，供本方法处理完成业务事务时使用
     */
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

    /**
     * 处理失败可重试，并将结果传给后续步骤。
     *
     * @param claim 认领，供本方法处理失败可重试时使用
     * @param now 当前时间，供本方法处理失败可重试时使用
     */
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

    /**
     * 校验并获取行；不满足约束时阻止后续处理。
     *
     * @param row 行，供本方法校验并获取行时使用
     * @return 校验并获取后的行结果，供调用方继续处理
     */
    private static EmbedIdempotencyRow requireRow(EmbedIdempotencyRow row) {
        if (row == null || row.fencingToken() < 1) {
            throw unavailable(null);
        }
        return row;
    }

    /**
     * 处理{@code acquired}，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code claim} 的输入影响后续处理
     * @return 处理后的{@code acquired}结果，供调用方继续处理
     */
    private static EmbedIdempotencyClaim acquired(EmbedIdempotencyRow row) {
        return claim(row, EmbedIdempotencyClaim.Disposition.ACQUIRED);
    }

    /**
     * 处理重放，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code claim} 的输入影响后续处理
     * @return 处理后的重放结果，供调用方继续处理
     */
    private static EmbedIdempotencyClaim replay(EmbedIdempotencyRow row) {
        if (row.responseStatus() == null || row.responseBody() == null
                || row.resourceType() == null || row.resourceId() == null) {
            throw unavailable(null);
        }
        return claim(row, EmbedIdempotencyClaim.Disposition.REPLAY);
    }

    /**
     * 处理处理，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code claim} 的输入影响后续处理
     * @return 处理后的处理结果，供调用方继续处理
     */
    private static EmbedIdempotencyClaim processing(EmbedIdempotencyRow row) {
        return claim(row, EmbedIdempotencyClaim.Disposition.PROCESSING);
    }

    /**
     * 认领MyBatis嵌入式幂等；后续读取或执行将使用更新后的状态。
     *
     * @param row 行，作为 {@code EmbedIdempotencyClaim} 的输入影响后续处理
     * @param disposition {@code disposition}，供本方法认领MyBatis嵌入式幂等时使用
     * @return 认领后的MyBatis嵌入式幂等结果，供调用方继续处理
     */
    private static EmbedIdempotencyClaim claim(
            EmbedIdempotencyRow row,
            EmbedIdempotencyClaim.Disposition disposition) {
        return new EmbedIdempotencyClaim(
                row.id(), row.fencingToken(), disposition,
                row.responseStatus(), row.responseBody(),
                row.resourceType(), row.resourceId());
    }

    /**
     * 构造{@code reused}异常，供调用方区分失败原因。
     *
     * @return 处理后的{@code reused}结果，供调用方继续处理
     */
    private static EmbedException reused() {
        return new EmbedException(
                409, EmbedErrorCode.EMBED_IDEMPOTENCY_KEY_REUSED,
                "Idempotency key was reused with different input");
    }

    /**
     * 处理本地，并将结果传给后续步骤。
     *
     * @param value 待处理本地的原始输入，结果供调用方继续使用
     * @return 处理后的本地结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static LocalDateTime local(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("Embed 幂等时间不能为空");
        }
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 构造服务不可用异常，供调用方区分失败原因。
     *
     * @param cause 原因，作为 {@code EmbedException} 的输入影响后续处理
     * @return 处理后的不可用结果，供调用方继续处理
     */
    private static EmbedException unavailable(Throwable cause) {
        return new EmbedException(
                503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is temporarily unavailable", null, cause);
    }
}
