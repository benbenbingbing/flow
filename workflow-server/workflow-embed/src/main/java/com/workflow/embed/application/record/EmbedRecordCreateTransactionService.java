package com.workflow.embed.application.record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRecordCreatePort;
import com.workflow.embed.application.audit.EmbedRuntimeAudit;
import com.workflow.embed.application.form.EmbedRuntimeFormFacade.CreateAuthorization;
import com.workflow.embed.application.port.EmbedIdempotencyPort;
import com.workflow.embed.application.port.EmbedOperationReceiptPort;
import com.workflow.embed.domain.EmbedIdempotencyClaim;
import com.workflow.embed.domain.EmbedOperationReceipt;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 把实体写、Operation Receipt 和 fenced complete 固定在同一个数据库事务中。 */
@Service
public class EmbedRecordCreateTransactionService {

    public static final String OPERATION = "EMBED_RECORD_CREATE";
    public static final String OUTCOME = "RECORD_CREATED";
    public static final String RESOURCE_TYPE = "EMBED_OPERATION_RECEIPT";
    private static final String REPLAY_SCHEMA = "embed-idempotency-replay-v1";
    private static final int MAX_SUMMARY_BYTES = 8 * 1024;

    private final EmbedRecordCreatePort recordCreatePort;
    private final EmbedOperationReceiptPort receiptPort;
    private final EmbedIdempotencyPort idempotencyPort;
    private final ObjectMapper objectMapper;
    private final EmbedRuntimeAudit runtimeAudit;

    public EmbedRecordCreateTransactionService(
            EmbedRecordCreatePort recordCreatePort,
            EmbedOperationReceiptPort receiptPort,
            EmbedIdempotencyPort idempotencyPort,
            ObjectMapper objectMapper,
            EmbedRuntimeAudit runtimeAudit) {
        this.recordCreatePort = recordCreatePort;
        this.receiptPort = receiptPort;
        this.idempotencyPort = idempotencyPort;
        this.objectMapper = objectMapper;
        this.runtimeAudit = runtimeAudit;
    }

    /**
     * 任一步骤失败都会回滚实体数据、实体自身回执、Embed 回执和幂等成功状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult create(
            EmbedIdempotencyClaim claim,
            CreateAuthorization authorization,
            String actorScopeDigest,
            Instant now,
            String traceId) {
        long startedNanos = System.nanoTime();
        if (claim == null || !claim.acquired() || authorization == null) {
            throw new IllegalArgumentException("Embed 创建事务参数无效");
        }
        EmbedRecordCreatePort.CreatedRecord created = recordCreatePort.create(
                new EmbedRecordCreatePort.CreateCommand(
                        authorization.target(), authorization.effectiveData(),
                        claim.id()));
        String receiptId = "eor_"
                + UUID.randomUUID().toString().replace("-", "");
        String summary = json(Map.of(
                "recordId", created.recordId(),
                "outcomeCode", OUTCOME));
        EmbedOperationReceipt receipt = new EmbedOperationReceipt(
                receiptId, claim.id(),
                authorization.session().applicationId(), OPERATION,
                actorScopeDigest, authorization.viewKey(),
                "RECORD", created.recordId(), OUTCOME,
                created.recordVersion(), summary);
        receiptPort.insertInBusinessTransaction(receipt);

        String replayEnvelope = json(Map.of(
                "schema", REPLAY_SCHEMA,
                "receiptId", receiptId,
                "outcomeCode", OUTCOME));
        idempotencyPort.completeInBusinessTransaction(
                claim, RESOURCE_TYPE, receiptId, 201,
                replayEnvelope, now);
        // required 审计必须在 fenced complete 之后、事务返回之前写入同一
        // Outbox；任何审计异常都会触发 rollbackFor=Exception，避免出现
        // 业务成功但无创建审计。
        runtimeAudit.recordCreatedRequired(
                authorization.session(),
                created.recordId(),
                traceId,
                elapsedMillis(startedNanos));
        return new BusinessResult(receipt, created);
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos));
    }

    private String json(Object value) {
        try {
            String result = objectMapper.writeValueAsString(value);
            if (result.getBytes(StandardCharsets.UTF_8).length
                    > MAX_SUMMARY_BYTES) {
                throw new IllegalArgumentException(
                        "Embed 最小回执超过 8 KiB");
            }
            return result;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法序列化 Embed 最小回执", error);
        }
    }

    public record BusinessResult(
            EmbedOperationReceipt receipt,
            EmbedRecordCreatePort.CreatedRecord record) {
    }
}
