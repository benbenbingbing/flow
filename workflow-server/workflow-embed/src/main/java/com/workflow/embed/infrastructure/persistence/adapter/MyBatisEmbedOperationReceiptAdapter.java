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

    /**
     * 初始化MyBatis嵌入式操作回执适配器，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedOperationReceiptAdapter(
            EmbedOperationReceiptMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 插入业务事务；后续读取或执行将使用更新后的状态。
     *
     * @param receipt 回执，作为 {@code mapper.insert} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 按ID查询嵌入式操作回执；结果供后续展示或处理。
     *
     * @param receiptId 回执ID，后续用于查询ID时定位或关联目标
     * @return 匹配的ID；未找到时为空
     */
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

    /**
     * 处理行，并将结果传给后续步骤。
     *
     * @param value 待处理行的原始输入，结果供调用方继续使用
     * @return 处理后的行结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理{@code domain}，并将结果传给后续步骤。
     *
     * @param value 待处理{@code domain}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code domain}结果，供调用方继续处理
     */
    private static EmbedOperationReceipt domain(EmbedOperationReceiptRow value) {
        return new EmbedOperationReceipt(
                value.id(), value.idempotencyRecordId(), value.applicationId(),
                value.operation(), value.actorScopeDigest(), value.viewKey(),
                value.targetType(), value.targetId(), value.outcomeCode(),
                value.recordVersion(), value.resultSummaryJson());
    }
}
