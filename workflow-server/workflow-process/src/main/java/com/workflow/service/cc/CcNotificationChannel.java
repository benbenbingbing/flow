package com.workflow.service.cc;

import com.workflow.entity.ProcessCcOutbox;
import com.workflow.entity.ProcessCcRecord;

/**
 * 知会通知渠道接口。
 *
 * <p>不同渠道（如站内信、邮件、IM 等）实现该接口，由知会发送箱按渠道分发调用。</p>
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
     * @param record 知会记录
     * @param outbox 发送箱记录，承载发送状态与载荷
     */
    void send(ProcessCcRecord record, ProcessCcOutbox outbox);
}
