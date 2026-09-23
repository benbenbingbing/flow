package com.workflow.embed.application.record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.runtime.port.EmbedRecordCreatePort.CreateCommand;
import com.workflow.contracts.embed.runtime.port.EmbedRecordCreatePort.CreatedRecord;
import com.workflow.contracts.embed.runtime.port.EmbedRecordCreatePort;
import com.workflow.embed.application.audit.EmbedRuntimeAudit;
import com.workflow.embed.application.record.EmbedNativeRecordCreateAuthorizationService.Authorization;
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

    /**
     * 初始化嵌入式记录创建事务服务，保存构造参数供后续方法使用。
     *
     * @param recordCreatePort 记录创建端口依赖，保存到当前对象供后续业务方法调用
     * @param receiptPort 回执端口依赖，保存到当前对象供后续业务方法调用
     * @param idempotencyPort 幂等端口依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param runtimeAudit 运行时审计依赖，保存到当前对象供后续业务方法调用
     */
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
     *
     * @param claim 认领，作为 {@code EmbedOperationReceipt} 的输入影响后续处理
     * @param authorization 授权，作为 {@code runtimeAudit.recordCreatedRequired} 的输入影响后续处理
     * @param actorScopeDigest 操作人作用域摘要，供本方法创建嵌入式记录创建事务时使用
     * @param now 当前时间，作为 {@code idempotencyPort.completeInBusinessTransaction} 的输入影响后续处理
     * @param traceId 追踪ID，后续用于创建嵌入式记录创建事务时定位或关联目标
     * @return 创建后的嵌入式记录创建事务结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult create(
            EmbedIdempotencyClaim claim,
            Authorization authorization,
            String actorScopeDigest,
            Instant now,
            String traceId) {
        long startedNanos = System.nanoTime();
        if (claim == null || !claim.acquired() || authorization == null) {
            throw new IllegalArgumentException("Embed 创建事务参数无效");
        }
        CreatedRecord created = recordCreatePort.create(
                new CreateCommand(
                        authorization.target(), authorization.effectiveData(),
                        claim.id(), authorization.startProcess()));
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

    /**
     * 处理{@code elapsed}{@code millis}，并将结果传给后续步骤。
     *
     * @param startedNanos 已启动{@code nanos}，供本方法处理{@code elapsed}{@code millis}时使用
     * @return 处理后的{@code elapsed}{@code millis}结果，供调用方继续处理
     */
    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos));
    }

    /**
     * 生成JSON文本，供后续匹配或展示。
     *
     * @param value 待处理JSON的原始输入，结果供调用方继续使用
     * @return 处理后的JSON文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 封装业务的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param receipt 回执，保存在对象中供后续校验、查询或展示
     * @param record 记录，保存在对象中供后续校验、查询或展示
     */
    public record BusinessResult(
            EmbedOperationReceipt receipt,
            CreatedRecord record) {
    }
}
