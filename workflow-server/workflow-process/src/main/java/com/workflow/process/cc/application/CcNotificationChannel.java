package com.workflow.process.cc.application;

import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;

import java.util.Map;

/**
 * 知会通知渠道接口。
 *
 * <p>不同渠道（如站内信、邮件、IM 等）实现该接口，由通用 Outbox 按渠道分发调用。</p>
 */
public interface CcNotificationChannel {

    /**
     * 获取该渠道支持的渠道标识（如 IN_APP、EMAIL 等）。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 发送一条知会通知。
     *
     * @param record  知会记录
     * @param message 发送时使用的稳定消息快照
     */
    void send(
            ProcessCcRecord record,
            Map<String, Object> message);
}
