package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedOperationReceipt;
import java.util.Optional;

/** Embed 写操作最小回执的持久化端口。 */
public interface EmbedOperationReceiptPort {

    /** 必须加入当前业务事务；唯一键或写入失败应让实体写一起回滚。 */
    void insertInBusinessTransaction(EmbedOperationReceipt receipt);

    Optional<EmbedOperationReceipt> findById(String receiptId);
}
