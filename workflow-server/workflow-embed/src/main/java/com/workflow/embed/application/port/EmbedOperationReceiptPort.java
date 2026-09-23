package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedOperationReceipt;
import java.util.Optional;

/** Embed 写操作最小回执的持久化端口。 */
public interface EmbedOperationReceiptPort {

    /**
     * 必须加入当前业务事务；唯一键或写入失败应让实体写一起回滚。
     *
     * @param receipt 回执，供本方法插入业务事务时使用
     */
    void insertInBusinessTransaction(EmbedOperationReceipt receipt);

    /**
     * 按ID查询嵌入式操作回执；结果供后续展示或处理。
     *
     * @param receiptId 回执ID，后续用于查询ID时定位或关联目标
     * @return 匹配的ID；未找到时为空
     */
    Optional<EmbedOperationReceipt> findById(String receiptId);
}
