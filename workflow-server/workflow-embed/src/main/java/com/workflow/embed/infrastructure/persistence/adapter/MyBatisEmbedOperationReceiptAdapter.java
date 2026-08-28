package com.workflow.embed.infrastructure.persistence.adapter;

import com.workflow.embed.application.port.EmbedOperationReceiptPort;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedOperationReceipt;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedOperationReceiptMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedOperationReceiptRow;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** embed_operation_receipt 的 MyBatis 适配器。 */
@Repository
public class MyBatisEmbedOperationReceiptAdapter
        implements EmbedOperationReceiptPort {

    private final EmbedOperationReceiptMapper mapper;

    public MyBatisEmbedOperationReceiptAdapter(
            EmbedOperationReceiptMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insertInBusinessTransaction(EmbedOperationReceipt receipt) {
        try {
            int inserted = mapper.insert(row(receipt));
            if (inserted != 1) {
                throw new IllegalStateException("Embed Operation Receipt 写入失败");
            }
        } catch (DataAccessException error) {
            // 唯一键冲突也必须向上抛出，让旧 Worker 的整个业务事务回滚。
            throw error;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmbedOperationReceipt> findById(String receiptId) {
        try {
            return Optional.ofNullable(mapper.findById(receiptId))
                    .map(MyBatisEmbedOperationReceiptAdapter::domain);
        } catch (DataAccessException error) {
            throw new EmbedException(
                    503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                    "Embed runtime is temporarily unavailable", null, error);
        }
    }

    private static EmbedOperationReceiptRow row(EmbedOperationReceipt value) {
        if (value == null) {
            throw new IllegalArgumentException("Embed Operation Receipt 不能为空");
        }
        return new EmbedOperationReceiptRow(
                value.id(), value.idempotencyRecordId(), value.applicationId(),
                value.operation(), value.actorScopeDigest(), value.viewKey(),
                value.targetType(), value.targetId(), value.outcomeCode(),
                value.recordVersion(), value.resultSummaryJson());
    }

    private static EmbedOperationReceipt domain(EmbedOperationReceiptRow value) {
        return new EmbedOperationReceipt(
                value.id(), value.idempotencyRecordId(), value.applicationId(),
                value.operation(), value.actorScopeDigest(), value.viewKey(),
                value.targetType(), value.targetId(), value.outcomeCode(),
                value.recordVersion(), value.resultSummaryJson());
    }
}
